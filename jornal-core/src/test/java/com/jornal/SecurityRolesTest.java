package com.jornal;

import com.jornal.catalog.CategoryService;
import com.jornal.shared.RestSecurityErrorHandlers;
import com.jornal.shared.SecurityConfig;
import com.jornal.shared.TimeConfig;
import com.jornal.users.CountryRepository;
import com.jornal.users.Rol;
import com.jornal.users.User;
import com.jornal.region.services.RegionService;
import com.jornal.users.services.AddressService;
import com.jornal.users.services.AuthService;
import com.jornal.users.services.BusinessService;
import com.jornal.users.services.JwtService;
import com.jornal.users.services.UserService;
import com.jornal.workers.WorkerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.jornal.shared.JwtProperties;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifica la autorización por rol con tokens reales emitidos por {@link JwtService}
 * y validados por el decodificador de {@link SecurityConfig}.
 */
@WebMvcTest
@Import({SecurityConfig.class, RestSecurityErrorHandlers.class, JwtService.class, TimeConfig.class})
class SecurityRolesTest {

    @Autowired MockMvc mvc;
    @Autowired JwtService jwtService;

    @MockitoBean UserService userService;
    @MockitoBean AuthService authService;
    @MockitoBean CategoryService categoryService;
    @MockitoBean WorkerService workerService;
    @MockitoBean CountryRepository countryRepository;
    @MockitoBean RegionService regionService;
    @MockitoBean AddressService addressService;
    @MockitoBean BusinessService businessService;
    @MockitoBean com.jornal.requests.services.RequestService requestService;
    @MockitoBean com.jornal.users.UserRepository userRepository;
    @MockitoBean com.jornal.requests.services.RatingService ratingService;
    @MockitoBean com.jornal.console.ConsoleService consoleService;

    @Autowired JwtEncoder jwtEncoder;
    @Autowired JwtProperties jwtProperties;

    @Test
    void tokenFromAnotherRegionIsRejected() throws Exception {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer()).issuedAt(now).expiresAt(now.plusSeconds(600))
                .subject(UUID.randomUUID().toString())
                .claim("role", "ADMIN").claim("region", "JP")
                .build();
        String foreign = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();

        mvc.perform(get("/admin/users").header("Authorization", "Bearer " + foreign))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void corsAllowsTheFrontendOriginOnly() throws Exception {
        mvc.perform(options("/users/me")
                        .header("Origin", "http://localhost:4200")
                        .header("Access-Control-Request-Method", "PATCH")
                        .header("Access-Control-Request-Headers", "Authorization, Accept-Language"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));
        mvc.perform(options("/users/me")
                        .header("Origin", "https://co.jornal.localhost")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://co.jornal.localhost"));
        mvc.perform(options("/users/me")
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }

    @Test
    void businessRoutesOnlyForBusiness() throws Exception {
        mvc.perform(get("/users/me/business").header("Authorization", bearer(Rol.BUSINESS)))
                .andExpect(status().isOk());
        mvc.perform(get("/users/me/business").header("Authorization", bearer(Rol.APPLICANT)))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/admin/region-profile/holidays/2026-12-25").header("Authorization", bearer(Rol.BUSINESS)))
                .andExpect(status().isForbidden());
    }

    @Test
    void securityErrorsAreTranslated() throws Exception {
        mvc.perform(get("/users/me").header("Accept-Language", "ja-JP"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("ログインが必要か、トークンが無効です"));
        mvc.perform(get("/admin/users").header("Authorization", bearer(Rol.WORKER))
                        .header("Accept-Language", "pt-BR,pt;q=0.9"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Você não tem permissão para realizar esta ação"));
    }

    @Test
    void withoutTokenProtectedRoutesReturn401() throws Exception {
        mvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void invalidTokenReturns401() throws Exception {
        mvc.perform(get("/users/me").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void publicRoutesDoNotNeedToken() throws Exception {
        mvc.perform(get("/countries")).andExpect(status().isOk());
        mvc.perform(get("/region-profile")).andExpect(status().isOk());
    }

    @Test
    void applicantCanUseOwnAccountButNotAdminNorWorkerRoutes() throws Exception {
        String token = bearer(Rol.APPLICANT);
        mvc.perform(get("/users/me").header("Authorization", token)).andExpect(status().isOk());
        mvc.perform(get("/categories").header("Authorization", token)).andExpect(status().isOk());
        mvc.perform(get("/admin/users").header("Authorization", token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(get("/workers/me").header("Authorization", token)).andExpect(status().isForbidden());
    }

    @Test
    void workerCanUseWorkerRoutesButNotAdminRoutes() throws Exception {
        String token = bearer(Rol.WORKER);
        mvc.perform(get("/workers/me").header("Authorization", token)).andExpect(status().isOk());
        mvc.perform(get("/admin/categories").header("Authorization", token)).andExpect(status().isForbidden());
    }

    @Test
    void adminCanUseAdminRoutesButNotWorkerRoutes() throws Exception {
        String token = bearer(Rol.ADMIN);
        mvc.perform(get("/admin/users").header("Authorization", token)).andExpect(status().isOk());
        mvc.perform(get("/admin/categories").header("Authorization", token)).andExpect(status().isOk());
        mvc.perform(get("/workers/me").header("Authorization", token)).andExpect(status().isForbidden());
    }

    private String bearer(Rol rol) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(rol.name().toLowerCase() + "@example.test");
        user.setName("Test " + rol.name());
        user.setRol(rol);
        return "Bearer " + jwtService.issue(user).value();
    }
}
