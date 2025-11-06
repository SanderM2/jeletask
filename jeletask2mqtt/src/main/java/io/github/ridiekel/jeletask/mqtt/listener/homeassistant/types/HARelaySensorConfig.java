package io.github.ridiekel.jeletask.mqtt.listener.homeassistant.types;

import io.github.ridiekel.jeletask.mqtt.listener.homeassistant.HAConfigParameters;
import io.github.ridiekel.jeletask.mqtt.listener.homeassistant.HAReadOnlyConfig;


public class HARelaySensorConfig extends HAReadOnlyConfig<HARelaySensorConfig> {
    public HARelaySensorConfig(HAConfigParameters parameters) {
        super(parameters);

        // For relay components configured as binary_sensor (read-only status)
        // Map relay ON/OFF states to binary sensor ON/OFF
        this.put("value_template", "{% if value_json.state == 'ON' %}ON{% else %}OFF{% endif %}");
    }
}