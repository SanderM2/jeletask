package io.github.ridiekel.jeletask.client.builder.message.messages.impl;

import io.github.ridiekel.jeletask.client.TeletaskClientImpl;
import io.github.ridiekel.jeletask.client.builder.composer.MessageHandler;
import io.github.ridiekel.jeletask.client.builder.composer.MessageHandlerFactory;
import io.github.ridiekel.jeletask.client.builder.composer.config.configurables.FunctionConfigurable;
import io.github.ridiekel.jeletask.client.builder.message.MessageUtilities;
import io.github.ridiekel.jeletask.client.builder.message.messages.AcknowledgeException;
import io.github.ridiekel.jeletask.client.builder.message.messages.FunctionStateBasedMessageSupport;
import io.github.ridiekel.jeletask.model.spec.CentralUnit;
import io.github.ridiekel.jeletask.model.spec.Command;
import io.github.ridiekel.jeletask.model.spec.ComponentSpec;
import io.github.ridiekel.jeletask.model.spec.Function;
import io.github.ridiekel.jeletask.utilities.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class SetMessage extends FunctionStateBasedMessageSupport {
    /**
     * Logger responsible for logging and debugging statements.
     */
    private static final Logger LOG = LoggerFactory.getLogger(SetMessage.class);

    private final int number;

    public SetMessage(CentralUnit clientConfig, Function function, int number, String state) {
        super(clientConfig, function, state);
        this.number = number;
    }

    public int getNumber() {
        return this.number;
    }

    @Override
    protected byte[] getPayload() {
        FunctionConfigurable functionConfig = this.getMessageHandler().getFunctionConfig(this.getFunction());
        ComponentSpec component = this.getClientConfig().getComponent(this.getFunction(), this.getNumber());
        byte[] function = {(byte) functionConfig.getNumber()};
        byte[] output = MessageHandlerFactory.getMessageHandler(this.getClientConfig().getCentralUnitType()).composeOutput(this.getNumber());
        byte[] state = functionConfig.getStateCalculator().convertSet(component, this.getState());
        return Bytes.concat(function, output, state);
    }

    @Override
    public void execute(TeletaskClientImpl client) throws AcknowledgeException {
        super.execute(client);

        ComponentSpec component = this.getClientConfig().getComponent(this.getFunction(), this.getNumber());
        String initialState = component.getState();
        long start = System.currentTimeMillis();
        while (!this.getState().equals(component.getState()) && (System.currentTimeMillis() - start) < 2000) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                LOG.trace("Exception ({}) caught in set: {}", e.getClass().getName(), e.getMessage(), e);
            }
            try {
                client.handleReceiveEvents(MessageUtilities.receive(LOG, client));
            } catch (Exception e) {
                LOG.error("Exception ({}) caught in execute: {}", e.getClass().getName(), e.getMessage(), e);
            }
        }
        if (this.getFunction().shouldReceiveAcknowledge(this.getState()) && !this.getState().equals(component.getState())) {
            String message = "Did not receive a state change for " + component.getFunction() + ":" + component.getNumber() + " ("+component.getDescription()+") within 2 seconds. Assuming failed to set state from '" + initialState + "' to '" + this.getState() + "'";
            LOG.warn(message);
        }
    }

    @Override
    protected Command getCommand() {
        return Command.SET;
    }

    @Override
    protected String[] getPayloadLogInfo() {
        return new String[]{this.formatFunction(this.getFunction()), this.formatOutput(this.getNumber()), this.formatState(this.getFunction(), this.getNumber(), this.getState())};
    }

    @Override
    public List<EventMessage> respond(CentralUnit config, MessageHandler messageHandler) {
        return messageHandler.createResponseEventMessage(config, this.getFunction(), new MessageHandler.OutputState(this.getNumber(), this.getState()));
    }

    @Override
    protected String getId() {
        return "SET " + super.getId() + "(" + this.number + ")";
    }
}
