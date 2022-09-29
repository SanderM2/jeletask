package io.github.ridiekel.jeletask.client.builder.composer.config.statecalculator;

import io.github.ridiekel.jeletask.client.builder.composer.config.NumberConverter;
import io.github.ridiekel.jeletask.client.spec.ComponentSpec;
import io.github.ridiekel.jeletask.client.spec.state.ComponentState;
import io.github.ridiekel.jeletask.utilities.Bytes;

import java.util.List;

public class AurusStateCalculator extends MappingStateCalculator {

    public static final OnOffToggleStateCalculator AURUS_STATE_CALCULATOR = new OnOffToggleStateCalculator(NumberConverter.UNSIGNED_BYTE, 255, 0);

    private static final List<StateMapping> STATE_MAPPINGS = List.of(
            new StateMapping("MANUAL", 0),
            new StateMapping("UP", 21),
            new StateMapping("DOWN", 22),
            new StateMapping("MANUALTARGET", 87),
            new StateMapping("FROST", 24),
            new StateMapping("DAY", 26),
            new StateMapping("NIGHT",25 ),
            new StateMapping("STANDBY", 93),    // On the Aurus this mode is called "ECO" ?
//            new StateMapping("SETDAY", 29),
//            new StateMapping("SETSTANDBY", 88),
//            new StateMapping("SETNIGHT", 27),
//            new StateMapping("SETNIGHTCOOL", 56),
            new StateMapping("SPEED", 31),
            new StateMapping("SPLOW", 97),
            new StateMapping("SPMED", 98),
            new StateMapping("SPHIGH", 99),
            new StateMapping("SPAUTO", 89),
            new StateMapping("MODE", 30),
            new StateMapping("AUTO", 94),
            new StateMapping("HEAT", 95),
            new StateMapping("COOL", 96),
            new StateMapping("VENT", 105),
            new StateMapping("STOP", 106),
            new StateMapping("HEATP", 107), // What is Heat+ ?
            new StateMapping("DRY", 108),
            new StateMapping("ON", 255),
            new StateMapping("OFF", 0),
            new StateMapping("ONOFF", 104)
    );

    public AurusStateCalculator(NumberConverter numberConverter) {
        super(numberConverter, STATE_MAPPINGS.toArray(StateMapping[]::new));
    }

    @Override
    public ComponentState toComponentState(ComponentSpec component, byte[] dataBytes) {
        if (dataBytes.length < 17)
            return null;

        ComponentState state = new ComponentState(AURUS_STATE_CALCULATOR.toComponentState(component, new byte[]{dataBytes[12]}).getState());
        TemperatureStateCalculator tempcalculator = new TemperatureStateCalculator(NumberConverter.UNSIGNED_SHORT, 10, 273);

        state.setCurrentTemperature(Float.valueOf(tempcalculator.toComponentState(component, new byte[]{dataBytes[0], dataBytes[1]}).getState()));
        state.setTargetTemperature(Float.valueOf(tempcalculator.toComponentState(component, new byte[]{dataBytes[2], dataBytes[3]}).getState()));

        // Byte 4+5 seems to be the day preset temperature?
        // Byte 6+7 seems to be the night preset @heating temperature?
        // Byte 8 = Unknown (0x19 ?)

        state.setPreset(super.toComponentState(component, new byte[]{dataBytes[9]}).getState());
        state.setMode(super.toComponentState(component, new byte[]{dataBytes[10]}).getState());
        state.setFanspeed(super.toComponentState(component, new byte[]{dataBytes[11]}).getState());

        // Byte 12 = ON/OFF (already used, see above)

        // Byte 13 = Unknown (0x00 ?)
        // Byte 14 = Unknown (0x80 / 0x90 ?)
        // Byte 15 = Unknown (0x00 ?)
        // Byte 16+17 seems to be the night preset @cooling temperature?
        
        return state;
    }

    @Override
    public byte[] toBytes(ComponentState state) {
        byte[] setting = null;
        byte[] data = Bytes.EMPTY;

        if (state.getTargetTemperature() != null) {
            setting = super.toBytes(new ComponentState("MANUALTARGET"));
            try {
                int temperature = (int) state.getTargetTemperature();
                data = NumberConverter.UNSIGNED_SHORT.convert((temperature+273)*10);
            } catch (NumberFormatException e) {
                //Not a number, so no data is needed
            }
        }

        if (setting == null) {
            setting = super.toBytes(state);
        }

        return Bytes.concat(setting, data);
    }

    @Override
    public boolean isValidState(ComponentState state) {
        return state.getState() != null || super.isValidState(state);
    }


}
