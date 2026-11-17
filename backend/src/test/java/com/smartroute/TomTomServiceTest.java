package com.smartroute;
import org.junit.jupiter.api.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
class TomTomServiceTest {
 MockRestServiceServer server;TomTomService service;
 @BeforeEach void setup(){var builder=RestClient.builder().baseUrl("https://api.tomtom.com");server=MockRestServiceServer.bindTo(builder).build();service=new TomTomService(new Network(),"test-key",builder.build());}
 @Test void mapsProviderRouteAndCaches(){server.expect(requestTo(org.hamcrest.Matchers.containsString("traffic=true"))).andRespond(withSuccess("""
 {"routes":[{"summary":{"lengthInMeters":12000,"travelTimeInSeconds":1200,"trafficDelayInSeconds":120,"arrivalTime":"2026-11-16T10:20:00+05:30"},"legs":[{"points":[{"latitude":20.35,"longitude":85.82},{"latitude":20.25,"longitude":85.81}]}],"sections":[{"sectionType":"TRAFFIC","startPointIndex":0,"endPointIndex":1,"simpleCategory":"JAM","magnitudeOfDelay":2,"delayInSeconds":120}]}]}
 """,MediaType.APPLICATION_JSON));var request=new TomTomService.LiveRequest("p0","p9","FASTEST");var result=service.route(request);assertEquals("TomTom",result.provider());assertEquals(12,result.routes().getFirst().distanceKm());assertEquals(120,result.routes().getFirst().delaySeconds());assertEquals(2,result.routes().getFirst().points().size());assertEquals("JAM",result.routes().getFirst().sections().getFirst().category());assertSame(result,service.route(request));server.verify();}
 @Test void mapsFlowSample(){server.expect(requestTo(org.hamcrest.Matchers.containsString("flowSegmentData"))).andRespond(withSuccess("""
 {"flowSegmentData":{"currentSpeed":22,"freeFlowSpeed":40,"confidence":0.8,"roadClosure":false,"coordinates":{"coordinate":[{"latitude":20.35,"longitude":85.82}]}}}
 """,MediaType.APPLICATION_JSON));var flow=service.flow("p0");assertEquals(22,flow.currentSpeed());assertEquals(.8,flow.confidence());assertEquals(1,flow.points().size());assertSame(flow,service.flow("p0"));server.verify();}
 @Test void hidesCredentialInProviderErrors(){server.expect(anything()).andRespond(withStatus(HttpStatus.FORBIDDEN).body("secret test-key"));var error=assertThrows(ResponseStatusException.class,()->service.flow("p0"));assertFalse(error.getReason().contains("test-key"));assertTrue(error.getReason().contains("API key"));assertEquals(502,error.getStatusCode().value());}
 @Test void reportsQuota(){server.expect(anything()).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));assertEquals(429,assertThrows(ResponseStatusException.class,()->service.flow("p0")).getStatusCode().value());}
 @Test void rejectsMissingKeyAndUnknownLocations(){var disabled=new TomTomService(new Network(),"",RestClient.create());assertFalse(disabled.configured());assertEquals(503,assertThrows(ResponseStatusException.class,()->disabled.flow("p0")).getStatusCode().value());assertThrows(IllegalArgumentException.class,()->service.flow("unknown"));assertThrows(IllegalArgumentException.class,()->service.route(new TomTomService.LiveRequest("p0","p0","FASTEST")));}
 @Test void missingFlowDoesNotBecomeZeroSpeed(){server.expect(anything()).andRespond(withSuccess("{}",MediaType.APPLICATION_JSON));assertThrows(ResponseStatusException.class,()->service.flow("p0"));}

 @Test void sendsCoordinateWaypointsAvoidanceAndDirections(){
  server.expect(requestTo(org.hamcrest.Matchers.allOf(org.hamcrest.Matchers.containsString("avoid=tollRoads"),org.hamcrest.Matchers.containsString("avoid=motorways"),org.hamcrest.Matchers.containsString("instructionsType=text"),org.hamcrest.Matchers.containsString("20.3,85.83")))).andRespond(withSuccess("""
  {"routes":[{"summary":{"lengthInMeters":14000,"travelTimeInSeconds":1400,"noTrafficTravelTimeInSeconds":1000,"historicTrafficTravelTimeInSeconds":1200,"arrivalTime":"2026-11-16T10:00:00+05:30"},"legs":[{"points":[{"latitude":20.35,"longitude":85.82},{"latitude":20.3,"longitude":85.83}]},{"points":[{"latitude":20.3,"longitude":85.83},{"latitude":20.25,"longitude":85.81}]}],"guidance":{"instructions":[{"message":"Turn left","street":"Test Road","routeOffsetInMeters":100,"travelTimeInSeconds":10,"maneuver":"TURN_LEFT","point":{"latitude":20.3,"longitude":85.83}}]}}]}
  """,MediaType.APPLICATION_JSON));
  var request=new TomTomService.LiveRequest(null,null,"FASTEST",new TomTomService.Location("A",20.35,85.82),new TomTomService.Location("B",20.25,85.81),java.util.List.of(new TomTomService.Location("Stop",20.3,85.83)),true,true);
  var route=service.route(request).routes().getFirst();assertEquals(2,route.legCount());assertEquals(1000,route.freeFlowSeconds());assertEquals("Turn left",route.instructions().getFirst().message());server.verify();
 }
 @Test void searchReturnsNamesAddressesAndCoordinates(){server.expect(requestTo(org.hamcrest.Matchers.containsString("countrySet=IN"))).andRespond(withSuccess("""
 {"results":[{"id":"poi1","poi":{"name":"City Museum"},"address":{"freeformAddress":"Bhubaneswar"},"position":{"lat":20.29,"lon":85.83}}]}
 """,MediaType.APPLICATION_JSON));var results=service.search("Museum & park");assertEquals("City Museum",results.getFirst().label());assertEquals(20.29,results.getFirst().lat());assertSame(results,service.search("Museum & park"));server.verify();}
 @Test void validatesSearchAndStopLimits(){assertThrows(IllegalArgumentException.class,()->service.search("a"));var stop=new TomTomService.Location("stop",20.3,85.83);assertThrows(IllegalArgumentException.class,()->service.route(new TomTomService.LiveRequest("p0","p9","FASTEST",null,null,java.util.Collections.nCopies(6,stop),false,false)));}

 @Test void formatsScheduledDepartureWithSeconds(){server.expect(requestTo(org.hamcrest.Matchers.containsString("departAt=2027-01-01T09:00:00"))).andRespond(withSuccess("""
 {"routes":[{"summary":{"lengthInMeters":1000,"travelTimeInSeconds":100,"arrivalTime":"2027-01-01T09:01:40+05:30"},"legs":[{"points":[{"latitude":20.35,"longitude":85.82},{"latitude":20.25,"longitude":85.81}]}]}]}
 """,MediaType.APPLICATION_JSON));service.route(new TomTomService.LiveRequest("p0","p9","FASTEST",null,null,java.util.List.of(),false,false,"2027-01-01T09:00"));server.verify();}
}
