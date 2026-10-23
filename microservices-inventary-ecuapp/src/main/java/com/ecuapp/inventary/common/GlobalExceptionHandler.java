package com.ecuapp.inventary.common;
import com.ecuapp.inventary.domain.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
@RestControllerAdvice
public class GlobalExceptionHandler {
 private static final Logger log=LoggerFactory.getLogger(GlobalExceptionHandler.class);
 private final ApiProblemFactory problems;
 public GlobalExceptionHandler(ApiProblemFactory problems){this.problems=problems;}
 @ExceptionHandler(ResourceNotFoundException.class)
 ResponseEntity<ProblemDetail> notFound(ResourceNotFoundException ex,HttpServletRequest req){return response(HttpStatus.NOT_FOUND,"Recurso no encontrado",ex.getMessage(),req);}
 @ExceptionHandler(DomainException.class)
 ResponseEntity<ProblemDetail> domain(DomainException ex,HttpServletRequest req){
  HttpStatus status=switch(ex.code()){
   case RESOURCE_NOT_FOUND->HttpStatus.NOT_FOUND;
   case DUPLICATE_BUSINESS_KEY,CONCURRENT_MODIFICATION->HttpStatus.CONFLICT;
   case INSUFFICIENT_STOCK->HttpStatus.UNPROCESSABLE_ENTITY;
   case INVALID_STATE_TRANSITION,INVALID_QUANTITY,IMMUTABLE_POSTED_DOCUMENT->HttpStatus.UNPROCESSABLE_ENTITY;};
  ProblemDetail body=problems.create(status,"Regla de dominio no satisfecha",ex.getMessage(),req);
  body.setProperty("code",ex.code().name());return ResponseEntity.status(status).body(body);
 }
 @ExceptionHandler({BusinessRuleException.class,IllegalArgumentException.class})
 ResponseEntity<ProblemDetail> badRequest(RuntimeException ex,HttpServletRequest req){return response(HttpStatus.UNPROCESSABLE_ENTITY,"Regla de negocio no satisfecha",safeBusinessMessage(ex),req);}
 @ExceptionHandler(ConflictException.class)
 ResponseEntity<ProblemDetail> conflict(ConflictException ex,HttpServletRequest req){return response(HttpStatus.CONFLICT,"Conflicto",ex.getMessage(),req);}
 @ExceptionHandler(DataIntegrityViolationException.class)
 ResponseEntity<ProblemDetail> integrity(DataIntegrityViolationException ex,HttpServletRequest req){return response(HttpStatus.CONFLICT,"Conflicto de datos","La operación infringe una restricción de unicidad o integridad.",req);}
 @ExceptionHandler(MethodArgumentNotValidException.class)
 ResponseEntity<ProblemDetail> validation(MethodArgumentNotValidException ex,HttpServletRequest req){
  ProblemDetail body=problems.create(HttpStatus.BAD_REQUEST,"Solicitud inválida","Uno o más campos no cumplen las validaciones.",req);
  Map<String,String> errors=ex.getBindingResult().getFieldErrors().stream().collect(Collectors.toMap(
   error->error.getField(),error->error.getDefaultMessage()==null?"Valor inválido":error.getDefaultMessage(),
   (first,ignored)->first,LinkedHashMap::new));
  body.setProperty("errors",errors);return ResponseEntity.badRequest().body(body);
 }
 @ExceptionHandler(HttpMessageNotReadableException.class)
 ResponseEntity<ProblemDetail> unreadable(HttpMessageNotReadableException ex,HttpServletRequest req){return response(HttpStatus.BAD_REQUEST,"JSON inválido","El cuerpo de la solicitud tiene un formato o valor no válido.",req);}
 @ExceptionHandler(BadCredentialsException.class)
 ResponseEntity<ProblemDetail> credentials(BadCredentialsException ex,HttpServletRequest req){return response(HttpStatus.UNAUTHORIZED,"No autorizado","Usuario o contraseña incorrectos.",req);}
 @ExceptionHandler(Exception.class)
 ResponseEntity<ProblemDetail> unexpected(Exception ex,HttpServletRequest req){
  Object requestId=req.getAttribute(com.ecuapp.inventary.config.RequestCorrelationFilter.ATTRIBUTE);
  log.error("Unhandled API exception requestId={} method={} path={}",requestId,req.getMethod(),req.getRequestURI(),ex);
  return response(HttpStatus.INTERNAL_SERVER_ERROR,"Error interno","Ocurrió un error inesperado. Consulte el identificador de solicitud con soporte.",req);
 }
 private String safeBusinessMessage(RuntimeException ex){
  String message=ex.getMessage();
  if(message==null||message.isBlank())return "La solicitud incumple una regla de negocio.";
  String normalized=message.toLowerCase(java.util.Locale.ROOT);
  if(normalized.contains("sql")||normalized.contains("jdbc")||normalized.contains("password")||normalized.contains("secret")||normalized.contains("token")||normalized.contains("constraint")||normalized.contains("postgres"))
   return "La solicitud incumple una regla de negocio.";
  return message;
 }
 private ResponseEntity<ProblemDetail> response(HttpStatus status,String title,String detail,HttpServletRequest request){
  return ResponseEntity.status(status).body(problems.create(status,title,detail,request));
 }
}
