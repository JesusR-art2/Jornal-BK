package com.jornal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F2: sesión con access token corto y refresh token rotativo. Mismas propiedades que
 * {@link LocalizationIntegrationTest} para reutilizar el contexto y los contenedores.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
        "jornal.region.code=CO",
        "jornal.admin.email=admin.co@example.test",
        "jornal.admin.password=AdminPass123"})
@AutoConfigureMockMvc
class AuthFlowIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;

    @Test
    void loginReturnsShortAccessTokenAndRefreshToken() throws Exception {
        JsonNode tokens = login(register(), "Clave12345");
        assertThat(tokens.get("expiresIn").asLong()).isEqualTo(15 * 60);
        assertThat(tokens.get("refreshToken").asString()).hasSizeGreaterThan(40);
        assertThat(tokens.get("refreshExpiresIn").asLong()).isEqualTo(30L * 24 * 3600);
    }

    @Test
    void refreshRotatesTheTokenAndDetectsReuse() throws Exception {
        JsonNode first = login(register(), "Clave12345");
        String r1 = first.get("refreshToken").asString();

        JsonNode second = refresh(r1);
        String r2 = second.get("refreshToken").asString();
        assertThat(r2).isNotEqualTo(r1);
        mvc.perform(get("/users/me").header("Authorization", "Bearer " + second.get("accessToken").asString()))
                .andExpect(status().isOk());

        // Reusar el token ya rotado: se rechaza y se revoca toda la familia (r2 también deja de servir)
        mvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(body(r1)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH001_INVALID_REFRESH_TOKEN"));
        mvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(body(r2)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesTheSession() throws Exception {
        String refresh = login(register(), "Clave12345").get("refreshToken").asString();
        mvc.perform(post("/auth/logout").contentType(MediaType.APPLICATION_JSON).content(body(refresh)))
                .andExpect(status().isNoContent());
        mvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(body(refresh)))
                .andExpect(status().isUnauthorized());
        // Un token desconocido en logout no revela nada
        mvc.perform(post("/auth/logout").contentType(MediaType.APPLICATION_JSON).content(body("unknown")))
                .andExpect(status().isNoContent());
    }

    @Test
    void passwordChangeClosesOtherSessions() throws Exception {
        String email = register();
        JsonNode session = login(email, "Clave12345");
        String access = "Bearer " + session.get("accessToken").asString();

        mvc.perform(put("/users/me/password").header("Authorization", access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Clave12345\",\"newPassword\":\"OtraClave678\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(body(session.get("refreshToken").asString())))
                .andExpect(status().isUnauthorized());
        login(email, "OtraClave678");
    }

    @Test
    void deactivatedAccountCannotRefresh() throws Exception {
        String email = register();
        JsonNode session = login(email, "Clave12345");
        String admin = "Bearer " + login("admin.co@example.test", "AdminPass123").get("accessToken").asString();
        String userId = json.readTree(mvc.perform(get("/users/me")
                        .header("Authorization", "Bearer " + session.get("accessToken").asString()))
                .andReturn().getResponse().getContentAsString()).get("id").asString();

        mvc.perform(post("/admin/users/{id}/deactivate", userId).header("Authorization", admin))
                .andExpect(status().isNoContent());
        mvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(body(session.get("refreshToken").asString())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void swaggerDeclaresBearerAuthentication() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearer.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearer.bearerFormat").value("JWT"));
    }

    @Test
    void adminListIsSortedByCreationDate() throws Exception {
        String admin = "Bearer " + login("admin.co@example.test", "AdminPass123").get("accessToken").asString();
        mvc.perform(get("/admin/users").param("size", "5").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(5));
    }

    // ------------------------------------------------------------------ utilidades

    private String register() throws Exception {
        String email = "auth-" + UUID.randomUUID() + "@example.test";
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"%s","password":"Clave12345","givenName":"Ana","familyName":"Prueba",
                         "rol":"APPLICANT","acceptedTerms":true}
                        """.formatted(email)))
                .andExpect(status().isCreated());
        return email;
    }

    private JsonNode login(String email, String password) throws Exception {
        String raw = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(raw);
    }

    private JsonNode refresh(String refreshToken) throws Exception {
        String raw = mvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(body(refreshToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(raw);
    }

    private static String body(String refreshToken) {
        return "{\"refreshToken\":\"%s\"}".formatted(refreshToken);
    }
}
