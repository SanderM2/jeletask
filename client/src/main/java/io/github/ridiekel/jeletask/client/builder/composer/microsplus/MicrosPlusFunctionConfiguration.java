package io.github.ridiekel.jeletask.client.builder.composer.microsplus;

import io.github.ridiekel.jeletask.client.builder.composer.config.ConfigurationSupport;
import io.github.ridiekel.jeletask.client.builder.composer.config.NumberConverter;
import io.github.ridiekel.jeletask.client.builder.composer.config.configurables.FunctionConfigurable;
import io.github.ridiekel.jeletask.client.builder.composer.config.statecalculator.*;
import io.github.ridiekel.jeletask.client.spec.Function;

import java.util.List;

public class MicrosPlusFunctionConfiguration extends ConfigurationSupport<Function, FunctionConfigurable, Integer> {
    private static final StateCalculator ON_OFF_TOGGLE = new OnOffToggleStateCalculator(NumberConverter.UNSIGNED_BYTE, 255, 0, 103);

    public MicrosPlusFunctionConfiguration() {
        super(List.of(
                new FunctionConfigurable(Function.RELAY, 1, ON_OFF_TOGGLE),
                new FunctionConfigurable(Function.DIMMER, 2, new DimmerStateCalculator(NumberConverter.UNSIGNED_BYTE)),
                new FunctionConfigurable(Function.MOTOR, 6, new MotorStateCalculator(NumberConverter.UNSIGNED_BYTE)),
                new FunctionConfigurable(Function.LOCMOOD, 8, ON_OFF_TOGGLE),
                new FunctionConfigurable(Function.TIMEDMOOD, 9, ON_OFF_TOGGLE),
                new FunctionConfigurable(Function.GENMOOD, 10, ON_OFF_TOGGLE),
                new FunctionConfigurable(Function.FLAG, 15, ON_OFF_TOGGLE),
                new FunctionConfigurable(Function.SENSOR, 20, new SensorStateCalculator(
                        new TemperatureStateCalculator(NumberConverter.UNSIGNED_SHORT, 10, 273),
                        new LuxStateCalculator(NumberConverter.UNSIGNED_SHORT),
                        new HumidityStateCalculator(NumberConverter.UNSIGNED_SHORT),
                        new GasStateCalculator(NumberConverter.UNSIGNED_SHORT),
                        new TemperatureControlStateCalculator(NumberConverter.UNSIGNED_BYTE, 10, 273),
                        new PulseCounterStateCalculator(NumberConverter.UNSIGNED_INT)
                )),
                new FunctionConfigurable(Function.COND, 60, ON_OFF_TOGGLE),
                new FunctionConfigurable(Function.INPUT, 52, new InputStateCalculator(NumberConverter.UNSIGNED_SHORT)),
                new FunctionConfigurable(Function.TIMEDFNC, 5, ON_OFF_TOGGLE),
                new FunctionConfigurable(Function.DISPLAYMESSAGE, 54, new DisplayMessageStateCalculator(NumberConverter.UNSIGNED_BYTE))            
        ));
    }

    @Override
    protected Integer getKey(FunctionConfigurable configurable) {
        return configurable.getNumber();
    }
}
