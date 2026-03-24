package com.agent.runtime;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class StartupRuntimeGuardrailsTest {

    @Test
    void startServiceScriptPinsJava21AndDisablesShutdownHooks() throws IOException {
        String script = Files.readString(Path.of("scripts/start_service.sh"));

        assertTrue(script.contains("DEFAULT_JAVA_HOME=\"/usr/lib/jvm/java-21-openjdk-amd64\""));
        assertTrue(script.contains("ASCEND_AGENT_JAVA_BIN"));
        assertTrue(script.contains("--spring.main.register-shutdown-hook=false"));
        assertTrue(script.contains("--logging.register-shutdown-hook=false"));
        assertTrue(script.contains("http://127.0.0.1:$SERVER_PORT$HEALTH_PATH"));
    }

    @Test
    void templateDisablesSpringAndLoggingShutdownHooksByDefault() throws IOException {
        String template = Files.readString(Path.of("src/main/resources/application.yml.template"));

        assertTrue(template.contains("register-shutdown-hook: ${SPRING_MAIN_REGISTER_SHUTDOWN_HOOK:false}"));
        assertTrue(template.contains("register-shutdown-hook: ${LOGGING_REGISTER_SHUTDOWN_HOOK:false}"));
    }
}
