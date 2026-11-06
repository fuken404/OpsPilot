package dev.fuken404.opspilot;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/** Falla si un módulo usa clases internas de otro: mantiene el monolito modular de verdad. */
class ModularityTests {

    private final ApplicationModules modules = ApplicationModules.of(OpspilotApplication.class);

    @Test
    void modulesRespectTheirBoundaries() {
        modules.verify();
    }

    @Test
    void documentModules() {
        new Documenter(modules).writeModulesAsPlantUml();
    }
}
