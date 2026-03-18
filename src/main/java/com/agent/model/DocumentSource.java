package com.agent.model;

import java.util.HashMap;
import java.util.Map;

/**
 * 文档源配置
 */
public class DocumentSource {
    private String id;
    private String name;
    private DocumentSourceType type;
    private String location;
    private boolean enabled;
    private int refreshInterval;
    private Map<String, String> metadata = new HashMap<>();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public DocumentSourceType getType() { return type; }
    public void setType(DocumentSourceType type) { this.type = type; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public int getRefreshInterval() { return refreshInterval; }
    public void setRefreshInterval(int refreshInterval) { this.refreshInterval = refreshInterval; }

    public Map<String, String> getMetadata() { return metadata; }
    public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }
}
