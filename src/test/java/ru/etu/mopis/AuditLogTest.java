package ru.etu.mopis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static ru.etu.mopis.support.TestHarness.assertAccepted;
import static ru.etu.mopis.support.TestHarness.assertRejected;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ru.etu.mopis.audit.AuditEntry;
import ru.etu.mopis.audit.AuditLog;
import ru.etu.mopis.audit.JsonLinesLog;
import ru.etu.mopis.audit.MemoryLog;
import ru.etu.mopis.command.CommandMessage;
import ru.etu.mopis.command.CommandResult;
import ru.etu.mopis.command.ProcessService;
import ru.etu.mopis.error.CommandOutcome;
import ru.etu.mopis.support.TestHarness;

/**
 * Сценарии T3/T4/T10: журнал JSONL содержит ровно одну запись на каждую команду
 * (успех, отказ, ошибка) с обязательными полями, а отказ не меняет состояние процесса.
 */
@DisplayName("Журнал команд и неизменность состояния при отказе")
class AuditLogTest {

    @TempDir
    Path tempDir;

    private static final List<String> REQUIRED_FIELDS =
            List.of("command", "user", "object", "state_before", "state_after", "result", "reason");

    @Test
    @DisplayName("T10: каждая команда даёт ровно одну запись в MemoryLog")
    void oneEntryPerCommand() {
        TestHarness harness = TestHarness.standard();
        harness.start("ivanov", "cmp-1");
        harness.action("ivanov", "cmp-1", "declare");
        harness.action("petrov", "cmp-1", "submit");   // отказ по роли
        harness.action("ivanov", "cmp-1", "submit");   // успех

        assertEquals(4, harness.log().size(), "одна запись на каждую из четырёх команд");
        assertEquals(List.of(CommandOutcome.ACCEPTED, CommandOutcome.ACCEPTED,
                        CommandOutcome.REJECTED, CommandOutcome.ACCEPTED),
                harness.entries().stream().map(AuditEntry::result).toList());
    }

    @Test
    @DisplayName("T10: успешный start пишет state_before = null, state_after = начальное состояние")
    void successfulStartEntry() {
        TestHarness harness = TestHarness.standard();
        harness.start("ivanov", "cmp-1");

        AuditEntry entry = harness.lastEntry();
        assertEquals(CommandMessage.TYPE_START, entry.commandType());
        assertEquals("ivanov", entry.userId());
        assertEquals("cmp-1", entry.objectId());
        assertNull(entry.actionId(), "у start нет действия");
        assertNull(entry.stateBefore(), "прежнего состояния нет");
        assertEquals("declared", entry.stateAfter());
        assertEquals(CommandOutcome.ACCEPTED, entry.result());
        assertNull(entry.reason(), "у успешной команды причины нет");
    }

    @Test
    @DisplayName("T10: отказ и ошибка пишут state_before = state_after и непустую причину")
    void rejectionEntryKeepsState() {
        TestHarness harness = TestHarness.standard();
        harness.start("ivanov", "cmp-1");
        harness.action("ivanov", "cmp-1", "declare");

        harness.action("petrov", "cmp-1", "submit");
        AuditEntry rejected = harness.lastEntry();
        assertEquals(CommandOutcome.REJECTED, rejected.result());
        assertEquals("editing", rejected.stateBefore());
        assertEquals("editing", rejected.stateAfter());
        assertFalse(rejected.reason() == null || rejected.reason().isBlank());

        harness.action("ivanov", "cmp-1", "unknown_action");
        AuditEntry error = harness.lastEntry();
        assertEquals(CommandOutcome.REJECTED, error.result(),
                "неизвестное действие отклоняется проверкой");
        assertEquals(error.stateBefore(), error.stateAfter());
        assertFalse(error.reason().isBlank());
        assertEquals("editing", harness.state("cmp-1"), "состояние не изменилось");
    }

    @Test
    @DisplayName("T10: некорректная команда получает запись и понятную причину")
    void invalidCommandIsLogged() {
        TestHarness harness = TestHarness.standard();

        CommandResult noUser = harness.send(new CommandMessage("start", "", "cmp-1", null));
        assertRejected(noUser);
        assertNull(harness.state("cmp-1"));
        assertEquals(CommandOutcome.REJECTED, harness.lastEntry().result());

        CommandResult noObject = harness.send(new CommandMessage("action", "ivanov", null, "declare"));
        assertRejected(noObject);

        CommandResult unknownType = harness.send(new CommandMessage("jump", "ivanov", "cmp-1", null));
        assertEquals(CommandOutcome.ERROR, unknownType.outcome(),
                "неизвестный тип команды — ошибка обработки");
        assertFalse(unknownType.reason().isBlank());

        CommandResult unknownObject = harness.start("ivanov", "no-such-object");
        assertRejected(unknownObject);
        assertNull(unknownObject.stateBefore(), "объект неизвестен — состояния нет");

        assertEquals(4, harness.log().size(), "каждая команда записана, включая некорректные");
        assertNull(harness.state("cmp-1"), "ни одна команда не запустила процесс");
    }

