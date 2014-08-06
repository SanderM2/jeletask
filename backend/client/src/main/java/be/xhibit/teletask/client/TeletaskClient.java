package be.xhibit.teletask.client;

import be.xhibit.teletask.client.builder.composer.MessageHandler;
import be.xhibit.teletask.client.builder.composer.MessageHandlerFactory;
import be.xhibit.teletask.client.builder.message.MessageUtilities;
import be.xhibit.teletask.client.builder.message.executor.MessageExecutor;
import be.xhibit.teletask.client.builder.message.messages.MessageSupport;
import be.xhibit.teletask.client.builder.message.messages.impl.EventMessage;
import be.xhibit.teletask.client.builder.message.messages.impl.GetMessage;
import be.xhibit.teletask.client.builder.message.messages.impl.LogMessage;
import be.xhibit.teletask.client.builder.message.messages.impl.SetMessage;
import be.xhibit.teletask.client.builder.message.strategy.KeepAliveStrategy;
import be.xhibit.teletask.client.listener.StateChangeListener;
import be.xhibit.teletask.model.spec.ClientConfigSpec;
import be.xhibit.teletask.model.spec.ComponentSpec;
import be.xhibit.teletask.model.spec.Function;
import be.xhibit.teletask.model.spec.State;
import be.xhibit.teletask.model.spec.StateEnum;
import be.xhibit.teletask.server.TeletaskTestServer;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Created by IntelliJ IDEA.
 * User: Bruno Braes, http://www.xhibit.be
 * Date: 7/09/12
 * Time: 15:40
 * <p/>
 * <p/>
 * FunctionSet(int Fnc, int Opt, int Number, int State)
 * > example to switch relays: FunctionSet(1, 0, 19, 1) -> switches relays 19 to on (=bureau).
 * <p/>
 * - Fnc: Fnc ( RELAY, DIMMER, MOTOR, MTRUPDOWN, LOCMOOD, TIMEDMOOD, GENMOOD, FLAG, PROCES, REGIME, SERVICE, MESSAGE) = see "Constants" below / Functions.cs for full list.
 * - Opt: not required for RELAYS? value 0? (dependent on the function: see Options.cs for full list)
 * - Number:
 * for Fnc = FNC_RELAY, FNC_DIMMER Number = 1 to Maximum -> Number out the output relay
 * for Fnc = FNC_TPKEY -> Number = Touch panel number you want to simulate
 * All other Fnc -> Number = 0 to Maximum -1
 * - State:
 * for Fnc=FNC_DIMMER & FNC_MOTOR -> State = 0 to 255 (always use the result from function ConvPercToDimVal, may never be 3!!!)
 * for Fnc = FNC_TPKEY
 * -> State bit 3-0 = Key number (0 to 7)
 * -> State bit 7-8 = 00 Normal Short Press, 01 Key Depressed, 10 Key Released
 * for all other Fnc
 * -> State = 0 or 255 (or 1) = OFF or ON
 * <p/>
 * Output: Return value:
 * - 0 = Message successfully transmitted
 * - 1 = Communication not opened
 * - 2 = No Answer
 * <p/>
 * All commands and messages in both directions will use the same frame getLogInfo:
 * STX (02h) + Length + Command Number + Parameter 1 + ... + Parameter n + ChkSm
 * <p/>
 * The length does not include the ChkSm-byte. The ChkSm is calculated on Command Number + Command Parameters + Length + STX.
 * After the ChkSm the central unit send an acknowledge byte 0A (hex). If no acknowledge byte is send the command is not handled.
 * <p/>
 * --------------------------
 * <p/>
 * Function Set
 * - Description: This command allows the CCT to set individual functions. See “methods” for detailed descriptions
 * - Command number: 01h
 * - Length: 6
 * - Direction: From TDS to CCT.
 * - Parameter 1 = Fnc
 * - Parameter 2 = Outp
 * - Parameter 3 = State
 * <p/>
 * Function Get
 * - Description: When the TDS receives this command it reports the level of the specified load. See methods for detailed descriptions
 * - Command number: 02h
 * - Length: 5
 * - Direction: From CCT to TDS
 * - Parameter 1 = fnc
 * - Parameter 2 = Outp
 * <p/>
 * Function Log On/Off
 * - Description: When the TDS receives this command it (de-)activates it’s channel for reporting the function!
 * This function will open/close a channel for the function
 * Example: If you call this function with the parameter Fnc=FNC_RELAY and State=1, all changes on relays will occur as ‘event’! In case you set State=0 no more events will occur from relays.
 * - Command number: 03h
 * - Length: 5
 * - Direction: From CCT to TDS
 * - Parameter 1 = fnc
 * - Parameter 2 = state
 * <p/>
 * Lux values
 * To change from byte to lux = (10 (byte / 40)) - 1
 * To change from lux to byte = Log10(lux + 1) * 40
 * <p/>
 * Constants
 * The functions in the DLL use a parameter “Fnc” and can have following values
 * FNC_RELAY = 1 (control or get the status of a relay)
 * FNC_DIMMER = 2 (control or get the status of a dimmer)
 * FNC_MOTOR = 6 (control or get the status of a Motor: On/Off)
 * FNC_MTRUPDOWN = 55 (control or get the status of a Motor: Op/Down)
 * FNC_LOCMOOD = 8 (control or get the status of a Local Mood)
 * FNC_TIMEDMOOD = 9 (control or get the status of a Timed Local Mood)
 * FNC_GENMOOD = 10 (control or get the status of a General Mood)
 * FNC_FLAG = 15 (control or get the status of a Flag)
 * FNC_PROCES = 3 (control or get the status of a Process function)
 * FNC_REGIME = 14 (control or get the status of a Regime function)
 * FNC_SERVICE = 53 (control or get the status of a Service function)
 * FNC_MESSAGE = 54 (control or get the status of a Messages or Alarms)
 * FNC_COND = 60 (get the status of a Condition)
 * FNC_TPKEY = 52 (simulate a key press on an interface)
 * FNC_GETSENSTARGET = 21 (get the status of a Sensor setting)
 * <p/>
 * If you are making your own interface you have to take care of the following:
 * <p/>
 * - With the LOG function you open a kind of 'channel' from the specific device type (ex. relays) from the central unit to your device.
 * This mean that when such a device has a change the central unit will sent automatically a "report" to you.
 * You only open the LOG for the devices you really need (ex. relays, dimmer, local moods, sensors)
 * - When you want to know a state a a specific device you have to send a GET command, afterwards (asynchronously) you will get a "report" with the state of the device
 * Normally it's not necessary to use this is you opened the LOG
 * - When you want to know the state of several devices (at startup) you send a Group GET command for a specific type (ex. relays)with the numbers of all devices you want to know the state.
 * Afterwards for every device you asked there will come a "report"
 * - These reports are coming on the socket you open, so you have to check the bytes that are coming in, but you don't have to open a listener on a port.
 * - You can send a keep alive to make sure that the central unit don't close the port because there is no activity
 */
