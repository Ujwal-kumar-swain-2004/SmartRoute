package com.smartroute;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.web.client.RestClient;
import static org.junit.jupiter.api.Assertions.*;
import static com.smartroute.TomTomService.*;
import java.util.*;
class RealRoadServiceTest {
 RealRoadService roads;TomTomService tomtom;
 List<Location> points=List.of(new Location("A",20.30,85.82),new Location("B",20.30,85.83),new Location("C",20.31,85.83),new Location("D",20.31,85.82));
 @BeforeEach void setup()throws Exception{tomtom=new TomTomService(new Network(),"",RestClient.create());try(var stream=getClass().getResourceAsStream("/osm-fixture.json")){roads=new RealRoadService(tomtom,new ObjectMapper().readTree(stream));}}
 RealRoadService.Request request(Location a,Location b,String algorithm,String objective,boolean tolls,boolean highways){return new RealRoadService.Request(new LiveRequest(null,null,objective,a,b,List.of(),tolls,highways),algorithm,"NORMAL");}
 @Test void importsDirectedTopologyAndExcludesPrivateRoads(){assertEquals(6,roads.status().get("nodes"));var r=roads.route(request(points.get(2),points.get(1),"DIJKSTRA","SHORTEST",true,true));assertEquals(4,r.routes().getFirst().points().size());assertFalse(r.routes().getFirst().instructions().stream().anyMatch(i->i.street().equals("Private Shortcut")));}
 @Test void altAgreesWithDijkstraOnEveryDirectedPair(){for(Location a:points)for(Location b:points){if(a.equals(b))continue;for(String objective:List.of("FASTEST","SHORTEST")){var d=roads.route(request(a,b,"DIJKSTRA",objective,false,false)).routes().getFirst();var alt=roads.route(request(a,b,"ALT",objective,false,false)).routes().getFirst();assertEquals(d.distanceKm(),alt.distanceKm(),1e-8);assertEquals(d.travelSeconds(),alt.travelSeconds(),1e-8);}}}
 @Test void respectsAvoidPreferences(){var r=roads.route(request(points.get(0),points.get(2),"ALT","FASTEST",true,true));assertFalse(r.routes().getFirst().instructions().stream().anyMatch(i->Set.of("Highway","Toll Road").contains(i.street())));}
 @Test void visitsMultipleStopsInOrder(){var j=new LiveRequest(null,null,"FASTEST",points.get(0),points.get(2),List.of(points.get(1),points.get(3)),true,true);var r=roads.route(new RealRoadService.Request(j,"DIJKSTRA","NORMAL"));assertEquals(3,r.routes().getFirst().legCount());assertEquals(4,r.snapDistancesMeters().size());assertTrue(r.routes().getFirst().distanceKm()>3);}
 @Test void timeDependentPeakIncreasesEstimatedTravel(){var normal=request(points.get(0),points.get(2),"TIME_DEPENDENT","FASTEST",true,true);var peak=new RealRoadService.Request(normal.journey(),normal.algorithm(),"MORNING");assertTrue(roads.route(peak).routes().getFirst().travelSeconds()>roads.route(normal).routes().getFirst().travelSeconds());}
 @Test void rejectsLocationsOutsideExtract(){assertThrows(IllegalArgumentException.class,()->roads.route(request(new Location("Outside",19,80),points.get(1),"DIJKSTRA","FASTEST",false,false)));}
 @Test void exposesMeasuredMetricsAndThreeAlgorithms(){var results=roads.evaluate(request(points.get(0),points.get(2),"DIJKSTRA","FASTEST",false,false));assertEquals(3,results.size());for(var result:results){assertTrue(result.metrics().executionMs()>0);assertTrue(result.metrics().settledNodes()>0);assertTrue(result.notice().contains("Not live traffic"));}}
 @Test void parsesSpeedUnits(){assertEquals(48.28032,RealRoadService.speed("30 mph","primary"),1e-6);assertEquals(15,RealRoadService.speed("unknown","service"));}
}
