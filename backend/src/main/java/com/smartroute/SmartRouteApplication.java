package com.smartroute;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
@SpringBootApplication @EnableCaching
public class SmartRouteApplication {
 public static void main(String[] args) { SpringApplication.run(SmartRouteApplication.class,args); }
}