    @Test
    @DisplayName("T10: JsonLinesLog пишет по строке на команду, файл читается Jackson")
    void jsonLinesLogFormat() throws IOException {
        TestHarness source = TestHarness.standard();
        Path logFile = tempDir.resolve("audit.jsonl");

        try (JsonLinesLog audit = new JsonLinesLog(logFile, new ObjectMapper())) {
            ProcessService service = new ProcessService(source.engine(), audit);
            service.send(CommandMessage.start("ivanov", "cmp-1"));
            service.send(CommandMessage.action("ivanov", "cmp-1", "declare"));
            service.send(CommandMessage.action("petrov", "cmp-1", "submit"));
        }

        List<String> lines = Files.readAllLines(logFile, StandardCharsets.UTF_8).stream()
                .filter(line -> !line.isBlank())
                .toList();
        assertEquals(3, lines.size(), "одна JSON-строка на команду");

        ObjectMapper mapper = new ObjectMapper();
        for (String line : lines) {
            JsonNode node = mapper.readTree(line);
            for (String field : REQUIRED_FIELDS) {
                assertTrue(node.has(field), "в записи есть поле '" + field + "': " + line);
            }
            assertTrue(node.get("command").has("type"), "вложенная команда содержит type");
            assertTrue(node.get("command").has("user"));
            assertTrue(node.get("command").has("object"));
        }

        JsonNode startEntry = mapper.readTree(lines.get(0));
        assertTrue(startEntry.get("state_before").isNull(), "у start прежнее состояние null");
        assertEquals("declared", startEntry.get("state_after").asText());
        assertEquals("accepted", startEntry.get("result").asText());
        assertTrue(startEntry.get("reason").isNull());

        JsonNode rejectedEntry = mapper.readTree(lines.get(2));
        assertEquals("action", rejectedEntry.get("command").get("type").asText());
        assertEquals("submit", rejectedEntry.get("command").get("action").asText());
        assertEquals("rejected", rejectedEntry.get("result").asText());
        assertEquals("editing", rejectedEntry.get("state_before").asText());
        assertEquals("editing", rejectedEntry.get("state_after").asText());
        assertFalse(rejectedEntry.get("reason").asText().isBlank());

        // Порядок полей фиксирован спецификацией §5.5.
        Iterator<String> names = mapper.readTree(lines.get(1)).fieldNames();
        assertEquals(REQUIRED_FIELDS, java.util.stream.StreamSupport
                .stream(((Iterable<String>) () -> names).spliterator(), false).toList());
    }

    @Test
    @DisplayName("T10: число записей журнала равно числу обработанных команд сценария")
    void logSizeMatchesCommandCount() {
        TestHarness harness = TestHarness.standard();
        List<CommandMessage> commands = TestHarness.demoCommands();

        MemoryLog log = harness.log();
        for (CommandMessage command : commands) {
            harness.send(command);
        }
        assertEquals(commands.size(), log.size());

        long accepted = log.entries().stream()
                .filter(entry -> entry.result() == CommandOutcome.ACCEPTED).count();
        long rejected = log.entries().stream()
                .filter(entry -> entry.result() == CommandOutcome.REJECTED).count();
        assertEquals(log.size(), accepted + rejected, "других исходов в сценарии нет");

        for (AuditEntry entry : log.entries()) {
            assertNotNull(entry.commandType(), "тип команды всегда известен");
            assertNotNull(entry.userId());
            assertNotNull(entry.objectId());
            assertNotNull(entry.result());
            if (entry.result() != CommandOutcome.ACCEPTED) {
                assertFalse(entry.reason() == null || entry.reason().isBlank(),
                        "у отказа непустая причина: " + entry);
                assertEquals(entry.stateBefore(), entry.stateAfter(),
                        "отказ не меняет состояние: " + entry);
            }
        }
    }

    @Test
    @DisplayName("Отказ не меняет состояние: серия отказов оставляет процесс на месте")
    void repeatedRejectionsDoNotMoveState() {
        TestHarness harness = TestHarness.standard();
        harness.start("ivanov", "cmp-1");
        harness.action("ivanov", "cmp-1", "declare");

        for (int i = 0; i < 3; i++) {
            assertRejected(harness.action("kuznetsov", "cmp-1", "submit"), "editing");
        }
        assertEquals("editing", harness.state("cmp-1"));

        // Действие вне текущего состояния — тоже отказ без изменения состояния.
        assertRejected(harness.action("ivanov", "cmp-1", "find_suppliers"), "editing");
        assertEquals("editing", harness.state("cmp-1"));

        assertAccepted(harness.action("ivanov", "cmp-1", "submit"), "review_kb");
    }

    @Test
    @DisplayName("MemoryLog подменяет AuditLog без изменения обработчика")
    void memoryLogIsInjected() {
        TestHarness harness = TestHarness.standard();
        AuditLog injected = harness.log();
        assertTrue(injected instanceof MemoryLog, "журнал в памяти реализует интерфейс AuditLog");

        harness.start("ivanov", "cmp-1");
        assertEquals(1, harness.log().entries().size(), "запись попала в подставленный журнал");
        assertEquals("declared", harness.log().last().stateAfter());
    }
}
