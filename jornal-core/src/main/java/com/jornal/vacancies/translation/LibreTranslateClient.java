package com.jornal.vacancies.translation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Cliente de LibreTranslate ({@code POST /translate} con varios textos a la vez). La URL viene de
 * {@code jornal.translation.url}; vacía, la traducción queda apagada y las vacantes se ven en su idioma original.
 */
@Component
public class LibreTranslateClient implements Translator {

    private final String url;
    private final RestClient http;

    public LibreTranslateClient(@Value("${jornal.translation.url:}") String url,
                                @Value("${jornal.translation.timeout:8s}") Duration timeout) {
        this.url = url == null ? "" : url.trim();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Math.min(timeout.toMillis(), 3000));
        factory.setReadTimeout((int) timeout.toMillis());
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public boolean enabled() {
        return !url.isEmpty();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> translate(List<String> texts, String source, String target) {
        Map<String, Object> body = Map.of("q", texts, "source", source, "target", target, "format", "text");
        Map<String, Object> response = http.post().uri(url + "/translate").contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().body(Map.class);
        Object translated = response == null ? null : response.get("translatedText");
        if (!(translated instanceof List<?> list) || list.size() != texts.size()) {
            throw new IllegalStateException("Unexpected translation response");
        }
        return list.stream().map(String::valueOf).toList();
    }
}
