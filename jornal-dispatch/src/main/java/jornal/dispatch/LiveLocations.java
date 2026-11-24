package jornal.dispatch;

import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Posición en vivo de los trabajadores en Redis. Por región: un índice GEO ({@code geo:live:<región>}) y una
 * clave por trabajador con TTL ({@code live:<región>:<trabajador>}); si la clave venció, la posición no cuenta
 * y se usa la ubicación base del perfil.
 */
@Component
public class LiveLocations {

    public record Position(double latitude, double longitude, Instant at) {
    }

    private final StringRedisTemplate redis;
    private final DispatchProperties properties;

    public LiveLocations(StringRedisTemplate redis, DispatchProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    public Position update(String region, UUID workerId, double latitude, double longitude) {
        Instant now = Instant.now();
        redis.opsForGeo().add("geo:live:" + region, new Point(longitude, latitude), workerId.toString());
        redis.opsForValue().set(key(region, workerId), latitude + "," + longitude + "," + now.toEpochMilli(),
                properties.liveTtl());
        return new Position(latitude, longitude, now);
    }

    public Optional<Position> position(String region, UUID workerId) {
        String value = redis.opsForValue().get(key(region, workerId));
        if (value == null) return Optional.empty();
        String[] parts = value.split(",");
        return Optional.of(new Position(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]),
                Instant.ofEpochMilli(Long.parseLong(parts[2]))));
    }

    private static String key(String region, UUID workerId) {
        return "live:" + region + ":" + workerId;
    }

    /** Distancia en km sobre la esfera (haversine). */
    public static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 6371.0088 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
