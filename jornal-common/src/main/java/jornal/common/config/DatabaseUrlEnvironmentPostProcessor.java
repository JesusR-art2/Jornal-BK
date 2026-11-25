package jornal.common.config;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Render (y otros proveedores) entregan la base como {@code DATABASE_URL=postgresql://usuario:clave@host:puerto/base}.
 * Si esa variable existe y no se definió {@code SPRING_DATASOURCE_URL}, se traduce a
 * {@code spring.datasource.url} (JDBC), {@code username} y {@code password}. Sin {@code DATABASE_URL} no hace nada:
 * Docker local sigue usando {@code DB_URL}/{@code DB_USER}/{@code DB_PASSWORD}.
 */
public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {

    static final String SOURCE = "jornalDatabaseUrl";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String url = environment.getProperty("DATABASE_URL");
        if (url == null || url.isBlank() || environment.getProperty("SPRING_DATASOURCE_URL") != null) return;
        Map<String, Object> props = toDatasource(url.trim());
        if (!props.isEmpty()) environment.getPropertySources().addFirst(new MapPropertySource(SOURCE, props));
    }

    /** {@code postgres(ql)://usuario:clave@host[:puerto]/base?param} → propiedades de {@code spring.datasource}. */
    static Map<String, Object> toDatasource(String url) {
        Map<String, Object> props = new HashMap<>();
        if (!url.startsWith("postgres://") && !url.startsWith("postgresql://")) return props;
        URI uri = URI.create(url);
        int port = uri.getPort() == -1 ? 5432 : uri.getPort();
        String query = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
        props.put("spring.datasource.url", "jdbc:postgresql://" + uri.getHost() + ":" + port + uri.getRawPath() + query);
        String userInfo = uri.getRawUserInfo();
        if (userInfo != null) {
            int colon = userInfo.indexOf(':');
            String user = colon < 0 ? userInfo : userInfo.substring(0, colon);
            props.put("spring.datasource.username", URLDecoder.decode(user, StandardCharsets.UTF_8));
            if (colon >= 0) {
                props.put("spring.datasource.password", URLDecoder.decode(userInfo.substring(colon + 1), StandardCharsets.UTF_8));
            }
        }
        return props;
    }
}
