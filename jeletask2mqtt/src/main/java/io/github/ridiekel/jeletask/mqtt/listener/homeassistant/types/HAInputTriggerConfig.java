package io.github.ridiekel.jeletask.mqtt.listener.homeassistant.types;

import io.github.ridiekel.jeletask.mqtt.listener.homeassistant.HAConfigParameters;
import io.github.ridiekel.jeletask.mqtt.listener.homeassistant.HAReadOnlyConfig;


public class HAInputTriggerConfig extends HAReadOnlyConfig<HAInputTriggerConfig> {
    public HAInputTriggerConfig(HAConfigParameters parameters) {
        super(parameters);

        // For INPUT components, we need to map the various input states to binary sensor ON/OFF
        // PRESSED, SHORT_PRESS, LONG_PRESS -> ON
        // NOT_PRESSED, OPEN, CLOSED -> OFF
        this.put("value_template", "{% if value_json.state in ['PRESSED', 'SHORT_PRESS', 'LONG_PRESS'] %}ON{% else %}OFF{% endif %}");
    }
}
