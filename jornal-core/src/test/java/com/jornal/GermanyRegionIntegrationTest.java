package com.jornal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P2: un sexto país solo con datos (migración V18). La misma base de código atiende Alemania con APP_REGION=DE:
 * perfil, idioma, teléfono, dirección, catálogo en alemán con precios en euros y el impuesto del país.
 * Los textos sin traducción al alemán salen del respaldo en inglés. Datos personales ficticios.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "jornal.region.code=DE")
@AutoConfigureMockMvc
class GermanyRegionIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;

    @Test
    void profileComesFromData() throws Exception {
        mvc.perform(get("/region-profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("DE"))
                .andExpect(jsonPath("$.defaultLocale").value("de-DE"))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.datePattern").value("dd.MM.yyyy"))
                .andExpect(jsonPath("$.receiptType").value("RECHNUNG"))
                .andExpect(jsonPath("$.taxRules[0].name").value("USt"))
                .andExpect(jsonPath("$.paymentMethods[2].code").value("SEPA"));
        mvc.perform(get("/region-profile/holidays").param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(9));
    }

    @Test
    void registersWithGermanPhoneAndAddressAndSeesTheCatalogInGerman() throws Exception {
        String email = "max-" + UUID.randomUUID() + "@example.test";
        mvc.perform(post("/auth/register").header("Accept-Language", "de").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"email":"%s","password":"Clave12345","givenName":"Max","familyName":"Muster",
                         "phone":"0151 23456789","rol":"APPLICANT","acceptedTerms":true,
                         "address":{"fields":{"street_address":"Unter den Linden 77","postal_code":"10117",
                           "city":"Berlin"}}}
                        """.formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.countryCode").value("DE"))
                .andExpect(jsonPath("$.locale").value("de-DE"))
                .andExpect(jsonPath("$.phone").value("+4915123456789"));

        String token = login(email);
        mvc.perform(get("/categories").param("q", "lager").header("Authorization", token)
                        .header("Accept-Language", "de-DE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slug").value("warehouse"))
                .andExpect(jsonPath("$[0].name").value("Lager und Inventur"))
                .andExpect(jsonPath("$[0].price.amountMinor").value(14000))
                .andExpect(jsonPath("$[0].price.currency").value("EUR"));
    }

    @Test
    void quotesTheTestOrderInEurosWithGermanVat() throws Exception {
        String email = "erika-" + UUID.randomUUID() + "@example.test";
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"%s","password":"Clave12345","givenName":"Erika","familyName":"Muster",
                         "rol":"APPLICANT","acceptedTerms":true}
                        """.formatted(email)))
                .andExpect(status().isCreated());
        String token = login(email);
        String category = json.readTree(mvc.perform(get("/categories").param("q", "warehouse")
                        .header("Authorization", token)).andReturn().getResponse().getContentAsString())
                .get(0).get("id").asString();

        // Pedido D4: 2 × 140,00 € + 9,00 € de desplazamiento; USt 19 % incluida; propina del 5 % del subtotal
        mvc.perform(post("/requests/quote").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"categoryId":"%s","units":2,"scheduledLocal":"%sT20:30","timezone":"Europe/Berlin",
                         "address":{"street_address":"Unter den Linden 77","postal_code":"10117","city":"Berlin"},
                         "latitude":52.5170,"longitude":13.3889,"paymentMethod":"SEPA","tipPercent":5}
                        """.formatted(category, LocalDate.now().plusDays(3))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subtotalMinor").value(28000))
                .andExpect(jsonPath("$.travelFeeMinor").value(900))
                .andExpect(jsonPath("$.taxMinor").value(4614))
                .andExpect(jsonPath("$.taxIncluded").value(true))
                .andExpect(jsonPath("$.tipMinor").value(1400))
                .andExpect(jsonPath("$.totalMinor").value(30300))
                .andExpect(jsonPath("$.currency").value("EUR"));
    }

    @Test
    void untranslatedTextsFallBackToEnglish() throws Exception {
        mvc.perform(post("/auth/register").header("Accept-Language", "de-DE").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"email":"x","password":"short","givenName":"","familyName":"Muster","rol":"APPLICANT",
                         "acceptedTerms":true}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The request contains invalid fields"));
    }

    private String login(String email) throws Exception {
        String body = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"Clave12345\"}".formatted(email)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(body).get("accessToken").asString();
    }
}
