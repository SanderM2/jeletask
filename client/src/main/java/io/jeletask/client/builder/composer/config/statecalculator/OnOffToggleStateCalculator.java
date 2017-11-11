package io.jeletask.client.builder.composer.config.statecalculator;

import io.jeletask.client.builder.composer.config.NumberConverter;

public class OnOffToggleStateCalculator extends MappingStateCalculator {
    public OnOffToggleStateCalculator(NumberConverter numberConverter, Number on, Number off, Number toggle) {
        super(numberConverter, new StateMapping("ON", on), new StateMapping("OFF", off), new StateMapping("TOGGLE", toggle));
    }
}
