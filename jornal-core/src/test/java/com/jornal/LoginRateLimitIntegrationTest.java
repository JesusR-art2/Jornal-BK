package com.jornal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** P2: límite de intentos de inicio de sesión con Redis (AUTH002), con límites bajos para la prueba. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
        "jornal.region.code=CO",
        "jornal.security.login.max-attempts=3",
        "jornal.security.login.max-attempts-per-ip=6",
        "jornal.security.login.window=10m"})
@AutoConfigureMockMvc
class LoginRateLimitIntegrationTest {

    @Autowired MockMvc mvc;

    @Test
    void blocksTheAccountAfterTooManyFailuresEvenWithTheRightPassword() throws Exception {
        String email = register();
        for (int i = 0; i < 3; i++) {
            login(email, "Incorrecta1", "10.0.0.1")
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("USER003_INVALID_CREDENTIALS"));
        }
        login(email, "Clave12345", "10.0.0.1")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("AUTH002_TOO_MANY_ATTEMPTS"))
                .andExpect(jsonPath("$.detail").value("Demasiados intentos de inicio de sesión, inténtalo más tarde"));

        // Otra cuenta desde otra IP no se ve afectada
        login(register(), "Clave12345", "10.0.0.2").andExpect(status().isOk());
    }

    @Test
    void successResetsTheAccountCounter() throws Exception {
        String email = register();
        login(email, "Incorrecta1", "10.0.0.3").andExpect(status().isUnauthorized());
        login(email, "Incorrecta1", "10.0.0.3").andExpect(status().isUnauthorized());
        login(email, "Clave12345", "10.0.0.3").andExpect(status().isOk());
        login(email, "Incorrecta1", "10.0.0.3").andExpect(status().isUnauthorized());
        login(email, "Incorrecta1", "10.0.0.3").andExpect(status().isUnauthorized());
        login(email, "Clave12345", "10.0.0.3").andExpect(status().isOk());
    }

    @Test
    void blocksAnIpThatTriesManyAccounts() throws Exception {
        for (int i = 0; i < 6; i++) {
            login("nadie-" + i + "@example.test", "Incorrecta1", "10.0.0.9").andExpect(status().isUnauthorized());
        }
        login(register(), "Clave12345", "10.0.0.9")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("AUTH002_TOO_MANY_ATTEMPTS"));
    }

    private ResultActions login(String email, String password, String ip) throws Exception {
        return mvc.perform(post("/auth/login").header("Accept-Language", "es-CO")
                .with(request -> { request.setRemoteAddr(ip); return request; })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
    }

    private String register() throws Exception {
        String email = "login-" + UUID.randomUUID() + "@example.test";
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"%s","password":"Clave12345","givenName":"Ana","familyName":"Prueba",
                         "rol":"APPLICANT","acceptedTerms":true}
                        """.formatted(email)))
                .andExpect(status().isCreated());
        return email;
    }
}
