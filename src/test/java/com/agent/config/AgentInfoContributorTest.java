package com.agent.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentInfoContributorTest {

    @Test
    @SuppressWarnings("unchecked")
    void exposesCurrentAgentAlignmentStage() {
        AgentConfig config = new AgentConfig();
        config.setEnabled(false);
        config.setStage("alignment");
        config.setMode("knowledge-base-only");
        config.setEntrypoint("knowledge-base-controller");
        config.setZeroInteractionEnabled(false);
        config.setOrchestrationEnabled(false);

        AppConfig appConfig = new AppConfig();
        InfoContributor contributor = appConfig.agentInfoContributor(config);
        Info.Builder builder = new Info.Builder();

        contributor.contribute(builder);
        Map<String, Object> details = (Map<String, Object>) builder.build().getDetails().get("agent");

        assertEquals(false, details.get("enabled"));
        assertEquals("alignment", details.get("stage"));
        assertEquals("knowledge-base-only", details.get("mode"));
        assertEquals("knowledge-base-controller", details.get("entrypoint"));
        assertEquals(false, details.get("zeroInteractionEnabled"));
        assertEquals(false, details.get("orchestrationEnabled"));
    }
}
