package com.ecuapp.inventary.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Best-effort per-instance rate limit. Production deployments should also rate-limit at the gateway
 * and use a distributed limiter when multiple API replicas share an external integration.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class ApiRateLimitFilter extends OncePerRequestFilter {
 private record Window(long startedAtMillis,int requests) {}
 private record Policy(String key,int maxRequests,long windowMillis) {}
 private final Cache<String,Window> windows=Caffeine.newBuilder().expireAfterAccess(Duration.ofMinutes(10)).maximumSize(20_000).build();

 @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
  throws ServletException,IOException {
  String path=request.getRequestURI();
  Policy policy=policy(path);
  if(policy==null){chain.doFilter(request,response);return;}
  String key=policy.key()+"|"+request.getRemoteAddr();
  long now=System.currentTimeMillis();
  AtomicBoolean limited=new AtomicBoolean(false);
  windows.asMap().compute(key,(ignored,current)->{
   if(current==null||now-current.startedAtMillis()>=policy.windowMillis())return new Window(now,1);
   if(current.requests()>=policy.maxRequests()){limited.set(true);return current;}
   return new Window(current.startedAtMillis(),current.requests()+1);
  });
  if(limited.get()){
   response.setStatus(429);response.setContentType("application/problem+json");response.setCharacterEncoding("UTF-8");
   response.setHeader("Retry-After",Long.toString(Math.max(1,policy.windowMillis()/1000)));
   response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Too Many Requests\",\"status\":429,\"detail\":\"Se excedió el límite temporal de solicitudes.\",\"instance\":\""+escape(path)+"\"}");
   return;
  }
  chain.doFilter(request,response);
 }
 private Policy policy(String path){
  if("/api/v1/auth/login".equals(path))return new Policy("login",10,60_000);
  if(path.startsWith("/api/v1/integrations/whatsapp/"))return new Policy("whatsapp",240,60_000);
  return null;
 }
 private String escape(String value){return value.replace("\\","\\\\").replace("\"","\\\"");}
}
