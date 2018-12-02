package io.github.ridiekel.jeletask.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.ridiekel.jeletask.model.nbt.NbtCentralUnit;
import io.github.ridiekel.jeletask.parser.handler.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.util.Map.entry;

public class NbtFile {
    private static final NbtFile INSTANCE = new NbtFile();

    private static final Map<Pattern, LineHandler> LINE_HANDLER_MAP = Map.ofEntries(
            entry(RoomLineHandler.getInstance().getStartPattern(), RoomLineHandler.getInstance()),
            entry(OutputInterfaceLineHandler.getInstance().getStartPattern(), OutputInterfaceLineHandler.getInstance()),
            entry(InputInterfaceLineHandler.getInstance().getStartPattern(), InputInterfaceLineHandler.getInstance()),
            entry(CentralUnitLineHandler.getInstance().getStartPattern(), CentralUnitLineHandler.getInstance()),
            entry(InputLineHandler.getInstance().getStartPattern(), InputLineHandler.getInstance()),
            entry(RelayLineHandler.getInstance().getStartPattern(), RelayLineHandler.getInstance()),
            entry(LocalMoodLineHandler.getInstance().getStartPattern(), LocalMoodLineHandler.getInstance()),
            entry(MotorLineHandler.getInstance().getStartPattern(), MotorLineHandler.getInstance()),
            entry(GeneralMoodLineHandler.getInstance().getStartPattern(), GeneralMoodLineHandler.getInstance()),
            entry(DimmerLineHandler.getInstance().getStartPattern(), DimmerLineHandler.getInstance()),
            entry(FlagsLineHandler.getInstance().getStartPattern(), FlagsLineHandler.getInstance()),
            entry(ConditionLineHandler.getInstance().getStartPattern(), ConditionLineHandler.getInstance()),
            entry(SensorLineHandler.getInstance().getStartPattern(), SensorLineHandler.getInstance())
    );

    private NbtFile() {
    }

    public static NbtCentralUnit read(InputStream nbtFile) throws IOException {
        InterestingNbtConsumerImpl consumer = new InterestingNbtConsumerImpl();
        getInstance().visit(consumer, nbtFile);
        return consumer.getCentralUnit();
    }

    public static NbtCentralUnit readFully(InputStream nbtFile) throws IOException {
        FullNbtConsumerImpl consumer = new FullNbtConsumerImpl();
        getInstance().visit(consumer, nbtFile);
        return consumer.getCentralUnit();
    }

    private void visit(NbtConsumer consumer, final InputStream stream) throws IOException {
        List<String> lines = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).lines().collect(Collectors.toList());
        for (ListIterator<String> iterator = lines.listIterator(); iterator.hasNext(); ) {
            String line = iterator.next();
            LineHandler handler = this.getLineHandler(line);
            if (handler != null) {
                handler.handle(line, consumer, iterator);
            }
        }
    }

    private LineHandler getLineHandler(String line) {
        LineHandler handler = null;
        for (Iterator<Map.Entry<Pattern, LineHandler>> iterator = LINE_HANDLER_MAP.entrySet().iterator(); iterator.hasNext() && handler == null; ) {
            Map.Entry<Pattern, LineHandler> entry = iterator.next();
            Matcher matcher = entry.getKey().matcher(line);
            if (matcher.matches()) {
                handler = entry.getValue();
            }
        }
        return handler;
    }

    public static NbtFile getInstance() {
        return INSTANCE;
    }

    public static void main(String[] args) throws IOException {
//        FullProprietaryModelConsumerImpl consumer = new FullProprietaryModelConsumerImpl();
        InterestingNbtConsumerImpl consumer = new InterestingNbtConsumerImpl();
        getInstance().visit(consumer, new FileInputStream("/home/geroen/Projects/git/home/jeletask/config-nbt-export/src/main/resources/centrale.ttt"));
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(System.out, consumer.getCentralUnit());
    }
}
