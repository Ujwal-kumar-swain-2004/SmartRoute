package com.smartroute;
import org.springframework.stereotype.Component;
import java.util.*;
import static com.smartroute.Models.*;
@Component
public class Network {
 public final List<Place> places=new ArrayList<>();
 public final List<Road> roads=new ArrayList<>();
 public final Map<String,Place> byId=new LinkedHashMap<>();
 public final Map<String,List<Road>> adjacency=new HashMap<>();
 public Network(){
  add("p0","Patia Square",20.3553,85.8240,"LANDMARK");
  add("p1","KIIT Square",20.3531,85.8170,"LANDMARK");
  add("p2","Chandrasekharpur",20.3265,85.8180,"LANDMARK");
  add("p3","Jaydev Vihar",20.3072,85.8184,"LANDMARK");
  add("p4","Acharya Vihar",20.3004,85.8300,"LANDMARK");
  add("p5","Vani Vihar",20.2960,85.8430,"LANDMARK");
  add("p6","Rasulgarh",20.2920,85.8620,"LANDMARK");
  add("p7","Saheed Nagar",20.2870,85.8440,"LANDMARK");
  add("p8","Master Canteen",20.2670,85.8430,"LANDMARK");
  add("p9","Biju Patnaik Airport",20.2530,85.8170,"LANDMARK");
  add("p10","Khandagiri",20.2570,85.7830,"LANDMARK");
  add("p11","Baramunda",20.2800,85.7980,"LANDMARK");
  add("p12","Nayapalli",20.2960,85.8070,"LANDMARK");
  add("p13","Lingaraj Temple",20.2380,85.8340,"LANDMARK");
  add("p14","Old Town",20.2470,85.8410,"LANDMARK");
  add("p15","Kalpana Square",20.2570,85.8500,"LANDMARK");
  add("f0","Patia Fuel Point",20.3420,85.8200,"PETROL");
  add("f1","Nayapalli Fuel Point",20.2900,85.8060,"PETROL");
  add("f2","Station Fuel Point",20.2720,85.8410,"PETROL");
  add("r0","Odisha Kitchen",20.3030,85.8230,"RESTAURANT");
  add("r1","Ekamra Table",20.2620,85.8470,"RESTAURANT");
  add("h0","City Garden Hotel",20.3120,85.8170,"HOTEL");
  add("h1","Heritage Stay",20.2500,85.8350,"HOTEL");
  int[][] links={{0,1},{0,2},{1,2},{2,3},{2,4},{3,4},{4,5},{5,6},{5,7},{6,7},{7,8},{8,9},{9,10},{10,11},{11,12},{12,3},{12,9},{3,11},{8,15},{15,14},{14,13},{13,9},{9,14},{7,15},{0,16},{16,2},{12,17},{17,11},{8,18},{18,7},{3,19},{19,4},{15,20},{20,8},{2,21},{21,3},{14,22},{22,9}};
  for(int i=0;i<links.length;i++){
   Place a=places.get(links[i][0]),b=places.get(links[i][1]);
   double km=haversine(a,b)*1.18; roads.add(new Road("e"+i,a.id(),b.id(),km,km/(i%4==0?32:40)*60,i%3));
  }
  for(Place p:places) adjacency.put(p.id(),new ArrayList<>());
  for(Road e:roads){adjacency.get(e.from()).add(e);adjacency.get(e.to()).add(e);}
 }
 void add(String id,String name,double lat,double lng,String cat){Place p=new Place(id,name,lat,lng,cat);places.add(p);byId.put(id,p);}
 public String other(Road e,String node){return e.from().equals(node)?e.to():e.from();}
 public static double haversine(Place a,Place b){double x=Math.toRadians(b.lat()-a.lat()),y=Math.toRadians(b.lng()-a.lng());double z=Math.pow(Math.sin(x/2),2)+Math.cos(Math.toRadians(a.lat()))*Math.cos(Math.toRadians(b.lat()))*Math.pow(Math.sin(y/2),2);return 6371*2*Math.atan2(Math.sqrt(z),Math.sqrt(1-z));}
}
