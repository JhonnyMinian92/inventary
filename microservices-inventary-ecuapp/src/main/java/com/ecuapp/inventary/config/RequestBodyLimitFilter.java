package com.ecuapp.inventary.config;

import com.ecuapp.inventary.common.ApiProblemFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Rejects oversized request bodies (declared Content-Length) before they are parsed. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 15)
public class RequestBodyLimitFilter extends OncePerRequestFilter {
    private final long maxBytes;
    private final ApiProblemFactory problems;
    private final ObjectMapper mapper;

    public RequestBodyLimitFilter(@Value("${app.security.max-request-body-bytes:1048576}") long maxBytes,
                                  ApiProblemFactory problems, ObjectMapper mapper) {
        this.maxBytes = maxBytes;
        this.problems = problems;
        this.mapper = mapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        if (request.getContentLengthLong() > maxBytes) {
            response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            var body = problems.create(HttpStatus.PAYLOAD_TOO_LARGE, "Solicitud demasiado grande",
                "El cuerpo de la solicitud supera el tamaño máximo permitido.", request);
            body.setProperty("code", "PAYLOAD_TOO_LARGE");
            mapper.writeValue(response.getOutputStream(), body);
            return;
        }
        chain.doFilter(request, response);
    }
}
