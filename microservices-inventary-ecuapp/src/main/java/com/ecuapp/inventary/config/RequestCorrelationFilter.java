package com.ecuapp.inventary.config;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
@Component
@Order(Ordered.HIGHEST_PRECEDENCE+10)
public class RequestCorrelationFilter extends OncePerRequestFilter {
 public static final String ATTRIBUTE="inventary.requestId";
 public static final String MDC_KEY="requestId";
 private static final Pattern SAFE=Pattern.compile("[A-Za-z0-9._:-]{1,80}");
 @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
  String inbound=request.getHeader("X-Request-ID");
  String requestId=inbound!=null&&SAFE.matcher(inbound).matches()?inbound:UUID.randomUUID().toString();
  request.setAttribute(ATTRIBUTE,requestId);response.setHeader("X-Request-ID",requestId);MDC.put(MDC_KEY,requestId);
  try{chain.doFilter(request,response);}finally{MDC.remove(MDC_KEY);}
 }
}
