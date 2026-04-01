package com.agent.service.testcase;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public class ResourceProfileRegistry {

    private final Map<String, TestResourceLifecycleHandler> handlersByProfile;

    public ResourceProfileRegistry(List<TestResourceLifecycleHandler> handlers) {
        Objects.requireNonNull(handlers, "handlers");
        Map<String, TestResourceLifecycleHandler> registry = new LinkedHashMap<>();
        for (TestResourceLifecycleHandler handler : handlers) {
            if (handler == null) {
                continue;
            }
            String profile = normalize(handler.resourceProfile());
            if (profile == null) {
                throw new IllegalArgumentException("resourceProfile must not be blank");
            }
            TestResourceLifecycleHandler previous = registry.putIfAbsent(profile, handler);
            if (previous != null) {
                throw new IllegalStateException("Duplicate testcase resourceProfile handler: " + profile);
            }
        }
        this.handlersByProfile = Map.copyOf(registry);
    }

    public void assertSupported(String resourceProfile) {
        getRequired(resourceProfile);
    }

    public boolean supports(String resourceProfile) {
        return normalize(resourceProfile) != null
                && handlersByProfile.containsKey(normalize(resourceProfile));
    }

    public TestResourceLifecycleHandler getRequired(String resourceProfile) {
        String normalized = normalize(resourceProfile);
        TestResourceLifecycleHandler handler = normalized == null ? null : handlersByProfile.get(normalized);
        if (handler == null) {
            throw new UnknownResourceProfileException(resourceProfile, supportedProfiles());
        }
        return handler;
    }

    public Set<String> supportedProfiles() {
        return Set.copyOf(new TreeSet<>(handlersByProfile.keySet()));
    }

    private String normalize(String resourceProfile) {
        if (resourceProfile == null) {
            return null;
        }
        String normalized = resourceProfile.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
