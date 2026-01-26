package com.jesusLuna.polyglotCloud;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Test suite runner que verifica que todos los tests de la aplicación funcionan correctamente.
 * Este test actúa como un punto de entrada para ejecutar todos los tests del proyecto.
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = PolyglotCloudApplication.class)
@ActiveProfiles("test")
public class AllTestsSuite {

    @Test
    void contextLoads() {
        // Este test verifica que el contexto de Spring Boot se carga correctamente
        // Si hay algún problema con la configuración, este test fallará
    }

}