package io.jeletask.teletask.client.builder.message.messages.impl;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.jeletask.teletask.client.builder.composer.config.configurables.FunctionConfigurable;
import io.jeletask.teletask.client.builder.message.messages.FunctionBasedMessageSupport;
import io.jeletask.teletask.model.spec.ClientConfigSpec;
import io.jeletask.teletask.model.spec.Command;
import io.jeletask.teletask.model.spec.ComponentSpec;
import io.jeletask.teletask.model.spec.Function;
import io.jeletask.teletask.utilities.Bytes;

public class EventMessage extends FunctionBasedMessageSupport {
    private final int number;
    private final String state;
    private final byte[] rawBytes;

    public EventMessage(ClientConfigSpec clientConfig, byte[] rawBytes, Function function, int number, String state) {
        super(clientConfig, function);
        this.rawBytes = rawBytes;
        this.number = number;
        this.state = state;
    }

    public String getState() {
        return this.state;
    }

    public int getNumber() {
        return this.number;
    }

    @JsonIgnore
    public byte[] getRawBytes() {
        return this.rawBytes;
    }

    @Override
    protected byte[] getPayload() {
        FunctionConfigurable functionConfig = this.getMessageHandler().getFunctionConfig(this.getFunction());
        ComponentSpec component = this.getClientConfig().getComponent(this.getFunction(), this.getNumber());
        byte[] function = {(byte) functionConfig.getNumber()};
        byte[] output = this.getMessageHandler().composeOutput(this.getNumber());
        byte[] state = functionConfig.getStateCalculator().convertSet(component, this.getState());
        return Bytes.concat(function, output, state);
    }

    @Override
    protected Command getCommand() {
        return Command.EVENT;
    }

    @Override
    protected String[] getPayloadLogInfo() {
        return new String[]{this.formatFunction(this.getFunction()), this.formatOutput(this.getNumber()), this.formatState(this.getFunction(), this.getNumber(), this.getState())};
    }

}
