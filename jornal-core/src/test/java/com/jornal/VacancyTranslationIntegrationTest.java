package com.jornal;

import com.jornal.vacancies.translation.Translator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El contenido de la vacante (título, qué hay que hacer e implementos) se muestra en el idioma de quien la ve:
 * se traduce una sola vez por idioma y se guarda. En el idioma original no se traduce.
 */
@Import({TestcontainersConfiguration.class, VacancyTranslationIntegrationTest.FakeTranslation.class})
@SpringBootTest(properties = {
        "jornal.region.code=CO",
        "jornal.admin.email=admin.co@example.test",
        "jornal.admin.password=AdminPass123"})
@AutoConfigureMockMvc
class VacancyTranslationIntegrationTest {

    static final AtomicInteger CALLS = new AtomicInteger();

    @TestConfiguration
    static class FakeTranslation {
        /** «[en] texto»: deja ver a qué idioma se tradujo y cuántas veces se llamó. */
        @Bean
        @Primary
        Translator fakeTranslator() {
            return new Translator() {
                @Override
                public List<String> translate(List<String> texts, String source, String target) {
                    CALLS.incrementAndGet();
                    return texts.stream().map(t -> "[" + source + ">" + target + "] " + t).toList();
                }

                @Override
                public boolean enabled() {
                    return true;
                }
            };
        }
    }

    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;

    @Test
    void contentFollowsTheReadersLanguage() throws Exception {
        String employer = token("APPLICANT");
        String day = LocalDate.now(ZoneId.of("America/Bogota")).plusDays(3).toString();
        String body = """
                {"title":"Auxiliar de bodega","description":"Cargar el camión","items":["Guantes"],
                 "startLocal":"%sT08:00","endLocal":"%sT16:00","positions":1,"payMinor":100000,
                 "address":{"street_address":"Calle 26 # 68-35","city":"Bogotá","department":"Cundinamarca"},
                 "latitude":4.65,"longitude":-74.08}
                """.formatted(day, day);
        String id = json.readTree(mvc.perform(post("/vacancies").header("Authorization", employer)
                        .header("Accept-Language", "es-CO").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Auxiliar de bodega"))
                .andExpect(jsonPath("$.sourceLanguage").value("es"))
                .andExpect(jsonPath("$.translated").value(false))
                .andReturn().getResponse().getContentAsString()).get("id").asString();

        int before = CALLS.get();
        String worker = token("WORKER");
        for (int i = 0; i < 2; i++) {   // la segunda vez sale de la tabla, sin volver a traducir
            mvc.perform(get("/vacancies/{id}", id).header("Authorization", worker).header("Accept-Language", "en-US"))
                    .andExpect(jsonPath("$.title").value("[es>en] Auxiliar de bodega"))
                    .andExpect(jsonPath("$.description").value("[es>en] Cargar el camión"))
                    .andExpect(jsonPath("$.items[0]").value("[es>en] Guantes"))
                    .andExpect(jsonPath("$.translated").value(true));
        }
        assertThat(CALLS.get() - before).isEqualTo(1);

        mvc.perform(get("/vacancies/{id}", id).header("Authorization", worker).header("Accept-Language", "ja-JP"))
                .andExpect(jsonPath("$.title").value("[es>ja] Auxiliar de bodega"));
        mvc.perform(get("/vacancies/{id}", id).header("Authorization", worker).header("Accept-Language", "es-CO"))
                .andExpect(jsonPath("$.title").value("Auxiliar de bodega"))
                .andExpect(jsonPath("$.translated").value(false));
    }

    private String token(String rol) throws Exception {
        String email = "tr-" + UUID.randomUUID() + "@example.test";
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"%s","password":"Clave12345","givenName":"Ana","familyName":"Prueba",
                         "rol":"%s","acceptedTerms":true}
                        """.formatted(email, rol)))
                .andExpect(status().isCreated());
        return "Bearer " + json.readTree(mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"Clave12345\"}".formatted(email)))
                .andReturn().getResponse().getContentAsString()).get("accessToken").asString();
    }
}
