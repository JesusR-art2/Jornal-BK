package com.jornal.region.web;

import com.jornal.region.RegionApi;
import com.jornal.shared.Locales;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.LocaleResolver;

import java.util.Locale;

/**
 * Idioma de cada petición: el primero soportado de Accept-Language; si no hay, el idioma por defecto
 * del perfil regional; y como último respaldo, en-US.
 */
@Component(DispatcherServlet.LOCALE_RESOLVER_BEAN_NAME)
public class RegionLocaleResolver implements LocaleResolver {

    private final RegionApi regions;

    public RegionLocaleResolver(RegionApi regions) {
        this.regions = regions;
    }

    @Override
    public Locale resolveLocale(HttpServletRequest request) {
        String tag = Locales.resolve(request.getHeader("Accept-Language"))
                .orElseGet(this::profileDefault);
        return Locales.toLocale(tag);
    }

    @Override
    public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
        throw new UnsupportedOperationException("The locale comes from the Accept-Language header");
    }

    private String profileDefault() {
        try {
            return Locales.normalize(regions.current().defaultLocale(), Locales.FALLBACK);
        } catch (RuntimeException e) {
            return Locales.FALLBACK;
        }
    }
}
