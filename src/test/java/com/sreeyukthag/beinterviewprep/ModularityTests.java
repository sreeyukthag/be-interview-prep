package com.sreeyukthag.beinterviewprep;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTests {

    @Test
    void modulesRespectDeclaredBoundaries() {
        ApplicationModules modules = ApplicationModules.of(BeInterviewPrepApplication.class);

        modules.verify();
    }
}
