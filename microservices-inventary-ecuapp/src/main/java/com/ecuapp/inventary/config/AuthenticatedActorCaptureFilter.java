package com.ecuapp.inventary.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

/** Captures the authenticated actor before authorization completes and the security context is cleared. */
public class AuthenticatedActorCaptureFilter extends OncePerRequestFilter {
    public static final String ATTRIBUTE = "inventary.auditActor";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
            && !"anonymousUser".equals(authentication.getName())) {
            request.setAttribute(ATTRIBUTE, authentication.getName());
        }
        chain.doFilter(request, response);
    }
}
