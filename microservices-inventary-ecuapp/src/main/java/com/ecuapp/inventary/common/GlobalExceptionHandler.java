package com.ecuapp.inventary.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ProblemDetail> notFound(ResourceNotFoundException ex, HttpServletRequest req) {
        return problem(HttpStatus.NOT_FOUND, "Recurso no encontrado", ex.getMessage(), req);
    }
    @ExceptionHandler({BusinessRuleException.class, IllegalArgumentException.class})
    ResponseEntity<ProblemDetail> badRequest(RuntimeException ex, HttpServletRequest req) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Regla de negocio no satisfecha", ex.getMessage(), req);
    }
    @ExceptionHandler(ConflictException.class)
    ResponseEntity<ProblemDetail> conflict(ConflictException ex, HttpServletRequest req) {
        return problem(HttpStatus.CONFLICT, "Conflicto", ex.getMessage(), req);
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> integrity(DataIntegrityViolationException ex, HttpServletRequest req) {
        return problem(HttpStatus.CONFLICT, "Conflicto de datos", "La operación infringe una restricción de unicidad o integridad.", req);
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> validation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Uno o más campos no cumplen las validaciones.");
        body.setTitle("Solicitud inválida");
        body.setType(URI.create("https://inventary.local/problems/validation"));
        body.setInstance(URI.create(req.getRequestURI()));
        Map<String,String> errors = ex.getBindingResult().getFieldErrors().stream()
            .collect(Collectors.toMap(e -> e.getField(), e -> e.getDefaultMessage() == null ? "Valor inválido" : e.getDefaultMessage(),
                (a,b) -> a, LinkedHashMap::new));
        body.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(body);
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> unreadable(HttpMessageNotReadableException ex, HttpServletRequest req) {
        return problem(HttpStatus.BAD_REQUEST, "JSON inválido", "El cuerpo de la solicitud tiene un formato o valor no válido.", req);
    }
    @ExceptionHandler(BadCredentialsException.class)
    ResponseEntity<ProblemDetail> credentials(BadCredentialsException ex, HttpServletRequest req) {
        return problem(HttpStatus.UNAUTHORIZED, "No autorizado", "Usuario o contraseña incorrectos.", req);
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception ex, HttpServletRequest req) {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno", "Ocurrió un error inesperado. Consulte el identificador de solicitud con soporte.", req);
    }
    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String title, String detail, HttpServletRequest req) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setTitle(title);
        body.setType(URI.create("https://inventary.local/problems/" + status.value()));
        body.setInstance(URI.create(req.getRequestURI()));
        body.setProperty("code", "HTTP_" + status.value());
        return ResponseEntity.status(status).body(body);
    }
}
