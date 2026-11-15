package ru.etu.mopis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static ru.etu.mopis.support.TestHarness.assertAccepted;
import static ru.etu.mopis.support.TestHarness.assertRejected;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ru.etu.mopis.audit.AuditEntry;
import ru.etu.mopis.command.CommandMessage;
import ru.etu.mopis.command.CommandResult;
import ru.etu.mopis.error.CommandOutcome;
import ru.etu.mopis.support.TestHarness;

/**
 * Сценарии T1, T2, T8: запуск процесса, основной маршрут варианта 1 до результатного
 * состояния {@code allowed}, возврат на предыдущий этап и повторный запуск.
 *
 * <p>Ожидания соответствуют таблице §7 {@code docs/spec.md} и рисунку 1 задания.
 * Конечных состояний в конфигурации нет: результат процесса — {@code allowed} или
 * {@code forbidden}, из которых доступно подтверждение.</p>
 */
@DisplayName("Основной маршрут процесса варианта 1")
class MainRouteTest {

    @Test
    @DisplayName("T1: путь declared → allowed за 7 команд, затем подтверждение")
    void happyPathReachesAllowed() {
        TestHarness harness = TestHarness.standard();

        assertNull(harness.state("cmp-1"), "до запуска процесса нет");

        CommandResult start = harness.start("ivanov", "cmp-1");
        assertAccepted(start, "declared");
        assertNull(start.stateBefore(), "у успешного запуска прежнего состояния нет");
        assertEquals("declared", harness.state("cmp-1"));

        assertAccepted(harness.action("ivanov", "cmp-1", "declare"), "editing");
        assertAccepted(harness.action("ivanov", "cmp-1", "submit"), "review_kb");
        assertAccepted(harness.action("petrov", "cmp-1", "approve_kb"), "review_to");
        assertAccepted(harness.action("sokolova", "cmp-1", "approve_to"), "suppliers");
        assertAccepted(harness.action("kuznetsov", "cmp-1", "find_suppliers"), "permission");
        assertAccepted(harness.action("sidorov", "cmp-1", "allow"), "allowed");
        assertEquals("allowed", harness.state("cmp-1"), "результат процесса — allowed");

        // allowed не конечное: подтверждение возвращает в permission.
        assertAccepted(harness.action("sidorov", "cmp-1", "confirm_allow"), "permission");
        assertEquals("permission", harness.state("cmp-1"));

        assertEquals(8, harness.log().size(), "ровно одна запись журнала на команду");
    }

    @Test
    @DisplayName("T8: повторный запуск процесса отклоняется и не меняет состояние")
    void repeatedStartIsRejected() {
        TestHarness harness = TestHarness.standard();
        harness.start("ivanov", "cmp-1");

        CommandResult repeated = harness.start("ivanov", "cmp-1");
        assertRejected(repeated, "declared");
        assertEquals("declared", harness.state("cmp-1"), "состояние осталось прежним");
        assertEquals("declared", harness.lastEntry().stateBefore(),
                "при отказе в повторном запуске прежнее состояние — текущее");
        assertEquals("declared", harness.lastEntry().stateAfter());
    }

    @Test
    @DisplayName("T8: повторный запуск отклоняется и из результатного состояния allowed")
    void repeatedStartRejectedFromAllowed() {
        TestHarness harness = TestHarness.standard();
        harness.start("ivanov", "cmp-1");
        harness.action("ivanov", "cmp-1", "declare");
        harness.action("ivanov", "cmp-1", "submit");
        harness.action("petrov", "cmp-1", "approve_kb");
        harness.action("sokolova", "cmp-1", "approve_to");
        harness.action("kuznetsov", "cmp-1", "find_suppliers");
        harness.action("sidorov", "cmp-1", "allow");
        assertEquals("allowed", harness.state("cmp-1"));

        assertRejected(harness.start("ivanov", "cmp-1"), "allowed");
        assertEquals("allowed", harness.lastEntry().stateBefore());
        assertEquals("allowed", harness.lastEntry().stateAfter());
    }

    @Test
    @DisplayName("T2: return_kb из review_kb возвращает в editing")
    void returnFromKbGoesBackToEditing() {
        TestHarness harness = TestHarness.standard();
        harness.start("ivanov", "cmp-1");
        harness.action("ivanov", "cmp-1", "declare");
        harness.action("ivanov", "cmp-1", "submit");
        assertEquals("review_kb", harness.state("cmp-1"));

        CommandResult returned = harness.action("petrov", "cmp-1", "return_kb");
        assertAccepted(returned, "editing");
        assertEquals("review_kb", returned.stateBefore(), "возврат выполнен из review_kb");
        assertEquals("editing", harness.state("cmp-1"));
        assertEquals("accepted", harness.lastEntry().result().wireName());

        // После возврата маршрут повторяем: замечания КБ не блокируют повторную передачу.
        assertAccepted(harness.action("ivanov", "cmp-1", "submit"), "review_kb");
    }

