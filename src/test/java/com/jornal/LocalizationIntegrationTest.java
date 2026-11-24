package com.jornal;

import com.jornal.region.DocumentKind;
import com.jornal.region.RegionApi;
import com.jornal.shared.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Criterio de terminado de F1 en la región CO (APP_REGION por defecto), con PostgreSQL real.
 * Todos los datos personales son ficticios.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
        "jornal.region.code=CO",
        "jornal.admin.email=admin.co@example.test",
        "jornal.admin.password=AdminPass123"})
@AutoConfigureMockMvc
class LocalizationIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired RegionApi regionApi;
    @Autowired JsonMapper json;

    // ------------------------------------------------------------------ catálogo en 5 idiomas

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "es-CO, Aseo y limpieza",
            "pt-BR, Limpeza",
            "en-US, Cleaning",
            "fr-FR, Ménage et nettoyage",
            "ja-JP, 清掃",
            "de-DE, Aseo y limpieza",      // no soportado: idioma del perfil (es-CO)
    })
    void categoriesAreTranslated(String acceptLanguage, String expectedName) throws Exception {
        String token = registerAndLogin("APPLICANT");
        mvc.perform(get("/categories").param("q", "cleaning")
                        .header("Authorization", token).header("Accept-Language", acceptLanguage))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slug").value("cleaning"))
                .andExpect(jsonPath("$[0].name").value(expectedName))
                .andExpect(jsonPath("$[0].price.currency").value("COP"))
                .andExpect(jsonPath("$[0].price.amountMinor").value(90000))
                .andExpect(jsonPath("$[0].price.moneyScale").value(0));
    }

    @Test
    void searchFindsCategoriesByTranslatedNameWithoutAccents() throws Exception {
        String token = registerAndLogin("APPLICANT");
        // "menage" coincide sin tildes con "Ménage et nettoyage" y con "Déménagement et manutention"
        mvc.perform(get("/categories").param("q", "menage")
                        .header("Authorization", token).header("Accept-Language", "fr-FR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].slug", containsInAnyOrder("cleaning", "moving")))
                .andExpect(jsonPath("$[0].name").value("Déménagement et manutention"));   // orden alfabético francés
    }

    // ------------------------------------------------------------------ perfil regional

    @Test
    void publicRegionProfile() throws Exception {
        mvc.perform(get("/region-profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("CO"))
                .andExpect(jsonPath("$.currency").value("COP"))
                .andExpect(jsonPath("$.nameOrder").value("GIVEN_FAMILY"))
                .andExpect(jsonPath("$.tipMode").value("OPTIONAL"))
                .andExpect(jsonPath("$.reviewStatus").value("VERIFICAR"))
                .andExpect(jsonPath("$.addressSchema[0].fieldKey").value("street_address"));
        mvc.perform(get("/region-profile/holidays").param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].date").value("2026-11-02"));
    }

    @Test
    void adminUpdatesProfileWithOptimisticVersion() throws Exception {
        String admin = login("admin.co@example.test", "AdminPass123");
        ObjectNode body = profileAsRequest();
        long version = body.get("version").asLong();
        ((ArrayNode) body.withArray("tipSuggestedPercents")).removeAll().add(10).add(15);

        mvc.perform(put("/admin/region-profile").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(version + 1))
                .andExpect(jsonPath("$.tipSuggestedPercents[1]").value(15))
                .andExpect(jsonPath("$.addressSchema.length()").value(5));

        // Repetir con la versión vieja: conflicto
        mvc.perform(put("/admin/region-profile").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REG005_VERSION_CONFLICT"));
    }

    // ------------------------------------------------------------------ registro validado por país

    @Test
    void registersApplicantWithCountryPhoneAndAddress() throws Exception {
        String email = unique();
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"%s","password":"Clave12345","givenName":"Ana","familyName":"Prueba",
                         "phone":"300 123 4567","rol":"APPLICANT","acceptedTerms":true,
                         "address":{"label":"Casa","fields":{"street_address":"Calle 10 # 5-20",
                           "city":"Bogotá","department":"Cundinamarca","postal_code":"110111"}}}
                        """.formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Ana Prueba"))
                .andExpect(jsonPath("$.countryCode").value("CO"))
                .andExpect(jsonPath("$.locale").value("es-CO"))
                .andExpect(jsonPath("$.phone").value("+573001234567"))
                .andExpect(jsonPath("$.consentUpToDate").value(true));

        String token = login(email, "Clave12345");
        mvc.perform(get("/users/me/addresses").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].formatted").value("Calle 10 # 5-20, Bogotá, Cundinamarca, 110111"));
    }

    @Test
    void rejectsAddressThatDoesNotFitTheCountrySchema() throws Exception {
        mvc.perform(post("/auth/register").header("Accept-Language", "es-CO")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"%s","password":"Clave12345","givenName":"Ana","familyName":"Prueba",
                         "rol":"APPLICANT","acceptedTerms":true,
                         "address":{"fields":{"street_address":"Calle 10 # 5-20","postal_code":"ABC","prefecture":"Tokyo"}}}
                        """.formatted(unique())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REG002_INVALID_ADDRESS"))
                .andExpect(jsonPath("$.detail").value("La dirección no es válida para este país"))
                .andExpect(jsonPath("$.errors.city").value("REQUIRED"))
                .andExpect(jsonPath("$.errors.department").value("REQUIRED"))
                .andExpect(jsonPath("$.errors.postal_code").value("INVALID_FORMAT"))
                .andExpect(jsonPath("$.errors.prefecture").value("UNKNOWN_FIELD"));
    }

    @Test
    void rejectsPhoneFromAnotherCountryAndForeignRegistration() throws Exception {
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"%s","password":"Clave12345","givenName":"Ana","familyName":"Prueba",
                         "phone":"03-1234-5678","rol":"APPLICANT","acceptedTerms":true}
                        """.formatted(unique())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("USER008_INVALID_PHONE"));

        mvc.perform(post("/auth/register").header("Accept-Language", "pt-BR")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"%s","password":"Clave12345","givenName":"Ana","familyName":"Prueba",
                         "rol":"APPLICANT","country":"BR","acceptedTerms":true}
                        """.formatted(unique())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("USER010_REGION_MISMATCH"))
                .andExpect(jsonPath("$.detail").value("Este servidor só cadastra usuários de CO"));
    }

    @Test
    void registersBusinessWithValidTaxId() throws Exception {
        String email = unique();
        String nit = "800197268-4";
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(business(email, nit)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rol").value("BUSINESS"));

        String token = login(email, "Clave12345");
        mvc.perform(get("/users/me/business").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taxIdType").value("NIT"))
                .andExpect(jsonPath("$.taxIdNumber").value("8001972684"))
                .andExpect(jsonPath("$.billingAddress.fields.city").value("Medellín"));

        // La misma identificación fiscal no se puede registrar dos veces
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(business(unique(), nit)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER013_TAX_ID_REGISTERED"));
    }

    @Test
    void rejectsBusinessWithInvalidOrPersonalTaxId() throws Exception {
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(business(unique(), "800197268-5")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REG004_INVALID_DOCUMENT"));

        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(business(unique(), "1020304050").replace("\"NIT\"", "\"CC\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REG003_DOCUMENT_TYPE_NOT_ALLOWED"));

        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"%s","password":"Clave12345","givenName":"Ana","familyName":"Prueba",
                         "rol":"BUSINESS","acceptedTerms":true}
                        """.formatted(unique())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("USER011_BUSINESS_REQUIRED"));
    }

    // ------------------------------------------------------------------ reglas de los otros 4 países (mismo código)

    @Test
    void otherCountriesValidateWithTheirOwnProfile() {
        assertThat(regionApi.validateAddress("BR", Map.of("street", "Rua das Flores", "number", "100",
                "neighborhood", "Centro", "city", "São Paulo", "state", "SP", "postal_code", "01001-000")))
                .containsEntry("postal_code", "01001-000");
        assertThat(regionApi.validateAddress("US", Map.of("line1", "1 Main St", "city", "Albany",
                "state", "NY", "postal_code", "12207"))).hasSize(4);
        assertThat(regionApi.validateAddress("FR", Map.of("street_address", "10 rue de Rivoli",
                "postal_code", "75001", "city", "Paris"))).hasSize(3);
        assertThat(regionApi.formatAddress("JP", regionApi.validateAddress("JP", Map.of("postal_code", "100-0001",
                "prefecture", "東京都", "city", "千代田区", "town", "千代田", "block", "1-1"))))
                .isEqualTo("100-0001, 東京都, 千代田区, 千代田, 1-1");
        assertThatThrownBy(() -> regionApi.validateAddress("US", Map.of("line1", "1 Main St", "city", "Albany",
                "state", "New York", "postal_code", "1220")))
                .isInstanceOf(ApiException.class);

        assertThat(regionApi.validateDocument("BR", "CNPJ", "11.222.333/0001-81", DocumentKind.TAX))
                .isEqualTo("11222333000181");
        assertThat(regionApi.validateDocument("JP", "CORPORATE_NUMBER", "8700110005901", DocumentKind.TAX))
                .isEqualTo("8700110005901");
        assertThat(regionApi.validateDocument("US", "EIN", "12-3456789", DocumentKind.TAX)).isEqualTo("12-3456789");
        assertThatThrownBy(() -> regionApi.validateDocument("FR", "SIRET", "12345678901234", DocumentKind.TAX))
                .isInstanceOf(ApiException.class);
    }

    // ------------------------------------------------------------------ utilidades

    private ObjectNode profileAsRequest() throws Exception {
        String raw = mvc.perform(get("/region-profile")).andReturn().getResponse().getContentAsString();
        ObjectNode node = (ObjectNode) json.readTree(raw);
        node.remove("code");
        node.remove("updatedAt");
        return node;
    }

    private String business(String email, String nit) {
        return """
                {"email":"%s","password":"Clave12345","givenName":"Luis","familyName":"Prueba",
                 "rol":"BUSINESS","acceptedTerms":true,
                 "business":{"legalName":"Comercio Ficticio SAS","taxIdType":"NIT","taxIdNumber":"%s",
                   "billingAddress":{"fields":{"street_address":"Carrera 43A # 1-50","city":"Medellín",
                     "department":"Antioquia"}}}}
                """.formatted(email, nit);
    }

    private String registerAndLogin(String rol) throws Exception {
        String email = unique();
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"%s","password":"Clave12345","givenName":"Ana","familyName":"Prueba",
                         "rol":"%s","acceptedTerms":true}
                        """.formatted(email, rol)))
                .andExpect(status().isCreated());
        return login(email, "Clave12345");
    }

    private String login(String email, String password) throws Exception {
        String raw = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = json.readTree(raw);
        return "Bearer " + node.get("accessToken").asString();
    }

    private static String unique() {
        return "user-" + UUID.randomUUID() + "@example.test";
    }
}
