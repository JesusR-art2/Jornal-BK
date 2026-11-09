package com.jornal.Shared;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class RestSecurityErrorHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {


    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException ex) throws IOException {
        write(response, 401, "Unauthorized",
                "Authentication is required or the token is invalid", "UNAUTHORIZED");
    }
    
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException ex) throws IOException {
        write(response, 403, "Forbidden",
                "You do not have permission to perform this action", "ACCESS_DENIED");
    }

    private void write(HttpServletResponse response, int status, String title,
                       String detail, String code) throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("""
                {"type":"about:blank","title":"%s","status":%d,"detail":"%s","code":"%s"}
                """.formatted(title, status, detail, code));
    }
}
