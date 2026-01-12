package com.note;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Entity wrapper class for working with compiled chart entities
 * Provides utility methods for extracting beat, lane, flick status, and references
 */
public class Entity {
    private final JsonNode node;
    private final String name;
    private final String archetype;
    
    public Entity(JsonNode node) {
        this.node = node;
        this.name = node.has("name") ? node.get("name").asText() : "";
        this.archetype = node.has("archetype") ? node.get("archetype").asText() : "";
    }
    
    /**
     * Get the JsonNode wrapped by this Entity
     */
    public JsonNode getNode() {
        return node;
    }
    
    /**
     * Get entity name
     */
    public String getName() {
        return name;
    }
    
    /**
     * Get archetype name
     */
    public String getArchetype() {
        return archetype;
    }
    
    /**
     * Extract beat value from entity data
     */
    public double getBeat() {
        JsonNode data = node.get("data");
        return getFieldValueByName(data, "#BEAT");
    }
    
    /**
     * Extract lane value from entity data
     */
    public int getLane() {
        JsonNode data = node.get("data");
        double laneValue = getFieldValueByName(data, "lane");
        return (int) laneValue;
    }
    
    /**
     * Check if this entity is a flick type
     * Returns true for FlickNote or SlideEndFlickNote
     */
    public boolean isFlickType() {
        return "FlickNote".equals(archetype) || "SlideEndFlickNote".equals(archetype);
    }
    
    /**
     * Check if entity has flick attribute in its data
     */
    public boolean hasFlick() {
        JsonNode data = node.get("data");
        if (data != null && data.isArray()) {
            for (JsonNode item : data) {
                if (item.has("name") && "flick".equals(item.get("name").asText())) {
                    if (item.has("value")) {
                        return item.get("value").asDouble() != 0;
                    }
                    return true;
                }
            }
        }
        return isFlickType();
    }
    
    /**
     * Get reference by field name (e.g., "next", "prev", "last", "first")
     */
    public String getRefByName(String refName) {
        JsonNode data = node.get("data");
        if (data != null && data.isArray()) {
            for (JsonNode item : data) {
                if (item.has("name") && item.get("name").asText().equals(refName)) {
                    if (item.has("ref")) {
                        return item.get("ref").asText();
                    }
                }
            }
        }
        return null;
    }
    
    /**
     * Get the next reference name (from "next" field)
     */
    public String getNextRefName() {
        return getRefByName("next");
    }
    
    /**
     * Get the previous reference name (from "prev" field)
     */
    public String getPrevRefName() {
        return getRefByName("prev");
    }
    
    /**
     * Get the last reference name (from "last" field)
     */
    public String getLastRefName() {
        return getRefByName("last");
    }
    
    /**
     * Get the first reference name (from "first" field)
     */
    public String getFirstRefName() {
        return getRefByName("first");
    }
    
    /**
     * Check if this is a slide start note
     */
    public boolean isSlideStart() {
        return "SlideStartNote".equals(archetype);
    }
    
    /**
     * Check if this is a slide tick note
     */
    public boolean isSlideTick() {
        return "SlideTickNote".equals(archetype);
    }
    
    /**
     * Check if this is a slide end note (including flick)
     */
    public boolean isSlideEnd() {
        return "SlideEndNote".equals(archetype) || "SlideEndFlickNote".equals(archetype);
    }
    
    /**
     * Check if this is any slide-related note
     */
    public boolean isSlideRelated() {
        return archetype.startsWith("Slide") || archetype.contains("Connector");
    }
    
    /**
     * Extract field value from data array by name
     */
    private double getFieldValueByName(JsonNode dataArray, String targetName) {
        if (dataArray == null || !dataArray.isArray()) {
            return 0.0;
        }
        for (JsonNode item : dataArray) {
            if (item.has("name") && item.get("name").asText().equals(targetName) && item.has("value")) {
                return item.get("value").asDouble();
            }
        }
        return 0.0;
    }
    
    @Override
    public String toString() {
        return String.format("Entity{name='%s', archetype='%s', beat=%.2f, lane=%d}", 
                           name, archetype, getBeat(), getLane());
    }
}
