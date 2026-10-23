package com.ecuapp.inventary.config;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Duration;
@Configuration
@EnableCaching
public class CacheConfig {
 @Bean
 CacheManager cacheManager(){
  CaffeineCacheManager manager=new CaffeineCacheManager("publicProductList");
  manager.setCaffeine(Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(60)).maximumSize(1000));
  return manager;
 }
}
