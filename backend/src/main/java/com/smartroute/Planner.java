package com.smartroute;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.Cacheable;
import java.util.*;
import static com.smartroute.Models.*;
@Service
public class Planner {
 private final Network g;private final Router router;
 public Planner(Network g,Router router){this.g=g;this.router=router;}
 public Request with(Request r,String s,String t,java.time.LocalDateTime departure,String algorithm){return new Request(s,t,departure,r.scenario(),r.emergency()?"FASTEST":r.objective(),algorithm,r.category(),r.stopId(),r.stopMinutes(),r.fuelRange(),r.reserve(),r.emergency());}
 void validate(Request r){
  if(!g.byId.containsKey(r.source())||!g.byId.containsKey(r.destination()))throw new IllegalArgumentException("Choose a valid source and destination.");
  if(r.source().equals(r.destination()))throw new IllegalArgumentException("Source and destination must be different.");
  if(r.category()!=null&&!List.of("PETROL","RESTAURANT","HOTEL").contains(r.category()))throw new IllegalArgumentException("Unknown stop category.");
  if(r.fuelRange()!=null&&r.category()!=null&&!r.category().equals("PETROL"))throw new IllegalArgumentException("Fuel planning requires a petrol stop.");
  if(r.stopId()!=null&&(!g.byId.containsKey(r.stopId())||!g.byId.get(r.stopId()).category().equals(r.fuelRange()!=null?"PETROL":r.category())))throw new IllegalArgumentException("Select a stop in the chosen category.");
 }
 @Cacheable("routes") public Result plan(Request r){return calculate(r);}
 public Result calculate(Request r){
  validate(r);Route direct=router.route(r,r.source(),r.destination(),Set.of(),r.algorithm());
  if(direct==null)return new Result(List.of(),List.of(),"No route is available for this scenario.","DEMO",r.algorithm());
  if(r.category()!=null||r.fuelRange()!=null){
   String cat=r.fuelRange()!=null?"PETROL":r.category();List<Route> stops=new ArrayList<>();
   for(Place p:g.places){if(!p.category().equals(cat)||p.id().equals(r.source())||p.id().equals(r.destination()))continue;
    Route a=router.route(r,r.source(),p.id(),Set.of(),r.algorithm());if(a==null)continue;
    if(r.fuelRange()!=null&&a.distanceKm()>r.fuelRange()-r.reserve()+1e-9)continue;
    var next=r.departure().plusNanos((long)((a.travelMinutes()+r.stopMinutes())*60e9));
    Route b=router.route(with(r,p.id(),r.destination(),next,r.algorithm()),p.id(),r.destination(),Set.of(),r.algorithm());if(b==null)continue;
    List<String> nodes=new ArrayList<>(a.nodes());nodes.addAll(b.nodes().subList(1,b.nodes().size()));List<String>edges=new ArrayList<>(a.roadIds());edges.addAll(b.roadIds());Map<String,Double>congestion=new LinkedHashMap<>(a.congestion());congestion.putAll(b.congestion());
    Metrics m=new Metrics(a.metrics().executionMs()+b.metrics().executionMs(),a.metrics().visitedNodes()+b.metrics().visitedNodes(),a.metrics().settledNodes()+b.metrics().settledNodes(),a.metrics().preprocessingMs()+b.metrics().preprocessingMs());
    stops.add(new Route(p.id(),p.name(),nodes,edges,a.distanceKm()+b.distanceKm(),a.travelMinutes()+b.travelMinutes(),r.stopMinutes(),b.arrival(),m,p.id(),a.distanceKm()+b.distanceKm()-direct.distanceKm(),congestion));
   }
   stops.sort(Comparator.comparingDouble(x->r.objective().equals("SHORTEST")&&!r.emergency()?x.distanceKm():x.travelMinutes()+x.stopMinutes()));
   Route chosen=r.stopId()==null?(stops.isEmpty()?null:stops.getFirst()):stops.stream().filter(x->x.stopId().equals(r.stopId())).findFirst().orElse(null);
   if(chosen==null)return new Result(List.of(),stops,"No feasible stop is reachable"+(r.fuelRange()!=null?" within your fuel range after the safety reserve.":" for the selected journey."),"DEMO",r.algorithm());
   return new Result(List.of(chosen),stops.stream().filter(x->!x.stopId().equals(chosen.stopId())).toList(),r.fuelRange()!=null?"Reachability checks the first leg. Refuelling is assumed to cover the onward journey.":"Stop recommendation minimizes the complete journey.","DEMO",r.algorithm());
  }
  List<Route> routes=new ArrayList<>();routes.add(direct);Map<String,Route> candidates=new HashMap<>();
  for(String edge:direct.roadIds()){Route alt=router.route(r,r.source(),r.destination(),Set.of(edge),r.algorithm());if(alt!=null&&!alt.nodes().equals(direct.nodes()))candidates.put(String.join(",",alt.nodes()),alt);}
  candidates.values().stream().sorted(Comparator.comparingDouble(x->r.objective().equals("SHORTEST")&&!r.emergency()?x.distanceKm():x.travelMinutes())).limit(2).forEach(routes::add);
  return new Result(routes,List.of(),r.emergency()?"Academic emergency demonstration only. Not operational emergency navigation.":"Routes use synthetic roads and simulated traffic. Alternatives are edge-deviation candidates.","DEMO",r.algorithm());
 }
}