public final class TeletaskClient {
    /**
     * Logger responsible for logging and debugging statements.
     */
    private static final Logger LOG = LoggerFactory.getLogger(TeletaskClient.class);

    private Socket socket;
    private OutputStream outputStream;
    private InputStream inputStream;

    private final ClientConfigSpec config;

    private static TeletaskClient instance = null;

    private final ExecutorService executorService;

    private final Timer keepAliveTimer = new Timer();
    private final Timer eventListenerTimer = new Timer();

    private final List<StateChangeListener> stateChangeListeners = new ArrayList<>();
    private TeletaskTestServer teletaskTestServer;

    /**
     * Default constructor.  Responsible for reading the client config (JSON).
     * Singleton class.  Private constructor to prevent new instance creations.
     */
    private TeletaskClient(ClientConfigSpec config) {
        this.config = config;
        this.executorService = Executors.newSingleThreadExecutor();
    }

    /**
     * Create of get an instance of the TDSClient.
     *
     * @return a new or existing TDSClient instance.
     */
    public static synchronized TeletaskClient getInstance(ClientConfigSpec clientConfig) {
        if (instance == null) {
            instance = new TeletaskClient(clientConfig);
            instance.start();
        }

        return instance;
    }

// ################################################ PUBLIC API FUNCTIONS

    public void registerStateChangeListener(StateChangeListener listener) {
        this.stateChangeListeners.add(listener);
    }

    public void set(ComponentSpec component, State state) {
        this.set(component.getFunction(), component.getNumber(), state);
    }

