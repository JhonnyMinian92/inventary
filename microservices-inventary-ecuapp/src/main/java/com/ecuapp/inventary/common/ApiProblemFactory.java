package com.ecuapp.inventary.common;

import com.ecuapp.inventary.config.RequestCorrelationFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

import java.net.URI;

/** Builds the common RFC 9457 problem response used by all REST exception handlers. */
@Component
public class ApiProblemFactory {
    public ProblemDetail create(HttpStatus status, String title, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("https://inventary.local/problems/" + status.value()));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", "HTTP_" + status.value());
        Object requestId = request.getAttribute(RequestCorrelationFilter.ATTRIBUTE);
        if (requestId instanceof String value && !value.isBlank()) {
            problem.setProperty("requestId", value);
        }
        return problem;
    }
}
