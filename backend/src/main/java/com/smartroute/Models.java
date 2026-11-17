package com.smartroute;
import java.time.LocalDateTime;
import java.util.*;
import jakarta.validation.constraints.*;
public class Models {
 public record Place(String id,String name,double lat,double lng,String category) {}
 public record Road(String id,String from,String to,double km,double minutes,int corridor) {}
 public record Request(@NotBlank String source,@NotBlank String destination,
   @NotNull LocalDateTime departure,@Pattern(regexp="NORMAL|MORNING|EVENING|CLOSURE") @NotNull String scenario,
   @Pattern(regexp="FASTEST|SHORTEST") @NotNull String objective,
   @Pattern(regexp="DIJKSTRA|TIME_DEPENDENT|ALT|CH") @NotNull String algorithm,
   String category,String stopId,@Min(0) @Max(1440) int stopMinutes,
   @DecimalMin("0") @DecimalMax("2000") Double fuelRange,@Min(0) @Max(500) double reserve,boolean emergency) {}
 public record Metrics(double executionMs,int visitedNodes,int settledNodes,double preprocessingMs) {}
 public record Route(String id,String label,List<String> nodes,List<String> roadIds,double distanceKm,double travelMinutes,
   double stopMinutes,LocalDateTime arrival,Metrics metrics,String stopId,double detourKm,Map<String,Double> congestion) {}
 public record Result(List<Route> routes,List<Route> stopAlternatives,String message,String mode,String algorithm) {}
 public record Evaluation(String algorithm,Route route) {}
}
