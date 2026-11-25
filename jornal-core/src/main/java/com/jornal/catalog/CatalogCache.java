package com.jornal.catalog;

import com.jornal.catalog.dto.CategoryView;
import com.jornal.region.RegionApi;
import com.jornal.region.RegionProfileUpdated;
import org.springframework.context.event.EventListener;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Caché del catálogo público ({@code GET /categories}) en Redis, con TTL. La clave lleva la región, el idioma
 * pedido, el texto buscado y una «generación» del catálogo: cualquier cambio del administrador (categoría,
 * traducción o precio) sube la generación al confirmar la transacción y las entradas viejas dejan de usarse
 * (vencen solas por el TTL). Si Redis no responde, se lee de la base como siempre.
 */
@Component
public class CatalogCache {

    private static final Logger log = LoggerFactory.getLogger(CatalogCache.class);
    private static final TypeReference<List<CategoryView>> VIEWS = new TypeReference<>() { };

    private final StringRedisTemplate redis;
    private final JsonMapper json;
    private final Duration ttl;
    private final String prefix;
    private final MeterRegistry metrics;

    public CatalogCache(StringRedisTemplate redis, JsonMapper json, RegionApi regions,
                        @Value("${jornal.catalog.cache-ttl:10m}") Duration ttl,
                        ObjectProvider<MeterRegistry> metrics) {
        this.redis = redis;
        this.json = json;
        this.ttl = ttl;
        this.prefix = "jornal:" + regions.currentCode().toLowerCase(Locale.ROOT) + ":catalog:";
        this.metrics = metrics.getIfAvailable();
    }

    /** Devuelve la lista guardada o la calcula con {@code loader} y la guarda. */
    public List<CategoryView> active(String locale, String query, Supplier<List<CategoryView>> loader) {
        if (ttl.isZero()) return loader.get();
        String key = null;
        try {
            key = prefix + "gen" + generation() + ":" + locale + ":" + normalize(query);
            String cached = redis.opsForValue().get(key);
            if (cached != null) {
                count("hit");
                return json.readValue(cached, VIEWS);
            }
        } catch (RuntimeException e) {
            log.warn("Catalog cache unavailable, reading from the database: {}", e.getMessage());
            count("error");
            return loader.get();
        }
        count("miss");
        List<CategoryView> views = loader.get();
        try {
            redis.opsForValue().set(key, json.writeValueAsString(views), ttl);
        } catch (RuntimeException e) {
            log.warn("Catalog cache not updated: {}", e.getMessage());
        }
        return views;
    }

    /** Un cambio del perfil regional (por ejemplo, los decimales de la moneda) también cambia la vista del catálogo. */
    @EventListener
    void on(RegionProfileUpdated updated) {
        invalidate();
    }

    /** Invalida todo el catálogo de la región cuando confirma la transacción que lo cambió. */
    public void invalidate() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    bump();
                }
            });
        } else {
            bump();
        }
    }

    private void bump() {
        try {
            redis.opsForValue().increment(prefix + "generation");
        } catch (RuntimeException e) {
            log.warn("Catalog cache not invalidated (entries expire in {}): {}", ttl, e.getMessage());
        }
    }

    private String generation() {
        String value = redis.opsForValue().get(prefix + "generation");
        return value == null ? "0" : value;
    }

    private static String normalize(String query) {
        return query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
    }

    private void count(String result) {
        if (metrics != null) metrics.counter("jornal.catalog.cache", "result", result).increment();
    }
}