    @Test
    @DisplayName("T2: return_to из review_to возвращает в editing")
    void returnFromToGoesBackToEditing() {
        TestHarness harness = TestHarness.standard();
        harness.start("ivanov", "cmp-1");
        harness.action("ivanov", "cmp-1", "declare");
        harness.action("ivanov", "cmp-1", "submit");
        harness.action("petrov", "cmp-1", "approve_kb");
        assertEquals("review_to", harness.state("cmp-1"));

        CommandResult returned = harness.action("sokolova", "cmp-1", "return_to");
        assertAccepted(returned, "editing");
        assertEquals("review_to", returned.stateBefore());
        assertEquals("editing", harness.state("cmp-1"));
        assertEquals("editing", harness.lastEntry().stateAfter());
    }

    @Test
    @DisplayName("Маршрут через requirements: not produced, цикл отказа поставщика, согласование")
    void notProducedRouteWithSupplierLoop() {
        TestHarness harness = TestHarness.standard();
        harness.start("ivanov", "cmp-custom");
        harness.action("ivanov", "cmp-custom", "declare");
        harness.action("ivanov", "cmp-custom", "submit");
        harness.action("petrov", "cmp-custom", "approve_kb");
        harness.action("sokolova", "cmp-custom", "approve_to");

        // not produced — вторая ветка выбора поставщиков.
        CommandResult noProduction = harness.action("kuznetsov", "cmp-custom", "no_production");
        assertAccepted(noProduction, "requirements");

        CommandResult refused = harness.action("kuznetsov", "cmp-custom", "supplier_refused");
        assertAccepted(refused, "requirements");
        assertEquals("requirements", refused.stateBefore(), "переход в себя");

        CommandResult agreed = harness.action("kuznetsov", "cmp-custom", "supplier_agreed");
        assertAccepted(agreed, "permission");

        // Вторая группа ДНФ: НЕ produced И supplier_approved.
        assertAccepted(harness.action("sidorov", "cmp-custom", "allow"), "allowed");
        assertEquals("allowed", harness.state("cmp-custom"));
    }

    @Test
    @DisplayName("T1 (полный): демонстрационный сценарий demo.yaml проходит целиком")
    void demoScenarioRunsEndToEnd() {
        TestHarness harness = TestHarness.standard();
        List<CommandMessage> commands = TestHarness.demoCommands();
        assertTrue(commands.size() >= 30, "сценарий демонстрации содержит все шаги: " + commands.size());

        for (CommandMessage command : commands) {
            CommandResult result = harness.send(command);
            assertNotNull(result, "каждая команда возвращает результат");
            assertNotNull(harness.lastEntry(), "каждая команда пишет запись журнала");
        }

        assertEquals(commands.size(), harness.log().size(), "одна запись журнала на команду");
        assertEquals("allowed", harness.state("cmp-1"), "основной маршрут дошёл до allowed");
        assertEquals("permission", harness.state("cmp-custom"),
                "второй маршрут прошёл allowed и подтверждён обратно в permission");
        assertEquals("suppliers", harness.state("cmp-explicit-null"),
                "cmp-explicit-null остановлен на suppliers: suppliers_count = null → has_suppliers ложно");
        assertEquals("declared", harness.state("cmp-undefined"),
                "cmp-undefined не прошёл declare без обозначения");
        assertEquals("editing", harness.state("cmp-heavy"),
                "cmp-heavy не прошёл submit из-за переопределённого диапазона");

        long rejected = harness.entries().stream()
                .filter(entry -> entry.result() == CommandOutcome.REJECTED)
                .count();
        assertTrue(rejected >= 6, "в сценарии есть отказы по ролям и условиям: " + rejected);
    }

    @Test
    @DisplayName("Журнал содержит ровно один accepted на каждое изменение состояния")
    void acceptedEntriesCarryStateTransition() {
        TestHarness harness = TestHarness.standard();
        harness.start("ivanov", "cmp-1");
        harness.action("ivanov", "cmp-1", "declare");

        AuditEntry startEntry = harness.entries().get(0);
        assertEquals(CommandMessage.TYPE_START, startEntry.commandType());
        assertEquals("ivanov", startEntry.userId());
        assertEquals("cmp-1", startEntry.objectId());
        assertEquals("declared", startEntry.stateAfter());

        AuditEntry declareEntry = harness.entries().get(1);
        assertEquals(CommandMessage.TYPE_ACTION, declareEntry.commandType());
        assertEquals("declare", declareEntry.actionId());
        assertEquals("declared", declareEntry.stateBefore());
        assertEquals("editing", declareEntry.stateAfter());
        assertEquals(CommandOutcome.ACCEPTED, declareEntry.result());
    }
}
