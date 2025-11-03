package io.github.ridiekel.jeletask.mqtt.listener.homeassistant;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.ridiekel.jeletask.client.spec.CentralUnit;
import io.github.ridiekel.jeletask.client.spec.ComponentSpec;

import java.util.List;
import java.util.Map;

public class HADeviceConfig {
    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final ObjectNode config;
    private final ObjectNode device;
    private final ArrayNode deviceIdentifiers;
    private final ObjectNode components;

    public HADeviceConfig(CentralUnit centralUnit, String identifier, List<ComponentSpec> componentSpecs, String baseTopicPrefix, Map<ComponentSpec, HAConfig<?>> componentConfigs) {
        this.config = OBJECT_MAPPER.createObjectNode();
        this.device = this.config.putObject("dev");  // Changed from "device" to "dev"
        this.deviceIdentifiers = this.device.putArray("ids");  // Changed from "identifiers" to "ids"
        this.components = this.config.putObject("cmps");  // Changed from "components" to "cmps"

        // Device information
        this.deviceIdentifiers.add(identifier);
        this.device.put("name", identifier);
        this.device.put("mf", "Teletask");  // Changed from "manufacturer" to "mf"
        this.device.put("mdl", centralUnit.getCentralUnitType().getDisplayName());  // Changed from "model" to "mdl"

        // Origin information (required for device discovery)
        ObjectNode origin = this.config.putObject("o");
        origin.put("name", "jeletask2mqtt");
        origin.put("sw", "4.3.3");

        // Note: Base topic (~) is not allowed at device level for device discovery

        // Add components directly to cmps object
        componentSpecs.stream()
                .filter(spec -> componentConfigs.containsKey(spec)) // Only include components that have configs
                .forEach(spec -> {
                    HAConfig<?> haConfig = componentConfigs.get(spec);
                    if (haConfig != null) {
                        try {
                            // Parse the existing HAConfig JSON and extract just the component-specific parts
                            ObjectNode componentJson = (ObjectNode) OBJECT_MAPPER.readTree(haConfig.toString());
                            
                            // Remove device info (since it's at the device level now)
                            componentJson.remove("device");
                            componentJson.remove("dev");
                            
                            // Add platform field (abbreviated as "p") with the domain name
                            String domain = getComponentDomain(spec);
                            componentJson.put("p", domain);
                            
                            // Adjust the base topic to be component-specific
                            String componentBaseTopic = baseTopicPrefix + "/" + spec.getFunction().toString().toLowerCase() + "/" + spec.getNumber();
                            componentJson.put("~", componentBaseTopic);
                            
                            // Use a unique key for each component (function + number)
                            String componentKey = spec.getFunction().toString().toLowerCase() + "_" + spec.getNumber();
                            this.components.set(componentKey, componentJson);
                        } catch (JsonProcessingException e) {
                            throw new IllegalStateException("Failed to process component config", e);
                        }
                    }
                });
    }

    private String getComponentDomain(ComponentSpec spec) {
        // Use the configured HA type from the JSON config file if available
        String configuredType = spec.getHAType();
        if (configuredType != null && !configuredType.isEmpty()) {
            return configuredType;
        }
        
        // Fallback to function-based mapping
        switch (spec.getFunction()) {
            case RELAY:
                return "switch";  // Default for relays
            case LOCMOOD:
            case GENMOOD:
            case TIMEDMOOD:
            case FLAG:
                return "switch";
            case DIMMER:
                return "light";
            case MOTOR:
                return "cover";
            case COND:
            case INPUT:
                return "binary_sensor";
            case SENSOR:
                // Check if this is a TEMPERATURECONTROL sensor -> should be climate
                if ("TEMPERATURECONTROL".equalsIgnoreCase(spec.getType())) {
                    return "climate";
                }
                return "sensor";
            default:
                return "switch"; // fallback
        }
    }

    @Override
    public String toString() {
        try {
            return OBJECT_MAPPER.writeValueAsString(this.config);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}