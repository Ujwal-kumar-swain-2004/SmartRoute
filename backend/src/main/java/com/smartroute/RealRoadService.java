package com.smartroute;

import com.fasterxml.jackson.databind.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.nio.file.*;
import java.io.*;
import java.time.*;
import java.util.*;
import static com.smartroute.TomTomService.*;

/** Directed OSM topology. Speed defaults and peak profiles are estimates, never live traffic. */
@Service
public class RealRoadService {
 public record Request(@NotNull @Valid LiveRequest journey,@NotBlank @Pattern(regexp="DIJKSTRA|TIME_DEPENDENT|ALT") String algorithm,@NotBlank @Pattern(regexp="NORMAL|MORNING|EVENING") String scenario,boolean preferBusierRoads) {public Request(LiveRequest journey,String algorithm,String scenario){this(journey,algorithm,scenario,false);}}
 public record Metrics(double executionMs,double preprocessingMs,int visitedNodes,int settledNodes) {}
 public record Result(String provider,String fetchedAt,List<ProviderRoute> routes,String notice,String algorithm,Metrics metrics,List<Double> snapDistancesMeters,int graphNodes,int graphEdges) {}
 record Node(long id,double lat,double lng) {}
 record Edge(long from,long to,double km,double minutes,String name,boolean toll,boolean motorway,boolean major) {}
 record Entry(long id,double cost,double priority) {}
 record PathResult(List<Edge> edges,Metrics metrics) {}
 private final TomTomService locations;
 private final Map<Long,Node> nodes=new LinkedHashMap<>();
 private final Map<Long,List<Edge>> out=new HashMap<>(),in=new HashMap<>();
 private String importedAt="",problem="OSM extract is missing. Run scripts/download-osm.ps1, rebuild, and restart.";
 private int edgeCount;
 private final Map<String,List<Edge>> spatial=new HashMap<>();
 public record RoadCoverage(double majorRoadKm,double matchedKm,double totalKm,double coverage,double mainRoadShare) {}
 private String cell(double lat,double lng){return ((int)Math.floor(lat/.002))+":"+((int)Math.floor(lng/.002));}
 public RoadCoverage coverage(ProviderRoute route){double major=0,matched=0,total=0;List<Point> points=route.points();for(int i=1;i<points.size();i++){Point a=points.get(i-1),b=points.get(i);double km=distance(a.lat(),a.lng(),b.lat(),b.lng());if(km<=0)continue;total+=km;double lat=(a.lat()+b.lat())/2,lng=(a.lng()+b.lng())/2;Edge best=null;double min=.06;int x=(int)Math.floor(lat/.002),y=(int)Math.floor(lng/.002);for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)for(Edge edge:spatial.getOrDefault((x+dx)+":"+(y+dy),List.of())){Node u=nodes.get(edge.from),v=nodes.get(edge.to);double scale=Math.cos(Math.toRadians(lat)),ax=(u.lng-lng)*scale,ay=u.lat-lat,bx=(v.lng-lng)*scale,by=v.lat-lat,ex=bx-ax,ey=by-ay,den=ex*ex+ey*ey,t=den==0?0:Math.max(0,Math.min(1,-(ax*ex+ay*ey)/den));double d=Math.hypot(ax+t*ex,ay+t*ey)*111.2;if(d<min){min=d;best=edge;}}if(best!=null){matched+=km;if(best.major)major+=km;}}return new RoadCoverage(major,matched,total,total==0?0:matched/total,matched==0?0:major/matched);}

 @org.springframework.beans.factory.annotation.Autowired public RealRoadService(TomTomService locations,@Value("${smartroute.osm-file:data/bhubaneswar-osm.json}") String filename){this.locations=locations;try{InputStream input=Files.exists(java.nio.file.Path.of(filename))?Files.newInputStream(java.nio.file.Path.of(filename)):getClass().getResourceAsStream("/osm/bhubaneswar.json");if(input!=null)try(input){load(new ObjectMapper().readTree(input));}}catch(Exception e){problem="OSM extract could not be read. Download it again and restart the backend.";nodes.clear();out.clear();in.clear();}}
 RealRoadService(TomTomService locations,JsonNode data){this.locations=locations;load(data);}
 void load(JsonNode data){
  if(!data.has("elements")||data.has("remark"))throw new IllegalArgumentException("Incomplete OSM response");
  Map<Long,Node> all=new HashMap<>();for(JsonNode e:data.path("elements"))if(e.path("type").asText().equals("node"))all.put(e.path("id").asLong(),new Node(e.path("id").asLong(),e.path("lat").asDouble(),e.path("lon").asDouble()));
  for(JsonNode way:data.path("elements"))if(way.path("type").asText().equals("way")){
   JsonNode tags=way.path("tags");String highway=tags.path("highway").asText();if(!Set.of("motorway","trunk","primary","secondary","tertiary","unclassified","residential","living_street","service","motorway_link","trunk_link","primary_link","secondary_link","tertiary_link").contains(highway))continue;
   String access=tags.path("motorcar").asText(tags.path("motor_vehicle").asText(tags.path("vehicle").asText(tags.path("access").asText("yes"))));if(Set.of("no","private","agricultural","forestry","delivery","destination","customers").contains(access))continue;
   double speed=speed(tags.path("maxspeed").asText(),highway);String oneway=tags.path("oneway").asText(tags.path("junction").asText().equals("roundabout")||highway.equals("motorway")?"yes":"no");String name=tags.path("name").asText(tags.path("ref").asText(highway.replace('_',' ')));
   boolean toll=tags.path("toll").asText().equals("yes"),motorway=highway.equals("motorway")||highway.equals("motorway_link")||highway.equals("trunk")||highway.equals("trunk_link");JsonNode ids=way.path("nodes");
   for(int i=1;i<ids.size();i++){Node a=all.get(ids.get(i-1).asLong()),b=all.get(ids.get(i).asLong());if(a==null||b==null)continue;double km=distance(a.lat,a.lng,b.lat,b.lng);if(km<=0)continue;nodes.put(a.id,a);nodes.put(b.id,b);if(!oneway.equals("-1"))add(new Edge(a.id,b.id,km,km/speed*60,name,toll,motorway,Set.of("motorway","trunk","primary","secondary","tertiary","motorway_link","trunk_link","primary_link","secondary_link","tertiary_link").contains(highway)));if(!Set.of("yes","1","true").contains(oneway))add(new Edge(b.id,a.id,km,km/speed*60,name,toll,motorway,Set.of("motorway","trunk","primary","secondary","tertiary","motorway_link","trunk_link","primary_link","secondary_link","tertiary_link").contains(highway)));}
  }
  for(long id:nodes.keySet()){out.computeIfAbsent(id,k->new ArrayList<>());in.computeIfAbsent(id,k->new ArrayList<>());}for(List<Edge> edges:out.values())for(Edge e:edges){Node a=nodes.get(e.from),b=nodes.get(e.to);int steps=Math.max(1,(int)Math.ceil(e.km/.15));for(int i=0;i<=steps;i++){double t=i/(double)steps;String cell=cell(a.lat+(b.lat-a.lat)*t,a.lng+(b.lng-a.lng)*t);List<Edge> bucket=spatial.computeIfAbsent(cell,k->new ArrayList<>());if(!bucket.contains(e))bucket.add(e);}}importedAt=data.path("osm3s").path("timestamp_osm_base").asText("unknown");problem=nodes.isEmpty()?"OSM extract contains no supported driving roads.":"";
 }
 void add(Edge e){out.computeIfAbsent(e.from,k->new ArrayList<>()).add(e);in.computeIfAbsent(e.to,k->new ArrayList<>()).add(e);edgeCount++;}
 static double speed(String value,String highway){try{var match=java.util.regex.Pattern.compile("([0-9]+(?:\\.[0-9]+)?)").matcher(value);if(match.find())return Math.max(5,Math.min(130,Double.parseDouble(match.group(1))*(value.contains("mph")?1.609344:1)));}catch(Exception ignored){}return switch(highway){case "motorway","motorway_link"->70;case "trunk","trunk_link"->60;case "primary","primary_link"->50;case "secondary","secondary_link"->40;case "tertiary","tertiary_link"->35;case "living_street","service"->15;default->25;};}
 public Map<String,Object> status(){return Map.of("ready",!nodes.isEmpty(),"nodes",nodes.size(),"directedEdges",edgeCount,"osmTimestamp",importedAt,"message",problem,"bounds",List.of(20.22,85.75,20.38,85.90),"algorithms",List.of("DIJKSTRA","TIME_DEPENDENT","ALT"));}
 static double distance(double a,double b,double c,double d){double x=Math.toRadians(c-a),y=Math.toRadians(d-b),v=Math.pow(Math.sin(x/2),2)+Math.cos(Math.toRadians(a))*Math.cos(Math.toRadians(c))*Math.pow(Math.sin(y/2),2);return 6371*2*Math.atan2(Math.sqrt(v),Math.sqrt(Math.max(0,1-v)));}
 private long snap(Location p,boolean source,LiveRequest request){if(p.lat()<20.22||p.lat()>20.38||p.lng()<85.75||p.lng()>85.90)throw new IllegalArgumentException("Java OSM routing is limited to the imported Bhubaneswar area (20.22–20.38 N, 85.75–85.90 E). Use TomTom for other locations.");long best=-1;double closest=Double.POSITIVE_INFINITY;for(Node n:nodes.values()){if((source?out:in).get(n.id).stream().noneMatch(e->allowed(e,request)))continue;double km=distance(p.lat(),p.lng(),n.lat,n.lng);if(km<closest){closest=km;best=n.id;}}if(best<0||closest>0.75)throw new IllegalArgumentException("No supported driving road is within 750 metres of "+p.label()+". Choose a nearby road on the map.");return best;}
 boolean allowed(Edge e,LiveRequest r){return !(r.avoidTolls()&&e.toll||r.avoidHighways()&&e.motorway);}
 double factor(Edge e,String scenario,LocalDateTime departure){if(scenario.equals("NORMAL"))return 1;double hour=departure.getHour()+departure.getMinute()/60.0+departure.getSecond()/3600.0;double peak=scenario.equals("MORNING")?9:18;return 1+Math.exp(-Math.pow((hour-peak)/2,2))*(e.motorway?1.5:.7);}
 double duration(Edge e,Request r,LocalDateTime departure,boolean dynamic){if(!dynamic)return e.minutes*factor(e,r.scenario,departure);double remaining=e.minutes,elapsed=0;while(remaining>1e-9){double f=factor(e,r.scenario,departure.plusNanos((long)(elapsed*60e9)));double step=Math.min(.25,remaining*f);remaining-=step/f;elapsed+=step;}return elapsed;}
 double preference(Edge e,Request r){return r.preferBusierRoads&&!e.major?2.5:1;}
 double weight(Edge e,Request r,LocalDateTime departure){return (r.journey.objective().equals("SHORTEST")?e.km:duration(e,r,departure,false))*preference(e,r);}
 Map<Long,Double> distances(long source,Request r,LocalDateTime departure,boolean reverse){Map<Long,Double>d=new HashMap<>();PriorityQueue<Entry>q=new PriorityQueue<>(Comparator.comparingDouble(Entry::cost));d.put(source,0.0);q.add(new Entry(source,0,0));while(!q.isEmpty()){Entry u=q.poll();if(u.cost>d.get(u.id))continue;for(Edge e:(reverse?in:out).get(u.id)){if(!allowed(e,r.journey))continue;long v=reverse?e.from:e.to;double cost=u.cost+weight(e,r,departure);if(cost<d.getOrDefault(v,Double.POSITIVE_INFINITY)){d.put(v,cost);q.add(new Entry(v,cost,cost));}}}return d;}
 PathResult search(long source,long target,Request r,LocalDateTime departure){
  long start=System.nanoTime();List<Map<Long,Double>>from=new ArrayList<>(),to=new ArrayList<>();
  if(r.algorithm.equals("ALT")){List<Long>landmarks=new ArrayList<>(nodes.keySet());for(int index:List.of(0,landmarks.size()/2,landmarks.size()-1)){long landmark=landmarks.get(index);from.add(distances(landmark,r,departure,false));to.add(distances(landmark,r,departure,true));}}
  double preprocessing=(System.nanoTime()-start)/1e6;Map<Long,Double>d=new HashMap<>();Map<Long,Edge>prev=new HashMap<>();Set<Long>settled=new HashSet<>();PriorityQueue<Entry>q=new PriorityQueue<>(Comparator.comparingDouble(Entry::priority));d.put(source,0.0);q.add(new Entry(source,0,0));
  while(!q.isEmpty()){Entry u=q.poll();if(!settled.add(u.id))continue;if(u.id==target)break;for(Edge e:out.get(u.id)){if(!allowed(e,r.journey))continue;double w=r.algorithm.equals("TIME_DEPENDENT")&&!r.preferBusierRoads&&!r.journey.objective().equals("SHORTEST")?duration(e,r,departure.plusNanos((long)(u.cost*60e9)),true):weight(e,r,departure);double cost=u.cost+w;if(cost<d.getOrDefault(e.to,Double.POSITIVE_INFINITY)){d.put(e.to,cost);prev.put(e.to,e);double h=0;for(int i=0;i<from.size();i++){var f=from.get(i);var t=to.get(i);if(f.containsKey(target)&&f.containsKey(e.to))h=Math.max(h,f.get(target)-f.get(e.to));if(t.containsKey(e.to)&&t.containsKey(target))h=Math.max(h,t.get(e.to)-t.get(target));}q.add(new Entry(e.to,cost,cost+h));}}}
  if(!settled.contains(target))throw new IllegalArgumentException("No directed path was found within the imported road network. Change the locations or avoidance options.");LinkedList<Edge>path=new LinkedList<>();long cur=target;while(cur!=source){Edge e=prev.get(cur);path.addFirst(e);cur=e.from;}
  return new PathResult(path,new Metrics((System.nanoTime()-start)/1e6,r.algorithm.equals("ALT")?preprocessing:0,d.size(),settled.size()));
 }
 public Result route(Request request){
  if(request.preferBusierRoads&&request.algorithm.equals("TIME_DEPENDENT"))throw new IllegalArgumentException("Main-road preference requires static Dijkstra or ALT.");
  if(nodes.isEmpty())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,problem);List<Location>journey=locations.journey(request.journey);List<Long>snaps=new ArrayList<>();List<Double>snapDistances=new ArrayList<>();
  for(int i=0;i<journey.size();i++){Location p=journey.get(i);long id=snap(p,i<journey.size()-1,request.journey);snaps.add(id);Node n=nodes.get(id);snapDistances.add(distance(p.lat(),p.lng(),n.lat,n.lng)*1000);}
  LocalDateTime departure=request.journey.departure()==null?LocalDateTime.now(ZoneId.of("Asia/Kolkata")):LocalDateTime.parse(request.journey.departure());if(request.journey.departure()==null&&request.scenario.equals("MORNING"))departure=departure.withHour(9).withMinute(0).withSecond(0).withNano(0);if(request.journey.departure()==null&&request.scenario.equals("EVENING"))departure=departure.withHour(18).withMinute(0).withSecond(0).withNano(0);
  List<Edge>edges=new ArrayList<>();double time=0,free=0,km=0,exec=0,prep=0;int visited=0,settled=0;
  for(int i=1;i<snaps.size();i++){var path=search(snaps.get(i-1),snaps.get(i),request,departure.plusNanos((long)(time*60e9)));exec+=path.metrics.executionMs;prep+=path.metrics.preprocessingMs;visited+=path.metrics.visitedNodes;settled+=path.metrics.settledNodes;LocalDateTime legDeparture=departure.plusNanos((long)(time*60e9));double legMinutes=0;for(Edge e:path.edges){double duration=duration(e,request,request.algorithm.equals("TIME_DEPENDENT")?legDeparture.plusNanos((long)(legMinutes*60e9)):legDeparture,request.algorithm.equals("TIME_DEPENDENT"));legMinutes+=duration;free+=e.minutes;km+=e.km;edges.add(e);}time+=legMinutes;}
  List<Point>points=new ArrayList<>();Node first=nodes.get(snaps.getFirst());points.add(new Point(first.lat,first.lng));for(Edge e:edges){Node n=nodes.get(e.to);points.add(new Point(n.lat,n.lng));}
  List<Instruction>instructions=new ArrayList<>();double offset=0,elapsed=0;String last="";for(Edge e:edges){if(!e.name.equals(last)){Node n=nodes.get(e.from);instructions.add(new Instruction(offset,elapsed,"Continue along "+e.name,e.name,"FOLLOW_ROAD",new Point(n.lat,n.lng)));last=e.name;}offset+=e.km*1000;elapsed+=e.minutes*60;}
  ProviderRoute route=new ProviderRoute(km,time*60,Math.max(0,(time-free)*60),departure.plusNanos((long)(time*60e9)).atZone(ZoneId.of("Asia/Kolkata")).toOffsetDateTime().toString(),points,List.of(),free*60,null,instructions,journey.size()-1);
  return new Result("Java + OpenStreetMap",Instant.now().toString(),List.of(route),"Java-computed route on an OSM extract. OSM road directions and basic motor-vehicle access are used; turn restrictions, conditional access, barriers and lane rules are not fully modelled. Speeds use maxspeed tags or defaults; peak traffic is simulated. Not live traffic or operational navigation. Points snap to nearby road nodes; connectors are not included in distance. Stops are visited in the chosen order.",request.algorithm,new Metrics(exec,prep,visited,settled),snapDistances,nodes.size(),edgeCount);
 }
 public List<Result> evaluate(Request request){return (request.preferBusierRoads?List.of("DIJKSTRA","ALT"):List.of("DIJKSTRA","TIME_DEPENDENT","ALT")).stream().map(a->route(new Request(request.journey,a,request.scenario,request.preferBusierRoads))).toList();}
}
@RestController @RequestMapping("/api/osm")
class RealRoadApi {
 private final RealRoadService roads;RealRoadApi(RealRoadService roads){this.roads=roads;}
 @GetMapping("/status") Map<String,Object>status(){return roads.status();}
 @PostMapping("/routes") RealRoadService.Result route(@Valid @RequestBody RealRoadService.Request r){return roads.route(r);}
 @PostMapping("/evaluate") List<RealRoadService.Result> evaluate(@Valid @RequestBody RealRoadService.Request r){return roads.evaluate(r);}
}
