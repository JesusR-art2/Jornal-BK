package com.jornal;

import com.jornal.catalog.CategoryService;
import com.jornal.shared.RestSecurityErrorHandlers;
import com.jornal.shared.SecurityConfig;
import com.jornal.shared.TimeConfig;
import com.jornal.users.CountryRepository;
import com.jornal.users.Rol;
import com.jornal.users.User;
import com.jornal.users.services.AuthService;
import com.jornal.users.services.JwtService;
import com.jornal.users.services.UserService;
import com.jornal.workers.WorkerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
        return "Bearer " + jwtService.issue(user).accessToken();
    }
}
