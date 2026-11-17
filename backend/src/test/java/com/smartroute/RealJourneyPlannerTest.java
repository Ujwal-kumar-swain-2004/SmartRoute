package com.smartroute;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.*;
import org.springframework.web.client.RestClient;
import static com.smartroute.TomTomService.*;
class RealJourneyPlannerTest {
 TomTomService tomtom;RealRoadService osm;RealJourneyPlanner planner;
 Location source=new Location("Start",20.35,85.82),destination=new Location("End",20.25,85.82);
 SearchPlace near=new SearchPlace("near","Near pump","Bhubaneswar",20.34,85.82),better=new SearchPlace("better","Better whole journey","Bhubaneswar",20.32,85.82);
 @BeforeEach void setup(){tomtom=spy(new TomTomService(new Network(),"test-key",RestClient.create()));osm=mock(RealRoadService.class);planner=new RealJourneyPlanner(tomtom,osm);doReturn(List.of(near,better)).when(tomtom).nearby(any(),anyString(),anyInt());doAnswer(call->{LiveRequest r=call.getArgument(0);List<Location> points=tomtom.journey(r);double km=10;Location a=points.getFirst(),b=points.getLast();if(b.label().equals(near.label()))km=2;if(b.label().equals(better.label()))km=4;if(a.label().equals(near.label()))km=9;if(a.label().equals(better.label()))km=3;return result(route(km,r.departure()));}).when(tomtom).route(any());when(osm.coverage(any())).thenReturn(new RealRoadService.RoadCoverage(8,10,10,1,.8));}
 ProviderRoute route(double km,String departure){LocalDateTime d=departure==null?LocalDateTime.of(2027,1,1,9,0):LocalDateTime.parse(departure);return new ProviderRoute(km,km*100,0,d.plusSeconds((long)(km*100)).atOffset(ZoneOffset.ofHoursMinutes(5,30)).toString(),List.of(new Point(20.35,85.82),new Point(20.25,85.82)),List.of(),km*90,null,List.of(),1);}
 LiveResult result(ProviderRoute... routes){return new LiveResult("TomTom",Instant.now().toString(),List.of(routes),"Provider traffic");}
 RealJourneyPlanner.Request request(String category,Double range,double reserve,boolean busy,boolean emergency){return new RealJourneyPlanner.Request(new LiveRequest(null,null,"FASTEST",source,destination,List.of(),false,false),"TOMTOM","DIJKSTRA","NORMAL",10,range,reserve,category,null,emergency,busy);}
 @Test void recommendsBestCompleteJourneyRatherThanNearest(){var r=planner.plan(request("PETROL",null,2,false,false));assertEquals("better",r.chosenStop().id());assertEquals(7,r.routes().getFirst().distanceKm());assertEquals(600,r.stopSeconds());assertEquals(2,r.candidates().size());}
 @Test void checksRoutedFuelRangeAfterReserve(){var r=planner.plan(request("PETROL",5.0,2,false,false));assertEquals("near",r.chosenStop().id());assertEquals(2,r.firstLegKm());assertFalse(r.candidates().stream().filter(c->c.place().id().equals("better")).findFirst().orElseThrow().feasible());}
 @Test void reportsNoReachablePumpAndDoesNotInventRoutes(){var r=planner.plan(request("PETROL",1.0,0,false,false));assertTrue(r.routes().isEmpty());assertNull(r.chosenStop());assertTrue(r.notice().contains("No feasible"));}
 @Test void unusableFuelSkipsProviderRouting(){var r=planner.plan(request("PETROL",1.0,2,false,false));assertTrue(r.routes().isEmpty());verify(tomtom,never()).route(any());}
 @Test void dwellShiftsOnwardDepartureAndArrival(){var r=planner.plan(request("PETROL",null,2,false,false));var expected=LocalDateTime.of(2027,1,1,9,0).plusSeconds(700+600).atOffset(ZoneOffset.ofHoursMinutes(5,30));assertEquals(expected,OffsetDateTime.parse(r.routes().getFirst().arrival()));}
 @Test void busierPreferenceRanksMainRoadCoverageWithinAllowance(){ProviderRoute quick=route(10,null),main=route(12,null),tooLong=route(20,null);doReturn(result(quick,main,tooLong)).when(tomtom).route(any());when(osm.coverage(quick)).thenReturn(new RealRoadService.RoadCoverage(2,10,10,1,.2));when(osm.coverage(main)).thenReturn(new RealRoadService.RoadCoverage(10,12,12,1,.83));when(osm.coverage(tooLong)).thenReturn(new RealRoadService.RoadCoverage(20,20,20,1,1));var r=planner.plan(request(null,null,2,true,false));assertEquals(12,r.routes().getFirst().distanceKm());assertTrue(r.busierPreferenceApplied());assertTrue(r.notice().contains("not measured vehicle counts or a safety guarantee"));}
 @Test void insufficientCoverageDoesNotClaimPreference(){when(osm.coverage(any())).thenReturn(new RealRoadService.RoadCoverage(0,0,10,0,0));var r=planner.plan(request(null,null,2,true,false));assertFalse(r.busierPreferenceApplied());}
 @Test void emergencyKeepsFastestAndDisablesBusierRanking(){var r=planner.plan(request(null,null,2,true,true));assertFalse(r.busierPreferenceApplied());assertTrue(r.notice().contains("emergency demonstration"));}
 @Test void rejectsMixedManualAndRecommendedStops(){var base=request("PETROL",null,2,false,false);var mixed=new LiveRequest(null,null,"FASTEST",source,destination,List.of(new Location("Manual",20.3,85.82)),false,false);assertThrows(IllegalArgumentException.class,()->planner.plan(new RealJourneyPlanner.Request(mixed,"TOMTOM","DIJKSTRA","NORMAL",10,null,2,"PETROL",null,false,false)));}
}
