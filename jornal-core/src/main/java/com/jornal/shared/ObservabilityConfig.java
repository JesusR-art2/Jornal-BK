package com.jornal.shared;

import jornal.common.observability.CorrelationFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/** Id de correlación del gateway al MDC antes que cualquier otro filtro (incluida la seguridad). */
@Configuration
public class ObservabilityConfig {

    @Bean
    FilterRegistrationBean<CorrelationFilter> correlationFilter() {
        FilterRegistrationBean<CorrelationFilter> registration = new FilterRegistrationBean<>(new CorrelationFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
