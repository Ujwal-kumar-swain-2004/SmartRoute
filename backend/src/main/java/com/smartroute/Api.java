package com.smartroute;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import jakarta.validation.Valid;
import java.util.*;
import static com.smartroute.Models.*;
@RestController @RequestMapping("/api")
public class Api {
 @org.springframework.beans.factory.annotation.Value("${tomtom.api-key:}") private String tomtomKey="";
 private final Network network;private final Planner planner;
 public Api(Network network,Planner planner){this.network=network;this.planner=planner;}
 @GetMapping("/network") public Map<String,Object> network(){return Map.of("places",network.places,"roads",network.roads,"mode","DEMO");}
 @GetMapping("/capabilities") public Map<String,Object> capabilities(){return Map.of("mode","DEMO","realProviderEnabled",!tomtomKey.isBlank(),"algorithms",List.of("DIJKSTRA","TIME_DEPENDENT","ALT","CH"),"traffic","Simulated; not live traffic","graph","Synthetic Bhubaneswar network");}
 @GetMapping("/scenarios") public List<String> scenarios(){return List.of("NORMAL","MORNING","EVENING","CLOSURE");}
 @PostMapping({"/routes","/alternatives","/stops","/fuel"}) public Result routes(@Valid @RequestBody Request r){return planner.plan(r);}
 @PostMapping("/evaluate") public List<Evaluation> evaluate(@Valid @RequestBody Request r){return List.of("DIJKSTRA","TIME_DEPENDENT","ALT","CH").stream().map(a->{Result result=planner.calculate(planner.with(r,r.source(),r.destination(),r.departure(),a));return new Evaluation(a,result.routes().isEmpty()?null:result.routes().getFirst());}).toList();}
 @RestControllerAdvice public static class Errors {
  @ExceptionHandler(IllegalArgumentException.class) ResponseEntity<?> bad(IllegalArgumentException e){return ResponseEntity.badRequest().body(Map.of("error","INVALID_REQUEST","message",e.getMessage()));}
  @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<?> invalid(MethodArgumentNotValidException e){return ResponseEntity.badRequest().body(Map.of("error","VALIDATION_ERROR","message",e.getBindingResult().getFieldErrors().stream().map(x->x.getField()+": "+x.getDefaultMessage()).toList()));}
  @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class) ResponseEntity<?> malformed(Exception e){return ResponseEntity.badRequest().body(Map.of("error","INVALID_JSON","message","Provide valid JSON and a local ISO departure date/time."));}
 }
}

