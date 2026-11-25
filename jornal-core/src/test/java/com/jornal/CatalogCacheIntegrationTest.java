package com.jornal;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** P2: catálogo público en la caché de Redis, por región e idioma, invalidado por los cambios del administrador. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
        "jornal.region.code=CO",
        "jornal.admin.email=admin.cache@example.test",
        "jornal.admin.password=AdminPass123"})
@AutoConfigureMockMvc
class CatalogCacheIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired StringRedisTemplate redis;
    @Autowired MeterRegistry metrics;

    @Test
    void servesFromRedisAndInvalidatesOnAdminChanges() throws Exception {
        String admin = login("admin.cache@example.test", "AdminPass123");
        String client = clientToken();

        // 1. Primera consulta: se lee de la base y se guarda en Redis
        double hitsBefore = counter("hit");
        mvc.perform(get("/categories").param("q", "warehouse").header("Authorization", client)
                        .header("Accept-Language", "es-CO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].price.amountMinor").value(85000));
        assertThat(redis.keys("jornal:co:catalog:gen*:es-CO:warehouse")).isNotEmpty();

        // 2. Segunda consulta igual: sale de la caché
        mvc.perform(get("/categories").param("q", "warehouse").header("Authorization", client)
                        .header("Accept-Language", "es-CO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Bodega e inventario"));
        assertThat(counter("hit")).isGreaterThan(hitsBefore);

        // El idioma es parte de la clave: en inglés se arma otra entrada
        mvc.perform(get("/categories").param("q", "warehouse").header("Authorization", client)
                        .header("Accept-Language", "en-US"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Warehouse and inventory"));

        // 3. El administrador cambia el precio: la caché se invalida y se ve el precio nuevo
        String id = json.readTree(mvc.perform(get("/categories").param("q", "warehouse")
                        .header("Authorization", client)).andReturn().getResponse().getContentAsString())
                .get(0).get("id").asString();
        mvc.perform(put("/admin/categories/{id}/price", id).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"unit\":\"DAY\",\"amountMinor\":91000}"))
                .andExpect(status().isOk());
        mvc.perform(get("/categories").param("q", "warehouse").header("Authorization", client)
                        .header("Accept-Language", "es-CO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].price.amountMinor").value(91000));

        // 4. Una categoría desactivada desaparece de inmediato del catálogo público
        mvc.perform(post("/admin/categories/{id}/deactivate", id).header("Authorization", admin))
                .andExpect(status().is2xxSuccessful());
        mvc.perform(get("/categories").param("q", "warehouse").header("Authorization", client))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(post("/admin/categories/{id}/activate", id).header("Authorization", admin))
                .andExpect(status().is2xxSuccessful());
        mvc.perform(put("/admin/categories/{id}/price", id).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"unit\":\"DAY\",\"amountMinor\":85000}"))
                .andExpect(status().isOk());
    }

    private double counter(String result) {
        var c = metrics.find("jornal.catalog.cache").tag("result", result).counter();
        return c == null ? 0 : c.count();
    }

    private String clientToken() throws Exception {
        String email = "cache-" + UUID.randomUUID() + "@example.test";
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"%s","password":"Clave12345","givenName":"Ana","familyName":"Prueba",
                         "rol":"APPLICANT","acceptedTerms":true}
                        """.formatted(email)))
                .andExpect(status().isCreated());
        return login(email, "Clave12345");
    }

    private String login(String email, String password) throws Exception {
        String body = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(body).get("accessToken").asString();
    }
}
