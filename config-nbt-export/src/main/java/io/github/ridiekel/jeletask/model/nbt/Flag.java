package io.github.ridiekel.jeletask.model.nbt;

import io.github.ridiekel.jeletask.model.spec.Function;

public class Flag extends ComponentSupport {
    public Flag(int id, Room room, String type, String description) {
        super(id, room, type, description);
    }

    @Override
    public Function getFunction() {
        return Function.FLAG;
    }
}
