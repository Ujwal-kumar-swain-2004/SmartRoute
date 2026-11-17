package com.smartroute;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDateTime;
import java.util.*;
import static com.smartroute.Models.*;
class RoutingTest {
 Network g=new Network();Router router=new Router(g);Planner planner=new Planner(g,router);
 Request req(String s,String t,String algorithm,String objective,String scenario){return new Request(s,t,LocalDateTime.of(2026,11,15,9,0),scenario,objective,algorithm,null,null,10,null,2,false);}
 @Test void staticAlgorithmsAgreeForEveryPair(){for(String scenario:List.of("NORMAL","MORNING","CLOSURE"))for(String objective:List.of("FASTEST","SHORTEST"))for(Place a:g.places)for(Place b:g.places){Request r=req(a.id(),b.id(),"DIJKSTRA",objective,scenario);Route base=router.route(r,a.id(),b.id(),Set.of(),"DIJKSTRA");for(String alg:List.of("ALT","CH")){Route actual=router.route(r,a.id(),b.id(),Set.of(),alg);assertNotNull(actual);assertEquals(objective.equals("FASTEST")?base.travelMinutes():base.distanceKm(),objective.equals("FASTEST")?actual.travelMinutes():actual.distanceKm(),1e-7,a.id()+"->"+b.id()+" "+alg);assertEquals(a.id(),actual.nodes().getFirst());assertEquals(b.id(),actual.nodes().getLast());assertEquals(actual.nodes().size()-1,actual.roadIds().size());}}}
 @Test void closureIsExcluded(){Request r=req("p3","p4","TIME_DEPENDENT","FASTEST","CLOSURE");Route a=router.route(r,"p3","p4",Set.of(),r.algorithm());assertFalse(a.roadIds().contains("e5"));}
 @Test void unreachableFuelIsReported(){Request r=new Request("p0","p9",LocalDateTime.now(),"NORMAL","FASTEST","DIJKSTRA","PETROL",null,10,0.1,2,false);Result result=planner.calculate(r);assertTrue(result.routes().isEmpty());assertTrue(result.message().contains("fuel range"));}
 @Test void stopAndDwellAreIncluded(){Request r=new Request("p0","p9",LocalDateTime.of(2026,11,15,9,0),"MORNING","FASTEST","TIME_DEPENDENT","PETROL",null,15,10.0,2,false);Route route=planner.calculate(r).routes().getFirst();assertNotNull(route.stopId());assertTrue(route.nodes().contains(route.stopId()));assertEquals(15,route.stopMinutes());assertEquals(r.departure().plusNanos((long)((route.travelMinutes()+15)*60e9)),route.arrival());}
 @Test void recommendationMinimizesWholeJourney(){Request r=new Request("p0","p9",LocalDateTime.now(),"NORMAL","SHORTEST","ALT","HOTEL",null,10,null,2,false);Result result=planner.calculate(r);for(Route other:result.stopAlternatives())assertTrue(result.routes().getFirst().distanceKm()<=other.distanceKm());}
 @Test void invalidStopsRejected(){Request r=new Request("p0","p9",LocalDateTime.now(),"NORMAL","FASTEST","CH","HOTEL","f0",10,null,2,false);assertThrows(IllegalArgumentException.class,()->planner.calculate(r));}
 @Test void alternativesAreDistinct(){Result result=planner.calculate(req("p0","p9","DIJKSTRA","FASTEST","NORMAL"));assertTrue(result.routes().size()>1);assertTrue(result.routes().size()<=3);assertEquals(result.routes().size(),result.routes().stream().map(Route::nodes).distinct().count());}
 @Test void dynamicTravelIsFifo(){Road e=g.roads.getFirst();Request r=req("p0","p9","TIME_DEPENDENT","FASTEST","MORNING");double previous=-1;for(int m=0;m<240;m++){var dep=r.departure().plusMinutes(m);double arrival=m+router.duration(e,r,dep,true);assertTrue(arrival>=previous);previous=arrival;}}
}
