package be.xhibit.teletask.client.builder.message;

import be.xhibit.teletask.client.builder.ByteUtilities;
import be.xhibit.teletask.client.builder.SendResult;
import be.xhibit.teletask.client.builder.composer.MessageHandler;
import be.xhibit.teletask.client.builder.composer.MessageHandlerFactory;
import be.xhibit.teletask.client.builder.message.response.AcknowledgeServerResponse;
import be.xhibit.teletask.client.builder.message.response.EventMessageServerResponse;
import be.xhibit.teletask.client.builder.message.response.ServerResponse;
import be.xhibit.teletask.model.spec.ClientConfigSpec;
import be.xhibit.teletask.model.spec.Command;
import be.xhibit.teletask.model.spec.ComponentSpec;
import be.xhibit.teletask.model.spec.Function;
import be.xhibit.teletask.model.spec.State;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.primitives.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

public abstract class MessageSupport<R> {
    /**
     * Logger responsible for logging and debugging statements.
     */
    private static final Logger LOG = LoggerFactory.getLogger(MessageSupport.class);

    private static final Pattern REMOVE_NAMES = Pattern.compile("[^\\|]");
    private static final Pattern INSERT_PLACEHOLDERS = Pattern.compile("\\|   ");

    private final ClientConfigSpec clientConfig;

    protected MessageSupport(ClientConfigSpec clientConfig) {
        this.clientConfig = clientConfig;
    }

    protected R execute(OutputStream outputStream, InputStream inputStream) {
        R response = null;
        MessageHandler messageHandler = this.getMessageHandler();
        if (this.isValid()) {
            if (messageHandler.knowsCommand(this.getCommand())) {
                byte[] message = messageHandler.compose(this.getCommand(), this.getPayload());

                try {
                    this.send(outputStream, message);

                    response = this.receive(inputStream);
                } catch (Exception e) {
                    LOG.error("Exception ({}) caught in execute: {}", e.getClass().getName(), e.getMessage(), e);
                }
            } else {
                LOG.warn("Message handler '{}' does not know of command '{}'", this.getMessageHandler().getClass().getSimpleName(), this.getCommand());
            }
        } else {
            LOG.warn("Invalid request: {}", this);
        }

        return response;
    }

