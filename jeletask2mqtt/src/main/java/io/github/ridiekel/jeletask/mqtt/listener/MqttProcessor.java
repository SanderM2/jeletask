package io.github.ridiekel.jeletask.mqtt.listener;

import io.github.ridiekel.jeletask.client.TeletaskClient;
import io.github.ridiekel.jeletask.client.listener.StateChangeListener;
import io.github.ridiekel.jeletask.client.spec.CentralUnit;
import io.github.ridiekel.jeletask.client.spec.ComponentSpec;
import io.github.ridiekel.jeletask.client.spec.Function;
import io.github.ridiekel.jeletask.mqtt.TeletaskService;
import io.github.ridiekel.jeletask.mqtt.listener.homeassistant.HAConfig;
import io.github.ridiekel.jeletask.mqtt.listener.homeassistant.HAConfigParameters;
import io.github.ridiekel.jeletask.mqtt.listener.homeassistant.HAReadWriteConfig;
import org.apache.commons.lang3.StringUtils;
import org.awaitility.Awaitility;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MqttProcessor implements StateChangeListener {
    private static final Logger LOG = LoggerFactory.getLogger(MqttProcessor.class);
    public static final int TIMEOUT = 30;

    private static final Pattern INVALID_CHARS = Pattern.compile("[^a-zA-Z0-9_-]");
    private static final Pattern SPACES = Pattern.compile("\\s+");
    private final TeletaskClient teletaskClient;

    private MqttClient client;
    private final String prefix;
    private final TeletaskService service;
    private final MqttConnectOptions connOpts;
    private final String teletaskIdentifier;

    public MqttProcessor(TeletaskService service) {
        this.service = service;

        String host = service.getConfiguration().getMqtt().getHost();
        String port = Optional.ofNullable(service.getConfiguration().getMqtt().getPort()).orElse("1883");
        String username = Optional.ofNullable(service.getConfiguration().getMqtt().getUsername()).map(String::trim).filter(u -> !u.isEmpty()).orElse(null);
        char[] password = Optional.ofNullable(service.getConfiguration().getMqtt().getPassword()).map(String::toCharArray).orElse(null);
        String clientId = Optional.ofNullable(service.getConfiguration().getMqtt().getClientId()).orElse("teletask2mqtt");
        this.prefix = removeInvalid(service.getConfiguration().getMqtt().getPrefix(), clientId);

        this.teletaskClient = service.createClient(this);

        this.teletaskIdentifier = resolveTeletaskIdentifier(service, this.teletaskClient.getConfig());

        LOG.info(String.format("teletask id: '%s'", this.teletaskIdentifier));
        LOG.info(String.format("host: '%s'", host));
        LOG.info(String.format("port: '%s'", port));
        LOG.info(String.format("username: '%s'", Optional.ofNullable(username).orElse("<not specified>")));
        LOG.info(String.format("clientId: '%s'", clientId));
        LOG.info(String.format("prefix: '%s'", this.prefix));

        this.connOpts = new MqttConnectOptions();
        this.connOpts.setMaxInflight(100000);
        this.connOpts.setCleanSession(true);
        this.connOpts.setAutomaticReconnect(true);
        this.connOpts.setUserName(username);
        this.connOpts.setPassword(password);

        this.connect(clientId, host, port);
    }

    private static String resolveTeletaskIdentifier(TeletaskService service, CentralUnit centralUnit) {
        return removeInvalid(service.getConfiguration().getId(), removeInvalid(centralUnit.getHost() + "_" + centralUnit.getPort(), "impossible"));
    }

    private static String removeInvalid(String value, String defaultValue) {
        return Optional.ofNullable(value).map(String::trim).filter(u -> !u.isEmpty()).map(u -> INVALID_CHARS.matcher(u).replaceAll("_")).orElseGet(() -> removeInvalid(defaultValue, "<not_found>"));
    }

    private void connect(String clientId, String host, String port) {
        String broker = "tcp://" + host + ":" + port;
        LOG.info("Connecting to MQTT broker...");

        try {
            this.client = new MqttClient(broker, clientId, new MemoryPersistence());
        } catch (MqttException e) {
            throw new RuntimeException(e);
        }
        Awaitility.await().atMost(TIMEOUT, TimeUnit.SECONDS).until(() -> {
            this.connect();
            this.publishConfig();
            this.subscribe();
            this.refreshStates();
            return true;
        });
    }

    private void refreshStates() {
        LOG.info("Refreshing states...");
        this.teletaskClient.groupGet();
    }

    private void subscribe() throws MqttException {
        LOG.info("Subscribing to topics...");
        this.client.subscribe(this.prefix + "/" + this.teletaskIdentifier + "/+/+/set", 0, new ChangeStateMqttCallback());
    }

    private synchronized void connect() {
        if (!this.client.isConnected()) {
            try {
                this.client.connect(this.connOpts);
            } catch (MqttException e) {
                LOG.debug("Connect warning: {}", e.getMessage());
            }

            Awaitility.await().pollDelay(100, TimeUnit.MILLISECONDS).atMost(TIMEOUT, TimeUnit.SECONDS).until(() -> {
                LOG.info("Waiting for connection...");
                return this.client.isConnected();
            });
        }
    }

    private void publishConfig() {
        //<discovery_prefix>/<component>/[<node_id>/]<object_id>/config
        LOG.info("Publishing config...");
        this.teletaskClient.getConfig().getAllComponents().forEach(c -> {
                this.publish("DELETE", c, this.createConfigTopic(c), "", LOG::debug);
        });
        this.teletaskClient.getConfig().getAllComponents().forEach(c -> {
            this.toConfig(c).ifPresent(config -> {
                this.publish("CREATE", c, createConfigTopic(c), config, LOG::debug);
            });
        });
    }

    private String createConfigTopic(ComponentSpec c) {
        return String.format("%s/%s/%s/%s_%s/config",
                Optional.ofNullable(this.service.getConfiguration().getMqtt().getDiscoveryPrefix()).orElse("homeassistant"),
                Optional.ofNullable(FUNCTION_TO_TYPE.get(c.getFunction())).map(f -> f.getType(c)).orElse("light"),
                this.teletaskIdentifier,
                c.getFunction().toString().toLowerCase(),
                c.getNumber());
    }

    private Optional<String> toConfig(ComponentSpec component) {
        return Optional.ofNullable(component).flatMap(c -> Optional.ofNullable(FUNCTION_TO_TYPE.get(c.getFunction())).map(f -> f.getConfig(this.teletaskClient.getConfig(), c, this.baseTopic(c), this.teletaskIdentifier)));
    }

    private static final Map<Function, FunctionConfig> FUNCTION_TO_TYPE = Map.ofEntries(
            Map.entry(Function.COND, f("binary_sensor", p -> {
                return null;
            })),
            Map.entry(Function.DIMMER, f("light", p -> {
                return null;
            })),
            Map.entry(Function.FLAG, f("binary_sensor", p -> {
                return null;
            })),
            Map.entry(Function.GENMOOD, f("scene", p -> {
                return null;
            })),
            Map.entry(Function.LOCMOOD, f("scene", p -> {
                return null;
            })),
            Map.entry(Function.MOTOR, f("cover", p -> {
                return null;
            })),
            Map.entry(Function.RELAY, f("light", HAReadWriteConfig::new)),
            Map.entry(Function.SENSOR, f("sensor", p -> {
                return null;
            })),
            Map.entry(Function.TIMEDMOOD, f("scene", p -> {
                return null;
            }))
    );

    static FunctionConfig f(String type, java.util.function.Function<HAConfigParameters, HAConfig<?>> config) {
        return new FunctionConfig(type, config);
    }

    private static final class FunctionConfig {
        private final java.util.function.Function<ComponentSpec, String> type;
        private final java.util.function.Function<HAConfigParameters, HAConfig<?>> config;

        private FunctionConfig(String typeIfAbsent, java.util.function.Function<HAConfigParameters, HAConfig<?>> config) {
            this.type = c -> Optional.ofNullable(c.getType()).orElse(typeIfAbsent);
            this.config = config;
        }

        public String getType(ComponentSpec componentSpec) {
            return this.type.apply(componentSpec);
        }

        public String getConfig(CentralUnit centralUnit, ComponentSpec componentSpec, String baseTopic, String identifier) {
            HAConfigParameters params = new HAConfigParameters(
                    centralUnit,
                    componentSpec,
                    baseTopic,
                    this.getType(componentSpec),
                    identifier
            );
            return Optional.ofNullable(this.config.apply(params)).map(HAConfig::toString).orElse(null);
        }

    }

    @Override
    public void receive(List<ComponentSpec> components) {
        components.forEach(c -> {
            String message = stateTranslateGet(c.getFunction(), c.getState().toUpperCase());
            String ttTopic = this.baseTopic(c) + "/state";
            this.publish("EVENT", c, ttTopic, message, LOG::info);
        });
    }

    private void publish(String what, ComponentSpec componentSpec, String topic, String message, Consumer<String> level) {
        try {
            MqttMessage mqttMessage = new MqttMessage(message.getBytes());
            mqttMessage.setQos(0);

            this.connect();

            LOG.debug(String.format("[%s] - %s - publishing topic '%s' -> %s", getWhat(what), getLoggingStringForComponent(componentSpec), topic, message));
            level.accept(String.format("[%s] - %s - %s", getWhat(what), getLoggingStringForComponent(componentSpec), message));
            this.client.publish(topic, mqttMessage);
        } catch (Exception e) {
            LOG.warn(e.getMessage());
        }
    }

    private String getLoggingStringForComponent(ComponentSpec componentSpec) {
        return String.format("[%s] - [%s] - [%s]", StringUtils.rightPad(componentSpec.getFunction().toString(), 10), StringUtils.leftPad(String.valueOf(componentSpec.getNumber()), 3), StringUtils.leftPad(componentSpec.getDescription(), 40));
    }

    private String getWhat(String what) {
        return StringUtils.rightPad(what, 7);
    }

    private String baseTopic(ComponentSpec c) {
        return this.prefix + "/" + this.teletaskIdentifier + "/" + c.getFunction().toString().toLowerCase() + "/" + c.getNumber();
    }

    private static final Map<Function, java.util.function.Function<String, String>> GET_TRANSLATIONS = Map.of(
            Function.MOTOR, MqttProcessor::motorStateTranslationGet,
            Function.DIMMER, MqttProcessor::dimmerStateTranslationGet
    );

    private static final Map<Function, java.util.function.Function<String, String>> SET_TRANSLATIONS = Map.of(
            Function.MOTOR, MqttProcessor::motorStateTranslationSet,
            Function.DIMMER, MqttProcessor::dimmerStateTranslationSet
    );

    private static String stateTranslateGet(Function function, String state) {
        return GET_TRANSLATIONS.containsKey(function) ? GET_TRANSLATIONS.get(function).apply(state) : state;
    }

    private static String dimmerStateTranslationGet(String state) {
        return state;
    }

    private static String motorStateTranslationGet(String state) {
        switch (state) {
            case "UP":
                state = "0";
                break;
            case "DOWN":
                state = "100";
                break;
        }
        return state;
    }

    private static String stateTranslateSet(Function function, String state) {
        return SET_TRANSLATIONS.containsKey(function) ? SET_TRANSLATIONS.get(function).apply(state) : state;
    }

    private static String dimmerStateTranslationSet(String state) {
        String newState = "0";
        try {
            if ("ON".equalsIgnoreCase(state)) {
                newState = "100";
            } else if ("OFF".equalsIgnoreCase(state)) {
                newState = "0";
            } else {
                newState = String.valueOf((int) Float.parseFloat(state));
            }
        } catch (Exception e) {
            LOG.debug(String.format("Could not translate dimmer state '%s' received from mqtt server", state));
        }
        return newState;
    }

    private static String motorStateTranslationSet(String state) {
        switch (state) {
            case "0":
                state = "UP";
                break;
            case "100":
                state = "DOWN";
                break;
        }
        return state;
    }

    @Override
    public void stop() {
        try {
            this.client.disconnect();
        } catch (MqttException e) {
            LOG.debug(e.getMessage());
        }
    }

    private class ChangeStateMqttCallback implements IMqttMessageListener {
        private final Pattern teletaskComponentPattern;

        ChangeStateMqttCallback() {
            this.teletaskComponentPattern = Pattern.compile(MqttProcessor.this.prefix + "/" + MqttProcessor.this.teletaskIdentifier + "/(\\w*)/(\\d*)/set");
        }

        @Override
        public void messageArrived(String topic, MqttMessage mqttMessage) {
            LOG.debug(String.format("MQTT message arrived '%s': '%s'", topic, new String(mqttMessage.getPayload())));
            try {
                Matcher matcher = this.teletaskComponentPattern.matcher(topic);
                if (matcher.find()) {
                    Function function = Function.valueOf(matcher.group(1).toUpperCase());
                    int number = Integer.parseInt(matcher.group(2));
                    String state = stateTranslateSet(function, mqttMessage.toString());

                    String componentLog = getLoggingStringForComponent(MqttProcessor.this.teletaskClient.getConfig().getComponent(function, number));
                    LOG.info(String.format("[%s] - %s - %s", getWhat("COMMAND"), componentLog, new String(mqttMessage.getPayload())));

                    MqttProcessor.this.teletaskClient.set(function, number, state,
                            (f, n, s) -> LOG.debug(String.format("[%s] MQTT topic '%s' changed state for: %s / %s -> %s", componentLog, topic, f, n, s)),
                            (f, n, s, e) -> LOG.warn(String.format("[%s] MQTT topic '%s' could not change state for: %s / %s -> %s", componentLog, topic, f, n, s)));
                }
            } catch (Exception e) {
                LOG.warn(String.format("MQTT topic '%s' could not change state to: %s", topic, mqttMessage.toString()), e);
                MqttProcessor.this.restartTeletask();
            }
        }
    }

    private void restartTeletask() {
        LOG.info("Restarting teletask connection due to exception in arriving message...");
        this.teletaskClient.restart();
        LOG.info("Restarted!");
    }
}
