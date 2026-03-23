package com.agent.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppConfigRuntimePathTest {

    private final AppConfig appConfig = new AppConfig();

    @Test
    void explicitDataDirOverridesAgentHomeSources() {
        Path dataDir = appConfig.resolveDataDir(
                "/var/lib/ascend-agent/db",
                "/srv/ascend-home",
                "/env/ascend-home",
                "/workspace/repo");

        assertEquals(Path.of("/var/lib/ascend-agent/db"), dataDir);
    }

    @Test
    void systemPropertyHomeOverridesEnvironmentHome() {
        Path dataDir = appConfig.resolveDataDir(
                null,
                "/srv/ascend-home",
                "/env/ascend-home",
                "/workspace/repo");

        assertEquals(Path.of("/srv/ascend-home/db"), dataDir);
    }

    @Test
    void environmentHomeOverridesDefaultRepoHome() {
        Path dataDir = appConfig.resolveDataDir(
                null,
                null,
                "/env/ascend-home",
                "/workspace/repo");

        assertEquals(Path.of("/env/ascend-home/db"), dataDir);
    }

    @Test
    void defaultsToHiddenAscendAgentDirectoryUnderRepo() {
        Path dataDir = appConfig.resolveDataDir(
                null,
                null,
                null,
                "/workspace/repo");

        assertEquals(Path.of("/workspace/repo/.ascend_agent/db"), dataDir);
    }
}
