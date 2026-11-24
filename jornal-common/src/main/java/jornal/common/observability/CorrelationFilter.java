package jornal.common.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Toma {@code X-Correlation-Id} del gateway (o crea uno), lo pone en el MDC y lo devuelve en la respuesta. */
public class CorrelationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try (MDC.MDCCloseable ignored = Correlation.open(request.getHeader(Correlation.HEADER))) {
            response.setHeader(Correlation.HEADER, Correlation.current());
            chain.doFilter(request, response);
        }
    }
}
