package com.jornal;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularidadTest {

    @Test
    void modulesAreWellFormed() {
        ApplicationModules.of(JornalBackendApplication.class).verify();
    }
}