    public void set(Function function, int number, State state) {
        Preconditions.checkNotNull(state, "Given state not found");

        try {
            this.execute(new SetMessage(this.getConfig(), function, number, state));

            ComponentSpec component = this.getConfig().getComponent(function, number);
            Long start = System.currentTimeMillis();
            while (!state.equals(component.getState()) && (System.currentTimeMillis() - start) < 5000) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    LOG.error("Exception ({}) caught in set: {}", e.getClass().getName(), e.getMessage(), e);
                }
            }
            if (!state.equals(component.getState())) {
                throw new RuntimeException("Did not receive a state change within 5 seconds. Assuming the state change did not succeed");
            }
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    public void groupGet(final Function function, final int... numbers) {
        try {
            this.getExecutorService().submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        TeletaskClient.this.getMessageHandler().getGroupGetStrategy().execute(TeletaskClient.this.getConfig(), TeletaskClient.this.getOutputStream(), TeletaskClient.this.getInputStream(), function, numbers);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }).get();
        } catch (InterruptedException e) {
            LOG.error("Exception ({}) caught in groupGet: {}", e.getClass().getName(), e.getMessage(), e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    public void groupGet() {
        for (Function function : Function.values()) {
            this.groupGet(function);
        }
    }

    public void groupGet(Function function) {
        List<? extends ComponentSpec> components = this.getConfig().getComponents(function);
        if (components != null) {
            this.groupGet(function, Ints.toArray(Lists.transform(components, new com.google.common.base.Function<ComponentSpec, Integer>() {
                @Override
                public Integer apply(ComponentSpec input) {
                    return input.getNumber();
                }
            })));
        }
    }

    public void get(Function function, int number) {
        this.get(this.getConfig().getComponent(function, number));
    }

    public void get(ComponentSpec component) {
        try {
            this.execute(new GetMessage(this.getConfig(), component.getFunction(), component.getNumber()));
        } catch (ExecutionException e) {
            LOG.error("Exception ({}) caught in get: {}", e.getClass().getName(), e.getMessage(), e);
        }
    }

    public void stop() {

        // close all log events to stop reporting
        this.sendLogEventMessages(StateEnum.OFF);
        this.stopEventListener();
        this.stopKeepAliveService();
        this.stopExecutorService();
        this.closeInputStream();
        this.closeOutputStream();
        this.closeSocket();
        this.stopTestServer();

    }

    private void stopTestServer() {
        if (this.getTeletaskTestServer() != null) {
            this.getTeletaskTestServer().stop();
        }
    }

    private void stopKeepAliveService() {
        this.getKeepAliveTimer().cancel();
    }

    private void stopEventListener() {
        this.getEventListenerTimer().cancel();
    }

    private void closeSocket() {
        try {
            this.socket.close();
        } catch (IOException e) {
            LOG.error("Exception ({}) caught in stop: {}", e.getClass().getName(), e.getMessage(), e);
        }
    }

    private void closeOutputStream() {
        try {
            this.getOutputStream().close();
        } catch (IOException e) {
            LOG.error("Exception ({}) caught in stop: {}", e.getClass().getName(), e.getMessage(), e);
        }
    }

    private void closeInputStream() {
        try {
            this.getInputStream().close();
        } catch (IOException e) {
            LOG.error("Exception ({}) caught in stop: {}", e.getClass().getName(), e.getMessage(), e);
        }
    }

    private void stopExecutorService() {
        try {
            this.getExecutorService().shutdown();
            this.getExecutorService().awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            LOG.error("Exception ({}) caught in stop: {}", e.getClass().getName(), e.getMessage(), e);
        }
    }

    public ClientConfigSpec getConfig() {
        return this.config;
    }

    // ################################################ PRIVATE API FUNCTIONS

    private void execute(MessageSupport message) throws ExecutionException {
        try {
            this.getExecutorService().submit(MessageExecutor.of(message, this.getOutputStream())).get();
        } catch (InterruptedException e) {
            LOG.error("Exception ({}) caught in execute: {}", e.getClass().getName(), e.getMessage(), e);
        }
    }

    private void sendLogEventMessages(StateEnum state) {
        this.sendLogEventMessage(Function.RELAY, state);
        this.sendLogEventMessage(Function.LOCMOOD, state);
        this.sendLogEventMessage(Function.GENMOOD, state);
        this.sendLogEventMessage(Function.MOTOR, state);
        this.sendLogEventMessage(Function.DIMMER, state);
        this.sendLogEventMessage(Function.COND, state);
    }

    private void start() {
        String host = this.getConfig().getHost();
        int port = this.getConfig().getPort();

        host = this.startTestServer(host, port);

        this.connect(host, port);

        this.startEventListener();

        this.groupGet();

        this.startKeepAlive();

        this.sendLogEventMessages(StateEnum.ON);
    }

    private String startTestServer(String host, int port) {
        if (!Boolean.getBoolean("production")) {
            LOG.debug("Starting test server...");
            host = "localhost";

            this.teletaskTestServer = new TeletaskTestServer(port, this.getConfig(), this.getMessageHandler());

            new Thread(this.getTeletaskTestServer()).start();

            LOG.debug("Started test server!");
        }
        return host;
    }

    private TeletaskTestServer getTeletaskTestServer() {
        return this.teletaskTestServer;
    }

    private void startEventListener() {
        this.getEventListenerTimer().schedule(new TimerTask() {
            @Override
            public void run() {
//                TeletaskClient.this.getExecutorService().submit(new EventMessageListener());
                new EventMessageListener().run();
            }
        }, 0, 20);
    }

    private void connect(String host, int port) {
        // Connect method
        LOG.debug("Connecting to {}:{}", host, port);

        try {
            this.socket = new Socket(host, port);
            this.socket.setKeepAlive(true);
            this.socket.setSoTimeout(5000);
        } catch (IOException e) {
            LOG.error("Problem connecting to host: {}", host, e);
            System.exit(1);
        }

        LOG.debug("Successfully Connected");

        try {
            this.outputStream = new DataOutputStream(this.socket.getOutputStream());
            this.inputStream = new DataInputStream(this.socket.getInputStream());
        } catch (IOException e) {
            LOG.error("Couldn't get I/O for the connection to: {}:{}", host, port);
            System.exit(1);
        }
    }

    private void startKeepAlive() {
        KeepAliveStrategy keepAliveStrategy = this.getMessageHandler().getKeepAliveStrategy();
        this.getKeepAliveTimer().schedule(new KeepAliveService(keepAliveStrategy), 0, keepAliveStrategy.getIntervalMinutes() * 60 * 1000);
    }

    private MessageHandler getMessageHandler() {
        return MessageHandlerFactory.getMessageHandler(this.getConfig().getCentralUnitType());
    }

    private void sendLogEventMessage(Function function, StateEnum state) {
        try {
            this.execute(new LogMessage(this.getConfig(), function, state));
        } catch (ExecutionException e) {
            LOG.error("Exception ({}) caught in sendLogEventMessage: {}", e.getClass().getName(), e.getMessage(), e);
        }
    }

    /**
     * Prevent cloning.
     *
     * @return Nothing really, because this will always result in an Exception.
     * @throws CloneNotSupportedException when called.
     */
    @Override
    public final TeletaskClient clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }

    private ExecutorService getExecutorService() {
        return this.executorService;
    }

    public OutputStream getOutputStream() {
        return this.outputStream;
    }

    public InputStream getInputStream() {
        return this.inputStream;
    }

    private class EventMessageListener implements Runnable {
        @Override
        public void run() {
            try {
                List<MessageSupport> messages = this.getEventMessageServerResponses();
                List<ComponentSpec> components = new ArrayList<>();
                for (MessageSupport message : messages) {
                    if (message instanceof EventMessage) {
                        EventMessage eventMessage = (EventMessage) message;
                        this.handleEvent(LOG, TeletaskClient.this.getConfig(), eventMessage);
                        components.add(TeletaskClient.this.getConfig().getComponent(eventMessage.getFunction(), eventMessage.getNumber()));
                    }
                }
                if (!components.isEmpty()) {
                    for (StateChangeListener stateChangeListener : TeletaskClient.this.stateChangeListeners) {
                        stateChangeListener.event(components);
                    }
                }
            } catch (Exception e) {
                LOG.error("Exception ({}) caught in run: {}", e.getClass().getName(), e.getMessage(), e);
            }
        }

        private void handleEvent(Logger logger, ClientConfigSpec config, EventMessage eventMessage) {
            if (logger.isDebugEnabled()) {
                logger.debug("Event: {}", eventMessage.getLogInfo(eventMessage.getRawBytes()));
            }
            ComponentSpec component = config.getComponent(eventMessage.getFunction(), eventMessage.getNumber());
            if (component != null) {
                State state = eventMessage.getState();
                if (state.getFunction() != Function.MOTOR || StateEnum.valueOf(state.getValue().toUpperCase()) != StateEnum.STOP) {
                    component.setState(state);
                }
            } else {
                logger.debug("Component {}:{} not found.", eventMessage.getFunction(), eventMessage.getNumber());
            }
        }

        private List<MessageSupport> getEventMessageServerResponses() throws Exception {
            return MessageUtilities.receive(LOG, TeletaskClient.this.getInputStream(), TeletaskClient.this.getConfig(), TeletaskClient.this.getMessageHandler());
        }
    }

    private class KeepAliveService extends TimerTask {
        private final KeepAliveStrategy keepAliveStrategy;

        public KeepAliveService(KeepAliveStrategy keepAliveStrategy) {
            this.keepAliveStrategy = keepAliveStrategy;
        }

        @Override
        public void run() {
            try {
                TeletaskClient.this.getExecutorService().execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            KeepAliveService.this.keepAliveStrategy.execute(TeletaskClient.this.getConfig(), TeletaskClient.this.getOutputStream(), TeletaskClient.this.getInputStream());
                        } catch (Exception e) {
                            LOG.error("Exception ({}) caught in run: {}", e.getClass().getName(), e.getMessage(), e);
                        }
                    }
                });
            } catch (Exception e) {
                LOG.error("Exception ({}) caught in run: {}", e.getClass().getName(), e.getMessage(), e);
            }
        }
    }

    public Timer getKeepAliveTimer() {
        return this.keepAliveTimer;
    }

    public Timer getEventListenerTimer() {
        return this.eventListenerTimer;
    }

    public List<StateChangeListener> getStateChangeListeners() {
        return this.stateChangeListeners;
    }
}