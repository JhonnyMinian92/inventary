package com.ecuapp.inventary.config;
import com.ecuapp.inventary.audit.AuditService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Map;
/** Audits failed API responses without reading request bodies, query values or sensitive headers. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE+30)
public class ApiRequestAuditFilter extends OncePerRequestFilter {
 private static final Logger log=LoggerFactory.getLogger(ApiRequestAuditFilter.class);
 private final AuditService auditService;
 public ApiRequestAuditFilter(AuditService auditService){this.auditService=auditService;}
 @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
  try{chain.doFilter(request,response);}finally{
   int status=response.getStatus();String path=request.getRequestURI();
   if(path!=null&&path.startsWith("/api/")&&status>=400){
    try{
     Authentication auth=SecurityContextHolder.getContext().getAuthentication();
     String actor=auth!=null&&auth.isAuthenticated()&&!"anonymousUser".equals(auth.getName())?auth.getName():"anonymous";
     String outcome=status==401||status==403?"DENIED":"FAILURE";
     Object correlation=request.getAttribute(RequestCorrelationFilter.ATTRIBUTE);
     String requestId=correlation instanceof String value?value:null;
     auditService.recordOutcome(actor,"HTTP_REQUEST_FAILED","HTTP_REQUEST",null,outcome,requestId,
      Map.of("method",safeMethod(request.getMethod()),"path",safePath(path),"status",status));
    }catch(RuntimeException ignored){log.warn("Could not persist request failure audit event; requestId={}",request.getAttribute(RequestCorrelationFilter.ATTRIBUTE));}
   }
  }
 }
 private String safeMethod(String method){return method==null?"UNKNOWN":method.substring(0,Math.min(method.length(),12));}
 private String safePath(String path){return path==null?"/":path.substring(0,Math.min(path.length(),500));}
}
