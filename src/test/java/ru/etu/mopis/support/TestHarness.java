package ru.etu.mopis.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import ru.etu.mopis.audit.AuditEntry;
import ru.etu.mopis.audit.MemoryLog;
import ru.etu.mopis.command.CommandMessage;
import ru.etu.mopis.command.CommandResult;
import ru.etu.mopis.command.ProcessService;
import ru.etu.mopis.config.ApplicationModel;
import ru.etu.mopis.config.ConfigurationLoader;
import ru.etu.mopis.error.CommandOutcome;
import ru.etu.mopis.expr.EvalContext;
import ru.etu.mopis.fsm.ProcessEngine;

/**
 * Общая обвязка тестов: загрузка настоящей конфигурации варианта 1 из {@code config/},
 * сборка движка и сервиса с журналом в памяти ({@link MemoryLog}).
 *
 * <p>Тесты не содержат предметных данных в виде Java-кода: классы, объекты, роли,
 * состояния и условия приходят из YAML. Здесь только вызовы сервиса и проверки.</p>
 */
public final class TestHarness {

    /** Каталог конфигурации варианта 1 в корне проекта. */
    public static final Path CONFIG_DIR = Path.of("config");

    /** Имя демонстрационного сценария в каталоге конфигурации. */
    public static final String DEMO_FILE = "demo.yaml";

    private static final List<String> CONFIG_FILES =
            List.of("classes.yaml", "objects.yaml", "users.yaml", "process.yaml");

    private final ApplicationModel model;
    private final ProcessEngine engine;
    private final MemoryLog log;
    private final ProcessService service;

    private TestHarness(ApplicationModel model) {
        this.model = model;
        this.engine = new ProcessEngine(
                model.classes(), model.objects(), model.users(), model.templates());
        this.log = new MemoryLog();
        this.service = new ProcessService(engine, log);
    }

    /** Свежий запуск на штатной конфигурации варианта 1. */
    public static TestHarness standard() {
        return from(CONFIG_DIR);
    }

    /** Свежий запуск на произвольном каталоге конфигурации (например, временном). */
    public static TestHarness from(Path configDir) {
        return new TestHarness(new ConfigurationLoader().load(configDir));
    }

    /** Запуск на уже загруженной модели (например, собранной из изменённого YAML). */
    public static TestHarness of(ApplicationModel model) {
        return new TestHarness(model);
    }

    public ApplicationModel model() {
        return model;
    }

    public ProcessEngine engine() {
        return engine;
    }

    public MemoryLog log() {
        return log;
    }

    public ProcessService service() {
        return service;
    }

    public EvalContext ctx() {
        return engine.evalContext();
    }

    /** Текущее состояние процесса или {@code null}, если процесса нет. */
    public String state(String objectId) {
        return engine.stateOf(objectId);
    }

    public List<AuditEntry> entries() {
        return log.entries();
    }

    public AuditEntry lastEntry() {
        return log.last();
    }

    public CommandResult send(CommandMessage message) {
        return service.send(message);
    }

    public CommandResult start(String userId, String objectId) {
        return service.send(CommandMessage.start(userId, objectId));
    }

    public CommandResult action(String userId, String objectId, String actionId) {
        return service.send(CommandMessage.action(userId, objectId, actionId));
    }

    /** Выполняет действия по порядку и возвращает результат последнего. */
    public CommandResult actions(String userId, String objectId, String... actionIds) {
        CommandResult result = null;
        for (String actionId : actionIds) {
            result = action(userId, objectId, actionId);
        }
        return result;
    }

    public CommandResult sendAll(CommandMessage... messages) {
        CommandResult result = null;
        for (CommandMessage message : messages) {
            result = send(message);
        }
        return result;
    }

    // ------------------------------------------------------------------ проверки

    /** Команда принята, состояние процесса после неё — {@code expectedStateAfter}. */
    public static void assertAccepted(CommandResult result, String expectedStateAfter) {
        assertEquals(CommandOutcome.ACCEPTED, result.outcome(),
                () -> "ожидался accepted, получено " + result.outcome() + ": " + result.reason());
        assertNull(result.reason(), "у успешной команды причина отказа отсутствует");
        assertEquals(expectedStateAfter, result.stateAfter(), "состояние после команды");
    }

    /** Команда отклонена: результат rejected, непустая причина, состояние не изменилось. */
    public static void assertRejected(CommandResult result) {
        assertEquals(CommandOutcome.REJECTED, result.outcome(),
                () -> "ожидался rejected, получено " + result.outcome());
        assertFalse(result.reason() == null || result.reason().isBlank(),
                "причина отказа должна быть непустой");
        assertEquals(result.stateBefore(), result.stateAfter(), "отказ не меняет состояние процесса");
    }

    /** Команда отклонена, состояние процесса оставалось {@code expectedState}. */
    public static void assertRejected(CommandResult result, String expectedState) {
        assertRejected(result);
        assertEquals(expectedState, result.stateAfter(), "состояние после отказа");
    }

    // ------------------------------------------------------------------ временная конфигурация

    /**
     * Копирует четыре обязательных YAML-файла варианта 1 в {@code target},
     * чтобы тест мог заменить часть из них собственной версией.
     */
    public static void copyStandardConfig(Path target) throws IOException {
        Files.createDirectories(target);
        for (String file : CONFIG_FILES) {
            Files.copy(CONFIG_DIR.resolve(file), target.resolve(file),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static void write(Path file, String content) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    /** Сценарий демонстрации {@code config/demo.yaml}, прочитанный как список команд. */
    public static List<CommandMessage> demoCommands() {
        Path file = CONFIG_DIR.resolve(DEMO_FILE);
        ObjectMapper yaml = new ObjectMapper(new YAMLFactory());
        JsonNode root;
        try (InputStream in = Files.newInputStream(file)) {
            root = yaml.readTree(in);
        } catch (IOException e) {
            throw new IllegalStateException("не удалось прочитать " + file, e);
        }
        JsonNode list = root == null ? null : root.get("commands");
        if (list == null || !list.isArray()) {
            throw new IllegalStateException(file + ": ожидался список команд");
        }
        List<CommandMessage> commands = new ArrayList<>();
        for (JsonNode node : list) {
            commands.add(new CommandMessage(
                    node.get("type").asText(),
                    node.get("user").asText(),
                    node.get("object").asText(),
                    node.hasNonNull("action") ? node.get("action").asText() : null));
        }
        return commands;
    }
}
