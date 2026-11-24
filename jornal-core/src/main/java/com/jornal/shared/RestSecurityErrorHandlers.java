package com.jornal.shared;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.MessageSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 401 y 403 en JSON. Corren en los filtros de seguridad, antes del DispatcherServlet,
 * así que el idioma se toma directamente del header Accept-Language.
 */
@Component
public class RestSecurityErrorHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final MessageSource messages;

    public RestSecurityErrorHandlers(MessageSource messages) {
        this.messages = messages;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException ex) throws IOException {
        write(request, response, 401, "Unauthorized", "UNAUTHORIZED",
                "Authentication is required or the token is invalid");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException ex) throws IOException {
        write(request, response, 403, "Forbidden", "ACCESS_DENIED",
                "You do not have permission to perform this action");
    }

    private void write(HttpServletRequest request, HttpServletResponse response, int status,
                       String title, String code, String fallback) throws IOException {
        String locale = Locales.resolve(request.getHeader("Accept-Language")).orElse(Locales.FALLBACK);
        String detail = messages.getMessage("error." + code, null, fallback, Locales.toLocale(locale))
                .replace("\\", "\\\\").replace("\"", "\\\"");
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("""
                {"type":"about:blank","title":"%s","status":%d,"detail":"%s","code":"%s"}
                """.formatted(title, status, detail, code));
    }
}
