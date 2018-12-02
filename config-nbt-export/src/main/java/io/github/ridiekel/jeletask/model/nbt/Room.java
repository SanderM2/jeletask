package io.github.ridiekel.jeletask.model.nbt;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.github.ridiekel.jeletask.model.spec.RoomSpec;

import java.util.ArrayList;
import java.util.List;

@JsonSerialize(as=RoomSpec.class)
public class Room implements RoomSpec {
    private final int id;
    private final String name;
    private List<Relay> relays;
    private List<LocalMood> localMoods;
    private List<Flag> flags;
    private List<Motor> motors;
    private List<GeneralMood> generalMoods;
    private List<Dimmer> dimmers;
    private List<Condition> conditions;
    private List<Sensor> sensors;

    public Room(int id, String name) {
        this.id = id;
        this.name = name;
    }

    @Override
    public int getId() {
        return this.id;
    }

    @Override
    public String getName() {
        return this.name;
    }

    public List<Flag> getFlags() {
        if (flags == null) {
            flags = new ArrayList<>();
        }
        return flags;
    }

    @Override
    public List<Relay> getRelays() {
        if (this.relays == null) {
            this.setRelays(new ArrayList<>());
        }
        return this.relays;
    }

    @Override
    public List<LocalMood> getLocalMoods() {
        if (this.localMoods == null) {
            this.setLocalMoods(new ArrayList<>());
        }
        return this.localMoods;
    }

    @Override
    public List<Motor> getMotors() {
        if (this.motors == null) {
            this.setMotors(new ArrayList<>());
        }
        return this.motors;
    }

    @Override
    public List<GeneralMood> getGeneralMoods() {
        if (this.generalMoods == null) {
            this.setGeneralMoods(new ArrayList<>());
        }
        return this.generalMoods;
    }

    @Override
    public List<Dimmer> getDimmers() {
        if (this.dimmers == null) {
            this.setDimmers(new ArrayList<>());
        }
        return this.dimmers;
    }

    @Override
    public List<Condition> getConditions() {
        if (this.conditions == null) {
            this.setConditions(new ArrayList<>());
        }
        return this.conditions;
    }

    @Override
    public List<Sensor> getSensors() {
        if (this.sensors == null) {
            this.setSensors(new ArrayList<>());
        }
        return this.sensors;
    }

    public void setSensors(List<Sensor> sensors) {
        this.sensors = sensors;
    }

    public void setConditions(List<Condition> conditions) {
        this.conditions = conditions;
    }

    public void setDimmers(List<Dimmer> dimmers) {
        this.dimmers = dimmers;
    }

    public void setGeneralMoods(List<GeneralMood> generalMoods) {
        this.generalMoods = generalMoods;
    }

    private void setMotors(List<Motor> motors) {
        this.motors = motors;
    }

    private void setLocalMoods(List<LocalMood> localMoods) {
        this.localMoods = localMoods;
    }

    private void setRelays(List<Relay> relays) {
        this.relays = relays;
    }
}
