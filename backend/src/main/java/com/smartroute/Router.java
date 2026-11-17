package com.smartroute;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.*;
import static com.smartroute.Models.*;
@Service
public class Router {
 private final Network g;
 public Router(Network g){this.g=g;}
 public double factor(Road e,String scenario,LocalDateTime t){
  if(scenario.equals("CLOSURE") && e.id().equals("e5"))return Double.POSITIVE_INFINITY;
  double hour=t.getHour()+t.getMinute()/60.0+t.getSecond()/3600.0;
  double peak=switch(scenario){case "MORNING"->Math.exp(-Math.pow((hour-9)/2,2));case "EVENING"->Math.exp(-Math.pow((hour-18)/2,2));default->0;};
  return 1+peak*(e.corridor()==0?2.4:e.corridor()==1?1.2:0.35);
 }
 // Integrating positive, time-varying speed preserves FIFO: later departures cannot overtake.
 double duration(Road e,Request r,LocalDateTime at,boolean dynamic){
  if(!Double.isFinite(factor(e,r.scenario(),at)))return Double.POSITIVE_INFINITY;
  if(!dynamic)return e.minutes()*factor(e,r.scenario(),r.departure());
  double remaining=e.minutes(),elapsed=0;
  while(remaining>1e-9){double f=factor(e,r.scenario(),at.plusNanos((long)(elapsed*60e9)));double step=Math.min(0.25,remaining*f);remaining-=step/f;elapsed+=step;}
  return elapsed;
 }
 record Entry(String id,double cost,double priority){}
 record Path(List<String> nodes,List<String> edges,double cost,int visited,int settled,double prep){}
 public Route route(Request r,String source,String target,Set<String> banned,String algorithm){
  long start=System.nanoTime();
  Path p=algorithm.equals("CH")?ch(r,source,target,banned):search(r,source,target,banned,algorithm);
  if(p==null)return null;
  double distance=0,time=0;Map<String,Double> congestion=new LinkedHashMap<>();
  for(String id:p.edges){Road e=g.roads.stream().filter(x->x.id().equals(id)).findFirst().orElseThrow();double d=duration(e,r,r.departure().plusNanos((long)(time*60e9)),algorithm.equals("TIME_DEPENDENT"));distance+=e.km();time+=d;congestion.put(id,d/e.minutes());}
  return new Route(UUID.randomUUID().toString(),algorithm,p.nodes,p.edges,distance,time,0,r.departure().plusNanos((long)(time*60e9)),new Metrics((System.nanoTime()-start)/1e6,p.visited,p.settled,p.prep),null,0,congestion);
 }
 double weight(Road e,Request r){return r.objective().equals("SHORTEST")&&!r.emergency()?e.km():duration(e,r,r.departure(),false);}
 Path search(Request r,String source,String target,Set<String> banned,String algorithm){
  Map<String,Double> dist=new HashMap<>();Map<String,String> prev=new HashMap<>(),edge=new HashMap<>();
  Map<String,Map<String,Double>> landmarks=new HashMap<>();long prep=System.nanoTime();
  if(algorithm.equals("ALT"))for(String landmark:List.of("p0","p10","p13"))landmarks.put(landmark,distances(r,landmark,banned));
  double prepMs=algorithm.equals("ALT")?(System.nanoTime()-prep)/1e6:0;
  PriorityQueue<Entry> q=new PriorityQueue<>(Comparator.comparingDouble(Entry::priority));dist.put(source,0.0);q.add(new Entry(source,0,0));Set<String> settled=new HashSet<>();
  while(!q.isEmpty()){
   Entry u=q.poll();if(!settled.add(u.id))continue;if(u.id.equals(target))break;
   for(Road e:g.adjacency.get(u.id)){
    if(banned.contains(e.id()))continue;String v=g.other(e,u.id);
    double w=algorithm.equals("TIME_DEPENDENT")&&(!r.objective().equals("SHORTEST")||r.emergency())?duration(e,r,r.departure().plusNanos((long)(u.cost*60e9)),true):weight(e,r);
    double nd=u.cost+w;if(nd<dist.getOrDefault(v,Double.POSITIVE_INFINITY)){
     dist.put(v,nd);prev.put(v,u.id);edge.put(v,e.id());double h=0;
     for(var d:landmarks.values())if(d.containsKey(v)&&d.containsKey(target))h=Math.max(h,Math.abs(d.get(target)-d.get(v)));
     q.add(new Entry(v,nd,nd+h));
    }
   }
  }
  if(!settled.contains(target))return null;
  LinkedList<String> nodes=new LinkedList<>(),edges=new LinkedList<>();String cur=target;nodes.addFirst(cur);
  while(!cur.equals(source)){edges.addFirst(edge.get(cur));cur=prev.get(cur);nodes.addFirst(cur);}
  return new Path(nodes,edges,dist.get(target),dist.size(),settled.size(),prepMs);
 }
 Map<String,Double> distances(Request r,String s,Set<String> banned){
  Map<String,Double>d=new HashMap<>();PriorityQueue<Entry>q=new PriorityQueue<>(Comparator.comparingDouble(Entry::cost));d.put(s,0.0);q.add(new Entry(s,0,0));
  while(!q.isEmpty()){Entry u=q.poll();if(u.cost>d.get(u.id))continue;for(Road e:g.adjacency.get(u.id)){if(banned.contains(e.id()))continue;String v=g.other(e,u.id);double nd=u.cost+weight(e,r);if(nd<d.getOrDefault(v,Double.POSITIVE_INFINITY)){d.put(v,nd);q.add(new Entry(v,nd,nd));}}}return d;
 }
 record Arc(String to,double cost,List<String> nodes,List<String> edges){}
 // Exact contraction: retain all improving shortcuts (no witness pruning).
 // Query explores only increasing ranks from both endpoints, then unpacks shortcuts.
 Path ch(Request r,String source,String target,Set<String>banned){
  long start=System.nanoTime();Map<String,Integer>rank=new HashMap<>();Map<String,Map<String,Arc>>graph=new HashMap<>();
  List<Place>order=new ArrayList<>(g.places);order.sort(Comparator.comparingInt(p->g.adjacency.get(p.id()).size()));
  for(int i=0;i<order.size();i++){rank.put(order.get(i).id(),i);graph.put(order.get(i).id(),new HashMap<>());}
  for(Road e:g.roads){if(banned.contains(e.id())||!Double.isFinite(weight(e,r)))continue;put(graph,e.from(),new Arc(e.to(),weight(e,r),List.of(e.from(),e.to()),List.of(e.id())));put(graph,e.to(),new Arc(e.from(),weight(e,r),List.of(e.to(),e.from()),List.of(e.id())));}
  for(Place p:order){String v=p.id();List<Arc>neighbors=graph.get(v).values().stream().filter(a->rank.get(a.to)>rank.get(v)).toList();
   for(Arc a:neighbors)for(Arc b:neighbors){if(a.to.equals(b.to))continue;List<String> ns=new ArrayList<>(a.nodes);Collections.reverse(ns);ns.addAll(b.nodes.subList(1,b.nodes.size()));List<String> es=new ArrayList<>(a.edges);Collections.reverse(es);es.addAll(b.edges);put(graph,a.to,new Arc(b.to,a.cost+b.cost,ns,es));}
  }
  double prep=(System.nanoTime()-start)/1e6;
  Map<String,Double>left=new HashMap<>(),right=new HashMap<>();Map<String,Arc>lp=new HashMap<>(),rp=new HashMap<>();int[]counts={0,0};
  upward(source,graph,rank,left,lp,counts);upward(target,graph,rank,right,rp,counts);
  String meet=null;double best=Double.POSITIVE_INFINITY;for(String v:left.keySet()){double cost=left.get(v)+right.getOrDefault(v,Double.POSITIVE_INFINITY);if(cost<best){best=cost;meet=v;}}
  if(meet==null)return null;
  List<String> ns=new ArrayList<>(),es=new ArrayList<>();List<Arc>chain=new ArrayList<>();String cur=meet;
  while(!cur.equals(source)){Arc a=lp.get(cur);chain.add(a);cur=a.nodes.getFirst();}Collections.reverse(chain);ns.add(source);for(Arc a:chain){ns.addAll(a.nodes.subList(1,a.nodes.size()));es.addAll(a.edges);}
  cur=meet;while(!cur.equals(target)){Arc a=rp.get(cur);List<String> rev=new ArrayList<>(a.nodes);Collections.reverse(rev);ns.addAll(rev.subList(1,rev.size()));List<String> re=new ArrayList<>(a.edges);Collections.reverse(re);es.addAll(re);cur=a.nodes.getFirst();}
  return new Path(ns,es,best,counts[0],counts[1],prep);
 }
 void put(Map<String,Map<String,Arc>>g,String from,Arc arc){Arc old=g.get(from).get(arc.to);if(old==null||arc.cost<old.cost)g.get(from).put(arc.to,arc);}
 void upward(String s,Map<String,Map<String,Arc>>graph,Map<String,Integer>rank,Map<String,Double>d,Map<String,Arc>prev,int[]counts){
  PriorityQueue<Entry>q=new PriorityQueue<>(Comparator.comparingDouble(Entry::cost));d.put(s,0.0);q.add(new Entry(s,0,0));Set<String>done=new HashSet<>();
  while(!q.isEmpty()){Entry u=q.poll();if(!done.add(u.id))continue;counts[1]++;for(Arc a:graph.get(u.id).values()){if(rank.get(a.to)<=rank.get(u.id))continue;double nd=u.cost+a.cost;if(nd<d.getOrDefault(a.to,Double.POSITIVE_INFINITY)){d.put(a.to,nd);prev.put(a.to,a);q.add(new Entry(a.to,nd,nd));}}}counts[0]+=d.size();
 }
}
