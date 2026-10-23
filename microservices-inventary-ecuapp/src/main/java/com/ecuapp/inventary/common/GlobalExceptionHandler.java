package com.ecuapp.inventary.common;
import com.ecuapp.inventary.domain.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
@RestControllerAdvice
public class GlobalExceptionHandler {
 private static final Logger log=LoggerFactory.getLogger(GlobalExceptionHandler.class);
 private final ApiProblemFactory problems;
 public GlobalExceptionHandler(ApiProblemFactory problems){this.problems=problems;}
 @ExceptionHandler(ResourceNotFoundException.class)
 ResponseEntity<ProblemDetail> notFound(ResourceNotFoundException ex,HttpServletRequest req){return response(HttpStatus.NOT_FOUND,"Recurso no encontrado",ex.getMessage(),"RESOURCE_NOT_FOUND",req);}
 @ExceptionHandler(DomainException.class)
 ResponseEntity<ProblemDetail> domain(DomainException ex,HttpServletRequest req){
  HttpStatus status=switch(ex.code()){
   case RESOURCE_NOT_FOUND->HttpStatus.NOT_FOUND;
   case DUPLICATE_BUSINESS_KEY,CONCURRENT_MODIFICATION->HttpStatus.CONFLICT;
   case INSUFFICIENT_STOCK->HttpStatus.UNPROCESSABLE_ENTITY;
   case INVALID_STATE_TRANSITION,INVALID_QUANTITY,IMMUTABLE_POSTED_DOCUMENT->HttpStatus.UNPROCESSABLE_ENTITY;};
  return response(status,"Regla de dominio no satisfecha",ex.getMessage(),ex.code().name(),req);
 }
 @ExceptionHandler(InvalidQueryException.class)
 ResponseEntity<ProblemDetail> invalidQuery(InvalidQueryException ex,HttpServletRequest req){return response(HttpStatus.BAD_REQUEST,"Parámetros inválidos",ex.getMessage(),"INVALID_QUERY",req);}
 @ExceptionHandler(PropertyReferenceException.class)
 ResponseEntity<ProblemDetail> invalidProperty(PropertyReferenceException ex,HttpServletRequest req){return response(HttpStatus.BAD_REQUEST,"Parámetros inválidos","Propiedad de ordenamiento o filtro no permitida.","INVALID_QUERY",req);}
 @ExceptionHandler({MissingServletRequestParameterException.class,MethodArgumentTypeMismatchException.class})
 ResponseEntity<ProblemDetail> invalidParameter(Exception ex,HttpServletRequest req){return response(HttpStatus.BAD_REQUEST,"Parámetro inválido","Falta un parámetro obligatorio o uno de los parámetros tiene un formato no válido.","INVALID_PARAMETER",req);}
 @ExceptionHandler(MissingRequestHeaderException.class)
 ResponseEntity<ProblemDetail> missingHeader(MissingRequestHeaderException ex,HttpServletRequest req){
  String header=ex.getHeaderName()==null?"requerido":ex.getHeaderName().replaceAll("[^A-Za-z0-9-]","");
  return response(HttpStatus.BAD_REQUEST,"Header obligatorio ausente","Falta el header obligatorio "+header+".","MISSING_HEADER",req);
 }
 @ExceptionHandler({ConstraintViolationException.class,HandlerMethodValidationException.class})
 ResponseEntity<ProblemDetail> constraint(Exception ex,HttpServletRequest req){return response(HttpStatus.BAD_REQUEST,"Parámetros inválidos","Uno o más parámetros no cumplen las validaciones.","VALIDATION_FAILED",req);}
 @ExceptionHandler({BusinessRuleException.class,IllegalArgumentException.class})
 ResponseEntity<ProblemDetail> badRequest(RuntimeException ex,HttpServletRequest req){return response(HttpStatus.UNPROCESSABLE_ENTITY,"Regla de negocio no satisfecha",safeBusinessMessage(ex),"BUSINESS_RULE_VIOLATION",req);}
 @ExceptionHandler(IllegalStateException.class)
 ResponseEntity<ProblemDetail> illegalState(IllegalStateException ex,HttpServletRequest req){
  log.warn("Rejected state transition requestId={} path={}: {}",requestId(req),req.getRequestURI(),ex.getMessage());
  return response(HttpStatus.CONFLICT,"Estado incompatible",safeBusinessMessage(ex),"INVALID_STATE_TRANSITION",req);
 }
 @ExceptionHandler(ConflictException.class)
 ResponseEntity<ProblemDetail> conflict(ConflictException ex,HttpServletRequest req){return response(HttpStatus.CONFLICT,"Conflicto",ex.getMessage(),"CONFLICT",req);}
 @ExceptionHandler({OptimisticLockingFailureException.class,PessimisticLockingFailureException.class,CannotAcquireLockException.class})
 ResponseEntity<ProblemDetail> concurrency(Exception ex,HttpServletRequest req){return response(HttpStatus.CONFLICT,"Modificación concurrente","El recurso fue modificado por otra operación; reintente la solicitud.","CONCURRENT_MODIFICATION",req);}
 @ExceptionHandler(DataIntegrityViolationException.class)
 ResponseEntity<ProblemDetail> integrity(DataIntegrityViolationException ex,HttpServletRequest req){
  log.warn("Data integrity violation requestId={} path={}",requestId(req),req.getRequestURI());
  return response(HttpStatus.CONFLICT,"Conflicto de datos","La operación infringe una restricción de unicidad o integridad.","DATA_INTEGRITY_VIOLATION",req);
 }
 @ExceptionHandler(MethodArgumentNotValidException.class)
 ResponseEntity<ProblemDetail> validation(MethodArgumentNotValidException ex,HttpServletRequest req){
  ProblemDetail body=problems.create(HttpStatus.BAD_REQUEST,"Solicitud inválida","Uno o más campos no cumplen las validaciones.",req);
  body.setProperty("code","VALIDATION_FAILED");
  Map<String,String> errors=ex.getBindingResult().getFieldErrors().stream().collect(Collectors.toMap(
   error->error.getField(),error->error.getDefaultMessage()==null?"Valor inválido":error.getDefaultMessage(),
   (first,ignored)->first,LinkedHashMap::new));
  body.setProperty("errors",errors);return ResponseEntity.badRequest().body(body);
 }
 @ExceptionHandler(HttpMessageNotReadableException.class)
 ResponseEntity<ProblemDetail> unreadable(HttpMessageNotReadableException ex,HttpServletRequest req){return response(HttpStatus.BAD_REQUEST,"JSON inválido","El cuerpo de la solicitud tiene un formato o valor no válido.","MALFORMED_JSON",req);}
 @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
 ResponseEntity<ProblemDetail> method(HttpRequestMethodNotSupportedException ex,HttpServletRequest req){return response(HttpStatus.METHOD_NOT_ALLOWED,"Método no permitido","El método HTTP no está soportado por este recurso.","METHOD_NOT_ALLOWED",req);}
 @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
 ResponseEntity<ProblemDetail> mediaType(HttpMediaTypeNotSupportedException ex,HttpServletRequest req){return response(HttpStatus.UNSUPPORTED_MEDIA_TYPE,"Tipo de contenido no soportado","Use Content-Type: application/json.","UNSUPPORTED_MEDIA_TYPE",req);}
 @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
 ResponseEntity<ProblemDetail> acceptable(HttpMediaTypeNotAcceptableException ex,HttpServletRequest req){return response(HttpStatus.NOT_ACCEPTABLE,"Representación no disponible","El recurso solo produce application/json.","NOT_ACCEPTABLE",req);}
 @ExceptionHandler(NoResourceFoundException.class)
 ResponseEntity<ProblemDetail> noRoute(NoResourceFoundException ex,HttpServletRequest req){return response(HttpStatus.NOT_FOUND,"Ruta no encontrada","La ruta solicitada no existe.","ROUTE_NOT_FOUND",req);}
 @ExceptionHandler(AccessDeniedException.class)
 ResponseEntity<ProblemDetail> denied(AccessDeniedException ex,HttpServletRequest req){return response(HttpStatus.FORBIDDEN,"Acceso denegado","La sesión no tiene permiso para realizar esta operación.","ACCESS_DENIED",req);}
 @ExceptionHandler(BadCredentialsException.class)
 ResponseEntity<ProblemDetail> credentials(BadCredentialsException ex,HttpServletRequest req){return response(HttpStatus.UNAUTHORIZED,"No autorizado","Credenciales inválidas.","INVALID_CREDENTIALS",req);}
 @ExceptionHandler(AuthenticationException.class)
 ResponseEntity<ProblemDetail> authentication(AuthenticationException ex,HttpServletRequest req){return response(HttpStatus.UNAUTHORIZED,"No autenticado","Se requiere una sesión válida para acceder a este recurso.","AUTHENTICATION_REQUIRED",req);}
 @ExceptionHandler(ResponseStatusException.class)
 ResponseEntity<ProblemDetail> status(ResponseStatusException ex,HttpServletRequest req){
  HttpStatusCode code=ex.getStatusCode();HttpStatus status=HttpStatus.resolve(code.value());if(status==null)status=HttpStatus.BAD_REQUEST;
  return response(status,status.getReasonPhrase(),ex.getReason()==null?status.getReasonPhrase():safeBusinessMessage(new RuntimeException(ex.getReason())),"HTTP_"+status.value(),req);
 }
 @ExceptionHandler(Exception.class)
 ResponseEntity<ProblemDetail> unexpected(Exception ex,HttpServletRequest req){
  log.error("Unhandled API exception requestId={} method={} path={}",requestId(req),req.getMethod(),req.getRequestURI(),ex);
  return response(HttpStatus.INTERNAL_SERVER_ERROR,"Error interno","Ocurrió un error inesperado. Consulte el identificador de solicitud con soporte.","INTERNAL_ERROR",req);
 }
 private Object requestId(HttpServletRequest req){return req.getAttribute(com.ecuapp.inventary.config.RequestCorrelationFilter.ATTRIBUTE);}
 private String safeBusinessMessage(RuntimeException ex){
  String message=ex.getMessage();
  if(message==null||message.isBlank())return "La solicitud incumple una regla de negocio.";
  String normalized=message.toLowerCase(Locale.ROOT);
  if(normalized.contains("sql")||normalized.contains("jdbc")||normalized.contains("password")||normalized.contains("secret")||normalized.contains("token")||normalized.contains("constraint")||normalized.contains("postgres")||normalized.contains("exception")||normalized.contains("hibernate"))
   return "La solicitud incumple una regla de negocio.";
  return message;
 }
 private ResponseEntity<ProblemDetail> response(HttpStatus status,String title,String detail,String code,HttpServletRequest request){
  ProblemDetail body=problems.create(status,title,detail,request);body.setProperty("code",code);
  return ResponseEntity.status(status).body(body);
 }
}
