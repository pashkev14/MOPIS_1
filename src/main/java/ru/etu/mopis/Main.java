package ru.etu.mopis;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import ru.etu.mopis.audit.JsonLinesLog;
import ru.etu.mopis.command.CommandMessage;
import ru.etu.mopis.command.CommandResult;
import ru.etu.mopis.command.ProcessService;
import ru.etu.mopis.config.ApplicationModel;
import ru.etu.mopis.config.ConfigurationLoader;
import ru.etu.mopis.error.ConfigurationException;
import ru.etu.mopis.error.RejectedException;
import ru.etu.mopis.fsm.ProcessEngine;
import ru.etu.mopis.type.Param;

/**
 * Точка входа: загрузка конфигурации, запуск сценария из {@code config/demo.yaml}
 * или одной команды из аргументов, вывод результата и запись JSONL-журнала.
 *
 * <p>Идентификаторов классов, состояний, действий и ролей конкретного варианта
 * в этом классе нет: сценарий и правила — данные конфигурации.</p>
 */
public final class Main {

    private static final Path DEFAULT_CONFIG_DIR = Path.of("config");
    private static final Path DEFAULT_LOG = Path.of("logs", "audit.jsonl");
    private static final String DEMO_FILE = "demo.yaml";

    private Main() {
    }

    public static void main(String[] args) {
        Path configDir = DEFAULT_CONFIG_DIR;
        Path logPath = DEFAULT_LOG;
        List<String> positional = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--config" -> {
                    if (++i >= args.length) {
                        System.err.println("не задан каталог для --config");
                        return;
                    }
                    configDir = Path.of(args[i]);
                }
                case "--log" -> {
                    if (++i >= args.length) {
                        System.err.println("не задан путь для --log");
                        return;
                    }
                    logPath = Path.of(args[i]);
                }
                default -> positional.add(args[i]);
            }
        }

        ApplicationModel model;
        try {
            model = new ConfigurationLoader().load(configDir);
        } catch (ConfigurationException e) {
            System.err.println("ошибка конфигурации: " + e.getMessage());
            return;
        }

        ProcessEngine engine = new ProcessEngine(
                model.classes(), model.objects(), model.users(), model.templates());

        List<CommandMessage> commands;
        if (positional.isEmpty()) {
            try {
                commands = readDemo(configDir.resolve(DEMO_FILE));
            } catch (ConfigurationException e) {
                System.err.println("ошибка сценария демонстрации: " + e.getMessage());
                return;
            }
        } else {
            try {
                commands = List.of(parseArguments(positional));
            } catch (RejectedException e) {
                System.err.println("некорректная команда: " + e.getMessage());
                return;
            }
        }

        try (JsonLinesLog audit = new JsonLinesLog(logPath, new ObjectMapper())) {
            ProcessService service = new ProcessService(engine, audit);
            printHeader(model, configDir);
            runScript(service, commands);
            System.out.println();
            System.out.println("Журнал: " + logPath.toAbsolutePath());
        }
    }

    /** Выполняет сценарий по порядку, печатая по строке на команду. */
    public static void runScript(ProcessService service, List<CommandMessage> commands) {
        int index = 0;
        for (CommandMessage command : commands) {
            index++;
            CommandResult result = service.send(command);
            System.out.println(formatLine(index, command, result));
        }
    }

    private static String formatLine(int index, CommandMessage command, CommandResult result) {
        StringBuilder line = new StringBuilder();
        line.append('[').append(index).append("] ").append(command.type()).append(' ')
                .append(command.userId()).append(' ').append(command.objectId());
        if (command.actionId() != null) {
            line.append(' ').append(command.actionId());
        }
        line.append(" -> ").append(result.outcome().wireName()).append(" | ").append(result.stateAfter());
        if (result.reason() != null) {
            line.append("   (").append(result.reason()).append(')');
        }
        line.append("   (состояние: ").append(result.stateBefore()).append(" -> ")
                .append(result.stateAfter()).append(')');
        return line.toString();
    }

    private static void printHeader(ApplicationModel model, Path configDir) {
        System.out.println("=== Движок исполнения процессов (конфигурация: " + configDir + ") ===");
        model.templates().all().values().forEach(machine ->
                System.out.println("Шаблон: " + machine.processId() + " — " + machine.title()
                        + " (класс " + machine.classId() + ", начальное состояние "
                        + machine.initialState().id() + ")"));
        System.out.println("Классы и действующие параметры (с учётом наследования):");
        for (String classId : model.classes().ids()) {
            Map<String, Param> params = model.classes().params(classId);
            StringBuilder line = new StringBuilder("  ").append(classId).append(": ");
            boolean first = true;
            for (Param param : params.values()) {
                if (!first) {
                    line.append(", ");
                }
                first = false;
                line.append(param.id()).append(':').append(param.type().wireName());
                param.min().ifPresent(min -> line.append(" [").append(min.toPlainString()));
                param.max().ifPresent(max -> line.append("..").append(max.toPlainString()));
                if (param.min().isPresent() || param.max().isPresent()) {
                    line.append(']');
                }
                if (param.hasDefault()) {
                    line.append(" default=").append(param.effectiveDefault());
                }
            }
            System.out.println(line);
        }
        System.out.println("Объектов загружено: " + model.objects().all().size());
        System.out.println();
    }

    private static CommandMessage parseArguments(List<String> tokens) {
        if (tokens.size() < 3) {
            throw new RejectedException("ожидается: start <пользователь> <объект> "
                    + "| action <пользователь> <объект> <действие>");
        }
        String type = tokens.getFirst().trim().toLowerCase(java.util.Locale.ROOT);
        String user = tokens.get(1);
        String object = tokens.get(2);
        return switch (type) {
            case CommandMessage.TYPE_START -> {
                if (tokens.size() != 3) {
                    throw new RejectedException("команда start принимает ровно два аргумента: пользователь и объект");
                }
                yield CommandMessage.start(user, object);
            }
            case CommandMessage.TYPE_ACTION -> {
                if (tokens.size() != 4) {
                    throw new RejectedException(
                            "команда action принимает три аргумента: пользователь, объект и действие");
                }
                yield CommandMessage.action(user, object, tokens.get(3));
            }
            default -> throw new RejectedException("неизвестный тип команды: " + tokens.getFirst());
        };
    }

    private static List<CommandMessage> readDemo(Path file) {
        if (!Files.isRegularFile(file)) {
            throw new ConfigurationException("файл сценария не найден: " + file.toAbsolutePath());
        }
        ObjectMapper yaml = new ObjectMapper(new YAMLFactory());
        JsonNode root;
        try (InputStream in = Files.newInputStream(file)) {
            root = yaml.readTree(in);
        } catch (IOException e) {
            throw new ConfigurationException("не удалось прочитать " + file + ": " + e.getMessage(), e);
        }
        JsonNode list = root == null ? null : root.get("commands");
        if (list == null || !list.isArray()) {
            throw new ConfigurationException(file + ": поле 'commands' должно быть списком");
        }

        List<CommandMessage> commands = new ArrayList<>();
        for (JsonNode node : list) {
            String type = text(node, "type");
            String user = text(node, "user");
            String object = text(node, "object");
            String action = text(node, "action");
            if (type == null || user == null || object == null) {
                throw new ConfigurationException(file + ": у команды не заданы type/user/object");
            }
            commands.add(new CommandMessage(type, user, object, action));
        }
        return commands;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text.isBlank() ? null : text;
    }
}
