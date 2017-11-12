package io.github.ridiekel.jeletask.model.nbt;

import io.github.ridiekel.jeletask.model.spec.Function;

public class LocalMood extends ComponentSupport {
    public LocalMood(int id, Room room, String type, String description) {
        super(id, room, type, description);
    }

    @Override
    public Function getFunction() {
        return Function.LOCMOOD;
    }
}
