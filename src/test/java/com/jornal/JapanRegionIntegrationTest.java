package com.jornal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** La misma base de código atendiendo Japón (APP_REGION=JP): orden del nombre, teléfono, dirección e idioma. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "jornal.region.code=JP")
@AutoConfigureMockMvc
class JapanRegionIntegrationTest {

    @Autowired MockMvc mvc;

    @Test
    void registersWithJapaneseRules() throws Exception {
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"%s","password":"Clave12345","givenName":"太郎","familyName":"山田",
                         "phone":"090-1234-5678","rol":"APPLICANT","acceptedTerms":true,
                         "address":{"fields":{"postal_code":"100-0001","prefecture":"東京都","city":"千代田区",
                           "town":"千代田","block":"1-1"}}}
                        """.formatted("taro-" + UUID.randomUUID() + "@example.test")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("山田 太郎"))
                .andExpect(jsonPath("$.countryCode").value("JP"))
                .andExpect(jsonPath("$.locale").value("ja-JP"))
                .andExpect(jsonPath("$.phone").value("+819012345678"));
    }

    @Test
    void defaultLanguageAndPricesComeFromTheJapaneseProfile() throws Exception {
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"x","password":"short","givenName":"","familyName":"山田","rol":"APPLICANT",
                         "acceptedTerms":true}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("リクエストに無効な項目があります"));

        mvc.perform(get("/region-profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("JPY"))
                .andExpect(jsonPath("$.tipMode").value("HIDDEN"))
                .andExpect(jsonPath("$.nameOrder").value("FAMILY_GIVEN"))
                .andExpect(jsonPath("$.datePattern").value("yyyy/MM/dd"));
    }

    @Test
    void rejectsColombianAddressInJapan() throws Exception {
        mvc.perform(post("/auth/register").header("Accept-Language", "en-US")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"%s","password":"Clave12345","givenName":"Ana","familyName":"Prueba",
                         "rol":"APPLICANT","acceptedTerms":true,
                         "address":{"fields":{"street_address":"Calle 10 # 5-20","city":"Bogotá"}}}
                        """.formatted("ana-" + UUID.randomUUID() + "@example.test")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REG002_INVALID_ADDRESS"))
                .andExpect(jsonPath("$.errors.street_address").value("UNKNOWN_FIELD"))
                .andExpect(jsonPath("$.errors.prefecture").value("REQUIRED"));
    }
}
