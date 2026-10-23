package com.ecuapp.inventary.config;

import com.ecuapp.inventary.common.ApiProblemFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** Produces safe RFC 9457 problem responses for errors raised before MVC dispatch. */
@Component
public class ApiSecurityProblemHandler implements AuthenticationEntryPoint, AccessDeniedHandler {
    private final ApiProblemFactory problems;
    private final ObjectMapper mapper;

    public ApiSecurityProblemHandler(ApiProblemFactory problems, ObjectMapper mapper) {
        this.problems = problems;
        this.mapper = mapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException exception) throws IOException {
        write(request, response, HttpStatus.UNAUTHORIZED, "No autenticado",
                "Se requiere una sesión válida para acceder a este recurso.", "AUTHENTICATION_REQUIRED");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException exception) throws IOException {
        write(request, response, HttpStatus.FORBIDDEN, "Acceso denegado",
                "La sesión no tiene permiso para realizar esta operación.", "ACCESS_DENIED");
    }

    private void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status,
                       String title, String detail, String code) throws IOException {
        if (response.isCommitted()) return;
        var body = problems.create(status, title, detail, request);
        body.setProperty("code", code);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        mapper.writeValue(response.getOutputStream(), body);
    }
}
