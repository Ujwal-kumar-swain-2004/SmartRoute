package com.smartroute;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import java.net.http.HttpClient;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TomTomService {
 public record Location(@NotBlank @Size(max=200) String label,@DecimalMin("-90") @DecimalMax("90") double lat,@DecimalMin("-180") @DecimalMax("180") double lng) {}
 public record LiveRequest(String source,String destination,@Pattern(regexp="FASTEST|SHORTEST") @NotBlank String objective,
   @Valid Location origin,@Valid Location destinationPoint,@Size(max=5) List<@Valid Location> stops,boolean avoidTolls,boolean avoidHighways,String departure){
  public LiveRequest {stops=stops==null?List.of():List.copyOf(stops);}
  public LiveRequest(String source,String destination,String objective,Location origin,Location destinationPoint,List<Location> stops,boolean avoidTolls,boolean avoidHighways){this(source,destination,objective,origin,destinationPoint,stops,avoidTolls,avoidHighways,null);}
  public LiveRequest(String source,String destination,String objective){this(source,destination,objective,null,null,List.of(),false,false);}
 }
 public record Point(double lat,double lng) {}
 public record SearchPlace(String id,String label,String address,double lat,double lng) {}
 public record Section(int start,int end,String category,int magnitude,double delaySeconds) {}
 public record Instruction(double offsetMeters,double travelSeconds,String message,String street,String maneuver,Point point) {}
 public record ProviderRoute(double distanceKm,double travelSeconds,double delaySeconds,String arrival,List<Point> points,List<Section> sections,
   Double freeFlowSeconds,Double historicalSeconds,List<Instruction> instructions,int legCount) {}
 public record LiveResult(String provider,String fetchedAt,List<ProviderRoute> routes,String notice) {}
 public record Flow(String provider,String fetchedAt,double currentSpeed,double freeFlowSpeed,double confidence,boolean roadClosure,List<Point> points) {}
 private final Network network;private final String key;private final RestClient client;
 private final Cache<LiveRequest,LiveResult> routes=Caffeine.newBuilder().maximumSize(200).expireAfterWrite(Duration.ofSeconds(60)).build();
 private final Cache<String,Flow> flows=Caffeine.newBuilder().maximumSize(100).expireAfterWrite(Duration.ofSeconds(60)).build();
 private final Cache<String,List<SearchPlace>> searches=Caffeine.newBuilder().maximumSize(300).expireAfterWrite(Duration.ofMinutes(10)).build();
 @org.springframework.beans.factory.annotation.Autowired public TomTomService(Network network,@Value("${tomtom.api-key:}") String key){this(network,key,createClient());}
 TomTomService(Network network,String key,RestClient client){this.network=network;this.key=key;this.client=client;}
 private static RestClient createClient(){var factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());factory.setReadTimeout(Duration.ofSeconds(25));return RestClient.builder().baseUrl("https://api.tomtom.com").requestFactory(factory).build();}
 public boolean configured(){return key!=null&&!key.isBlank();}
 private void requireKey(){if(!configured())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"TomTom is not configured. Set TOMTOM_API_KEY on the backend and restart it.");}
 Location resolve(String id,Location custom){if(custom!=null){if(!Double.isFinite(custom.lat())||!Double.isFinite(custom.lng())||Math.abs(custom.lat())>90||Math.abs(custom.lng())>180)throw new IllegalArgumentException("Invalid location coordinates.");return custom;}var p=network.byId.get(id==null?"":id);if(p==null)throw new IllegalArgumentException("Choose a location using search, a landmark, or the map.");return new Location(p.name(),p.lat(),p.lng());}
 private String coordinates(Location p){return p.lat()+","+p.lng();}
 public List<Location> journey(LiveRequest request){List<Location> points=new ArrayList<>();points.add(resolve(request.source(),request.origin()));if(request.stops().size()>5)throw new IllegalArgumentException("At most five intermediate stops are supported.");for(Location stop:request.stops())points.add(resolve(null,stop));points.add(resolve(request.destination(),request.destinationPoint()));for(int i=1;i<points.size();i++)if(Math.abs(points.get(i).lat()-points.get(i-1).lat())<1e-7&&Math.abs(points.get(i).lng()-points.get(i-1).lng())<1e-7)throw new IllegalArgumentException("Consecutive journey locations must be different.");return points;}
 public LiveResult route(LiveRequest request){requireKey();journey(request);return routes.get(request,this::loadRoute);}
 private LiveResult loadRoute(LiveRequest request){
  String locations=journey(request).stream().map(this::coordinates).collect(Collectors.joining(":"));
  Map<String,List<String>> params=new LinkedHashMap<>();params.put("traffic",List.of("true"));params.put("departAt",List.of(request.departure()==null?"now":LocalDateTime.parse(request.departure()).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))));params.put("routeType",List.of(request.objective().equals("SHORTEST")?"shortest":"fastest"));params.put("travelMode",List.of("car"));params.put("maxAlternatives",List.of(request.stops().isEmpty()?"2":"0"));params.put("sectionType",List.of("traffic"));params.put("computeTravelTimeFor",List.of("all"));params.put("instructionsType",List.of("text"));params.put("language",List.of("en-GB"));
  List<String> avoid=new ArrayList<>();if(request.avoidTolls())avoid.add("tollRoads");if(request.avoidHighways())avoid.add("motorways");if(!avoid.isEmpty())params.put("avoid",avoid);
  JsonNode response=getMany("/routing/1/calculateRoute/"+locations+"/json",params);
  List<ProviderRoute> result=new ArrayList<>();
  for(JsonNode route:response.path("routes")){
   JsonNode summary=route.path("summary");List<Point> points=new ArrayList<>();
   for(JsonNode leg:route.path("legs"))for(JsonNode point:leg.path("points"))points.add(new Point(point.path("latitude").asDouble(),point.path("longitude").asDouble()));
   if(points.size()<2||!summary.has("travelTimeInSeconds"))throw unavailable("TomTom returned an incomplete route.");
   List<Section> sections=new ArrayList<>();for(JsonNode section:route.path("sections"))if(section.path("sectionType").asText().equals("TRAFFIC"))sections.add(new Section(section.path("startPointIndex").asInt(),section.path("endPointIndex").asInt(),section.path("simpleCategory").asText("OTHER"),section.path("magnitudeOfDelay").asInt(),section.path("delayInSeconds").asDouble()));
   List<Instruction> instructions=new ArrayList<>();for(JsonNode i:route.path("guidance").path("instructions")){JsonNode p=i.path("point");instructions.add(new Instruction(i.path("routeOffsetInMeters").asDouble(),i.path("travelTimeInSeconds").asDouble(),i.path("message").asText(i.path("maneuver").asText("Continue")),i.path("street").asText(""),i.path("maneuver").asText(""),new Point(p.path("latitude").asDouble(),p.path("longitude").asDouble())));}
   result.add(new ProviderRoute(summary.path("lengthInMeters").asDouble()/1000,summary.path("travelTimeInSeconds").asDouble(),summary.path("trafficDelayInSeconds").asDouble(),summary.path("arrivalTime").asText(),points,sections,number(summary,"noTrafficTravelTimeInSeconds"),number(summary,"historicTrafficTravelTimeInSeconds"),instructions,route.path("legs").size()));
  }
  if(result.isEmpty())throw unavailable("TomTom found no available routes for this journey.");
  return new LiveResult("TomTom",Instant.now().toString(),result,"TomTom-computed driving routes for the selected departure; available live and historical traffic. Stops are visited in your chosen order, without dwell time. Alternatives are available for journeys without intermediate stops. Avoidance is a provider preference, not a guarantee. Cached for up to 60 seconds.");
 }
 private Double number(JsonNode node,String key){return node.hasNonNull(key)?node.get(key).asDouble():null;}
 public List<SearchPlace> search(String query){requireKey();String q=query==null?"":query.trim();if(q.length()<2||q.length()>120)throw new IllegalArgumentException("Search must be between 2 and 120 characters.");return searches.get(q,value->{JsonNode response=getSearch(value);List<SearchPlace> results=new ArrayList<>();for(JsonNode item:response.path("results")){JsonNode p=item.path("position");if(!p.has("lat")||!p.has("lon"))continue;String address=item.path("address").path("freeformAddress").asText("");String label=item.path("poi").path("name").asText(address);results.add(new SearchPlace(item.path("id").asText(),label,address,p.path("lat").asDouble(),p.path("lon").asDouble()));}return List.copyOf(results);});}
 private JsonNode getSearch(String query){return getMany("/search/2/search/{query}.json",Map.of("countrySet",List.of("IN"),"lat",List.of("20.296"),"lon",List.of("85.824"),"limit",List.of("6"),"typeahead",List.of("false")),Map.of("query",query));}
 public List<SearchPlace> nearby(Location center,String category,int radius){requireKey();resolve(null,center);String id=switch(category){case "PETROL"->"7311";case "RESTAURANT"->"7315";case "HOTEL"->"7314";default->throw new IllegalArgumentException("Choose petrol, restaurant or hotel.");};String cacheKey="nearby:"+category+":"+coordinates(center)+":"+radius;return searches.get(cacheKey,k->{JsonNode response=getMany("/search/2/nearbySearch/.json",Map.of("lat",List.of(Double.toString(center.lat())),"lon",List.of(Double.toString(center.lng())),"radius",List.of(Integer.toString(Math.max(200,Math.min(20000,radius)))),"categorySet",List.of(id),"limit",List.of("6")));List<SearchPlace> result=new ArrayList<>();for(JsonNode item:response.path("results")){JsonNode p=item.path("position");if(!p.has("lat")||!p.has("lon"))continue;String address=item.path("address").path("freeformAddress").asText("");result.add(new SearchPlace(item.path("id").asText(),item.path("poi").path("name").asText(address),address,p.path("lat").asDouble(),p.path("lon").asDouble()));}return List.copyOf(result);});}
 public Flow flow(String placeId){return flowAt(resolve(placeId,null));}
 public Flow flowAt(Location location){requireKey();Location p=resolve(null,location);return flows.get(coordinates(p),id->loadFlow(p));}
 private Flow loadFlow(Location location){JsonNode data=getMany("/traffic/services/4/flowSegmentData/absolute/14/json",Map.of("point",List.of(coordinates(location)),"unit",List.of("kmph"))).path("flowSegmentData");
  if(!data.has("currentSpeed")||!data.has("confidence"))throw unavailable("No traffic flow observation is available near this location.");
  List<Point> points=new ArrayList<>();JsonNode coordinates=data.path("coordinates");JsonNode values=coordinates.isArray()?coordinates:coordinates.path("coordinate");for(JsonNode p:values)points.add(new Point(p.path("latitude").asDouble(),p.path("longitude").asDouble()));
  return new Flow("TomTom",Instant.now().toString(),data.path("currentSpeed").asDouble(),data.path("freeFlowSpeed").asDouble(),data.path("confidence").asDouble(),data.path("roadClosure").asBoolean(),points);
 }
 private JsonNode getMany(String path,Map<String,List<String>> params){return getMany(path,params,Map.of());}
 private JsonNode getMany(String path,Map<String,List<String>> params,Map<String,String> variables){
  try{JsonNode result=client.get().uri(builder->{builder.path(path).queryParam("key",key);params.forEach((k,values)->values.forEach(v->builder.queryParam(k,v)));return builder.build(variables);}).retrieve().body(JsonNode.class);if(result==null)throw unavailable("TomTom returned an empty response.");return result;}
  catch(RestClientResponseException e){int code=e.getStatusCode().value();if(code==401||code==403)throw unavailable("TomTom rejected the API key or this product is not enabled for it.");if(code==429)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"TomTom quota exceeded. Wait before retrying or check your provider plan.");if(code==400||code==404)throw unavailable("TomTom has no usable data for this request or location.");throw unavailable("TomTom is temporarily unavailable. Try again later.");}
  catch(ResponseStatusException e){throw e;}
  catch(Exception e){throw unavailable("Could not reach TomTom. Check the backend internet connection and try again.");}
 }
 private ResponseStatusException unavailable(String message){return new ResponseStatusException(HttpStatus.BAD_GATEWAY,message);}
}

@RestController @RequestMapping("/api/tomtom")
class TomTomApi {
 private final TomTomService service;
 TomTomApi(TomTomService service){this.service=service;}
 @GetMapping("/status") Map<String,Object> status(){return Map.of("configured",service.configured(),"provider","TomTom","cacheSeconds",60);}
 @PostMapping("/routes") TomTomService.LiveResult route(@Valid @RequestBody TomTomService.LiveRequest request){return service.route(request);}
 @GetMapping("/search") List<TomTomService.SearchPlace> search(@RequestParam String q){return service.search(q);}
 @GetMapping("/flow") TomTomService.Flow flow(@RequestParam String placeId){return service.flow(placeId);}
 @PostMapping("/flow") TomTomService.Flow flowAt(@Valid @RequestBody TomTomService.Location location){return service.flowAt(location);}
}
