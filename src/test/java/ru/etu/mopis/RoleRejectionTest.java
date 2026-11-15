package ru.etu.mopis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static ru.etu.mopis.support.TestHarness.assertAccepted;
import static ru.etu.mopis.support.TestHarness.assertRejected;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ru.etu.mopis.audit.AuditEntry;
import ru.etu.mopis.command.CommandResult;
import ru.etu.mopis.error.CommandOutcome;
import ru.etu.mopis.support.TestHarness;

/**
 * Сценарий T3: отказ пользователю без необходимой роли — как при запуске процесса
 * ({@code create_roles}), так и при выполнении действия в состоянии ({@code states[].roles}).
 * Каждый отказ не меняет состояние и попадает в журнал.
 */
@DisplayName("Отказы по ролям")
class RoleRejectionTest {

    @Test
    @DisplayName("T3: submit в editing отдаётся только роли developer")
    void developerRoleRequiredToSubmit() {
        TestHarness harness = TestHarness.standard();
        harness.start("ivanov", "cmp-1");
        harness.action("ivanov", "cmp-1", "declare");

        CommandResult rejected = harness.action("petrov", "cmp-1", "submit");
        assertRejected(rejected, "editing");
        assertEquals("editing", harness.state("cmp-1"), "состояние процесса не изменилось");
        assertTrue(harness.lastEntry().reason().contains("developer"),
                "причина указывает требуемую роль: " + harness.lastEntry().reason());
        assertEquals(CommandOutcome.REJECTED, harness.lastEntry().result());
        assertEquals("editing", harness.lastEntry().stateBefore());
        assertEquals("editing", harness.lastEntry().stateAfter());

        // Тот же переход доступен роли developer.
        assertAccepted(harness.action("ivanov", "cmp-1", "submit"), "review_kb");
    }

    @Test
    @DisplayName("T3: approve_kb в review_kb недоступен роли developer")
    void kbRoleRequiredToApprove() {
        TestHarness harness = TestHarness.standard();
        harness.start("ivanov", "cmp-1");
        harness.action("ivanov", "cmp-1", "declare");
        harness.action("ivanov", "cmp-1", "submit");

        assertRejected(harness.action("ivanov", "cmp-1", "approve_kb"), "review_kb");
        assertEquals("review_kb", harness.state("cmp-1"));
        assertAccepted(harness.action("petrov", "cmp-1", "approve_kb"), "review_to");
    }

    @Test
    @DisplayName("T3: allow в permission недоступен роли kb — требуется chief_designer")
    void chiefDesignerRoleRequiredToAllow() {
        TestHarness harness = TestHarness.standard();
        harness.start("ivanov", "cmp-1");
        harness.action("ivanov", "cmp-1", "declare");
        harness.action("ivanov", "cmp-1", "submit");
        harness.action("petrov", "cmp-1", "approve_kb");
        harness.action("sokolova", "cmp-1", "approve_to");
        harness.action("kuznetsov", "cmp-1", "find_suppliers");
        assertEquals("permission", harness.state("cmp-1"));

        CommandResult rejected = harness.action("petrov", "cmp-1", "allow");
        assertRejected(rejected, "permission");
        assertTrue(harness.lastEntry().reason().contains("chief_designer"),
                "причина указывает требуемую роль: " + harness.lastEntry().reason());

        assertAccepted(harness.action("sidorov", "cmp-1", "allow"), "allowed");
    }

    @Test
    @DisplayName("Start: create_roles ограничивает запуск процесса ролью developer")
    void startRequiresCreateRole() {
        TestHarness harness = TestHarness.standard();

        CommandResult rejected = harness.start("petrov", "cmp-1");
        assertRejected(rejected);
        assertNull(rejected.stateBefore(), "процесс не был создан");
        assertNull(rejected.stateAfter());
        assertNull(harness.state("cmp-1"), "процесс над объектом не появился");
        assertEquals(CommandOutcome.REJECTED, harness.lastEntry().result());
        assertTrue(harness.lastEntry().reason().contains("developer"),
                "причина указывает роль запуска: " + harness.lastEntry().reason());

        // Другая роль тоже не подходит.
        assertRejected(harness.start("sidorov", "cmp-1"));

        // После отказа процесс всё ещё можно запустить корректной ролью.
        assertAccepted(harness.start("ivanov", "cmp-1"), "declared");
    }

    @Test
    @DisplayName("Роли разных этапов не взаимозаменяемы, но пользователь с двумя ролями допускается")
    void rolesAreStageSpecific() {
        TestHarness harness = TestHarness.standard();
        harness.start("ivanov", "cmp-1");
        harness.action("ivanov", "cmp-1", "declare");
        harness.action("ivanov", "cmp-1", "submit");
        harness.action("petrov", "cmp-1", "approve_kb");

        // В review_to действует только роль to.
        assertRejected(harness.action("petrov", "cmp-1", "approve_to"), "review_to");
        assertRejected(harness.action("ivanov", "cmp-1", "approve_to"), "review_to");
        assertEquals("review_to", harness.state("cmp-1"));
        assertAccepted(harness.action("sokolova", "cmp-1", "approve_to"), "suppliers");

        // У sidorov две роли: omts и chief_designer — обе применимы на своих этапах.
        assertAccepted(harness.action("sidorov", "cmp-1", "find_suppliers"), "permission");
        assertEquals(2, harness.model().users().require("sidorov").roles().size());
        assertTrue(harness.model().users().require("sidorov").hasRole("omts"));
        assertTrue(harness.model().users().require("sidorov").hasRole("chief_designer"));
    }

    @Test
    @DisplayName("Каждый отказ по роли оставляет ровно одну запись журнала")
    void eachRoleRejectionIsLoggedOnce() {
        TestHarness harness = TestHarness.standard();
        harness.start("ivanov", "cmp-1");
        harness.action("ivanov", "cmp-1", "declare");

        int before = harness.log().size();
        harness.action("petrov", "cmp-1", "submit");
        harness.action("sokolova", "cmp-1", "submit");
        assertEquals(before + 2, harness.log().size(), "по записи на команду");

        for (AuditEntry entry : harness.entries().subList(before, harness.log().size())) {
            assertEquals("submit", entry.actionId());
            assertEquals(CommandOutcome.REJECTED, entry.result());
            assertEquals(entry.stateBefore(), entry.stateAfter());
            assertFalse(entry.reason().isBlank(), "причина непустая");
        }
        assertEquals("editing", harness.state("cmp-1"), "ни один отказ не изменил состояние");
    }
}