    private void send(OutputStream outputStream, byte[] message) throws IOException {
        //Send data over socket
        if (Boolean.getBoolean("production")) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Sending: {}", this.getLogInfo(message));
            }
            outputStream.write(message);
            outputStream.flush();
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Test mode send: {}", this.getLogInfo(message));
            }
        }
    }

    private R receive(InputStream inputStream) throws Exception {
        List<ServerResponse> responses = new ArrayList<>();

        byte[] overflow = new byte[0];
        long startTime = System.currentTimeMillis();
        while (responses.size() != this.getExpectedResultCount()) {
            if ((System.currentTimeMillis() - startTime) > 5000) {
                throw new RuntimeException("Did not receive data in a timely fashion. This means either: \n\t- You sent wrong data to the server and hence did not get an acknowledge.\n\t- Or you requested information from the server that was not available to the server");
            }
            int available = inputStream.available();
            if (available > 0) {
                byte[] read = new byte[available];
                inputStream.read(read, 0, available);
                byte[] data = Bytes.concat(overflow, read);
                overflow = this.extractResponses(responses, data);
            }
            Thread.sleep(10);
        }

        return this.createResponse(responses);
    }

    protected int getExpectedResultCount() {
        return 1;
    }

    protected abstract R createResponse(List<ServerResponse> serverResponses);

    private byte[] extractResponses(Collection<ServerResponse> responses, byte[] data) throws Exception {
        LOG.debug("Raw bytes {}", ByteUtilities.bytesToHex(data));
        byte[] overflow = new byte[0];
        for (int i = 0; i < data.length; i++) {
            byte b = data[i];
            LOG.debug("Processing: {}", ByteUtilities.bytesToHex(b));
            if (b == this.getMessageHandler().getStxValue()) {
                int eventLengthInclChkSum = data[i+1] + 1; // +1 for checksum
                byte[] event = new byte[eventLengthInclChkSum];

                if (i + eventLengthInclChkSum > data.length) {
                    overflow = new byte[data.length - i];
                    System.arraycopy(data, i, event, 0, data.length - i);
                    i = data.length-1;

                    LOG.debug("Overflowing: {}", ByteUtilities.bytesToHex(overflow));
                } else {
                    System.arraycopy(data, i, event, 0, eventLengthInclChkSum);

                    i += eventLengthInclChkSum-1;

                    LOG.debug("Event bytes part: {}", ByteUtilities.bytesToHex(event));
                    try {
                        responses.add(new EventMessageServerResponse(this.getMessageHandler().parseEvent(this.getClientConfig(), event)));
                    } catch (Exception e) {
                        LOG.error("Exception ({}) caught in readLogResponse: {}", e.getClass().getName(), e.getMessage(), e);
                    }
                }
            } else if (b == this.getMessageHandler().getAcknowledgeValue()) {
                responses.add(new AcknowledgeServerResponse());
            }
        }
        return overflow;
    }

    protected boolean isValid() {
        return true;
    }

    protected ClientConfigSpec getClientConfig() {
        return this.clientConfig;
    }

    /**
     * This should return the payload without the function part of the payload.
     *
     * @return The payload after 'function'
     */
    protected abstract byte[] getPayload();

    protected abstract Command getCommand();

    public String getLogInfo(byte[] message) {
        List<String> hexParts = this.getHexParts(message);

        StringBuilder table = this.getHeader(hexParts);
        String hexLine = this.getMessageAsTableContent(hexParts, table);
        String seperatorLine = this.getTableSeperatorLine(table.length());
        table.append(System.lineSeparator()).append(seperatorLine);
        table.append(System.lineSeparator()).append(hexLine);
        table.append(System.lineSeparator()).append(seperatorLine);

        return System.lineSeparator() + "Command: " + this.getCommand() + ", Payload: " + this.getPayloadLogInfo() + System.lineSeparator() + "Raw Bytes: " + ByteUtilities.bytesToHex(message) + System.lineSeparator() + seperatorLine + System.lineSeparator() + table.toString();
    }

    protected abstract String getPayloadLogInfo();

    private String getTableSeperatorLine(int size) {
        return Strings.repeat("-", size);
    }

    private String getMessageAsTableContent(List<String> parts, StringBuilder builder) {
        String content = builder.toString();
        content = REMOVE_NAMES.matcher(content).replaceAll(" ");
        content = INSERT_PLACEHOLDERS.matcher(content).replaceAll("| %s");
        content = String.format(content, parts.toArray());
        return content;
    }

    private StringBuilder getHeader(Collection<String> parts) {
        StringBuilder builder = new StringBuilder(500);
        builder.append("| STX | Length | Command | ");
        for (int i = 1; i <= parts.size() - 4; i++) {
            String logHeaderName = this.getLogHeaderName(i);
            if (logHeaderName == null) {
                logHeaderName = "Parameter " + i;
            }
            builder.append(logHeaderName).append(" | ");
        }
        builder.append("ChkSm |");
        return builder;
    }

    private List<String> getHexParts(byte[] message) {
        return Splitter.on(' ')
                .trimResults()
                .omitEmptyStrings()
                .splitToList(ByteUtilities.bytesToHex(message));
    }

    protected String getLogHeaderName(int index) {
        return this.getMessageHandler().getCommandConfig(this.getCommand()).getParamNames().get(index);
    }

    protected String formatState(State... states) {
        Collection<String> log = new ArrayList<>();
        for (State state : states) {
            log.add("State( " + state + " | " + (state == null ? null : this.getMessageHandler().getStateConfig(state).getNumber()) + " | " + (state == null ? null : ByteUtilities.bytesToHex((byte) this.getMessageHandler().getStateConfig(state).getNumber())) + ")");
        }
        return Joiner.on(", ").join(log);
    }

    protected MessageHandler getMessageHandler() {
        return MessageHandlerFactory.getMessageHandler(this.getClientConfig().getCentralUnitType());
    }

    protected String formatFunction(Function function) {
        return "Function( " + function + " | " + this.getMessageHandler().getFunctionConfig(function).getNumber() + " | " + ByteUtilities.bytesToHex((byte) this.getMessageHandler().getFunctionConfig(function).getNumber()) + ")";
    }

    protected String formatOutput(int... numbers) {
        Collection<String> outputs = new ArrayList<>();
        for (int number : numbers) {
            outputs.add("Output( " + number + " | " + ByteUtilities.bytesToHex(this.getMessageHandler().composeOutput(number)) + ")");
        }
        return Joiner.on(", ").join(outputs);
    }

    protected SendResult expectSingleAcknowledge(List<ServerResponse> serverResponses) {
        SendResult result = null;
        if (serverResponses.size() == 1 && serverResponses.get(0) instanceof AcknowledgeServerResponse) {
            result = SendResult.SUCCESS;
        } else {
            throw new RuntimeException("No acknowledge received");
        }
        return result;
    }

    protected ComponentSpec expectSingleEventMessage(Iterable<ServerResponse> serverResponses) {
        EventMessageServerResponse serverResponse = (EventMessageServerResponse) Iterables.find(serverResponses, new Predicate<ServerResponse>() {
            @Override
            public boolean apply(ServerResponse input) {
                return input instanceof EventMessageServerResponse;
            }
        });
        return this.convert(serverResponse);
    }

    protected ComponentSpec convert(EventMessageServerResponse serverResponse) {
        EventMessage eventMessage = serverResponse.getEventMessage();
        if (LOG.isDebugEnabled()) {
            LOG.debug("Event: {}", eventMessage.getLogInfo(eventMessage.getRawBytes()));
        }
        ComponentSpec component = this.getClientConfig().getComponent(eventMessage.getFunction(), eventMessage.getNumber());
        component.setState(eventMessage.getState());
        return component;
    }

    /**
     * Method for toString readability
     *
     * @return The Classname of the current class
     */
    public String getMessageClass() {
        return this.getClass().getSimpleName();
    }

    @Override
    public String toString() {
        try {
            return new ObjectMapper().writer().writeValueAsString(this);
        } catch (JsonProcessingException e) {
            LOG.error("Exception ({}) caught in toString: {}", e.getClass().getName(), e.getMessage());
        }
        return super.toString();
    }
}
