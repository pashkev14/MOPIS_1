package ru.etu.mopis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static ru.etu.mopis.support.TestHarness.assertAccepted;
import static ru.etu.mopis.support.TestHarness.assertRejected;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ru.etu.mopis.expr.And;
import ru.etu.mopis.expr.ClassPredicate;
import ru.etu.mopis.expr.Not;
import ru.etu.mopis.expr.Or;
import ru.etu.mopis.expr.ParamPredicate;
import ru.etu.mopis.fsm.StateMachine;
import ru.etu.mopis.fsm.Transition;
import ru.etu.mopis.support.TestHarness;
import ru.etu.mopis.type.Obj;

/**
 * Сценарий T4: отказ при невыполненном условии перехода и проверка всех форм ДНФ —
 * «И» (дизъюнкт), «ИЛИ» (между дизъюнктами), отрицание {@code not}, {@code in_range}
 * и предикат класса.
 *
 * <p>Условия берутся из {@code config/process.yaml}: изменение условий требует правки
 * только конфигурации, код тестов и движка не меняется.</p>
 */
@DisplayName("Условия переходов в ДНФ")
class DnfConditionTest {

    @TempDir
    Path tempDir;

    // ------------------------------------------------------------------ «И»

    @Test
    @DisplayName("AND: find_suppliers требует одновременно produced и has_suppliers")
    void andConditionRequiresBothPredicates() {
        TestHarness harness = TestHarness.standard();
        harness.start("ivanov", "cmp-explicit-null");
        harness.action("ivanov", "cmp-explicit-null", "declare");
        harness.action("ivanov", "cmp-explicit-null", "submit");
        harness.action("petrov", "cmp-explicit-null", "approve_kb");
        harness.action("sokolova", "cmp-explicit-null", "approve_to");
        assertEquals("suppliers", harness.state("cmp-explicit-null"));

        // produced = "yes", но suppliers_count = null → has_suppliers ложно.
        assertRejected(harness.action("kuznetsov", "cmp-explicit-null", "find_suppliers"),
                "suppliers");
        assertEquals("suppliers", harness.state("cmp-explicit-null"),
                "отказ по условию не меняет состояние");
        assertTrue(harness.lastEntry().reason().contains("условие"),
                "причина отказа: " + harness.lastEntry().reason());

        // У cmp-1 оба предиката истинны — переход выполняется.
        harness.start("ivanov", "cmp-1");
        harness.action("ivanov", "cmp-1", "declare");
        harness.action("ivanov", "cmp-1", "submit");
        harness.action("petrov", "cmp-1", "approve_kb");
        harness.action("sokolova", "cmp-1", "approve_to");
        assertAccepted(harness.action("kuznetsov", "cmp-1", "find_suppliers"), "permission");
    }

    @Test
    @DisplayName("AND: submit требует designation_filled и mass_in_range")
    void andConditionCoversFilledAndRange() {
        TestHarness harness = TestHarness.standard();

        // Обозначение задано, масса 0.35 в диапазоне — переход выполняется.
        harness.start("ivanov", "cmp-1");
        harness.action("ivanov", "cmp-1", "declare");
        assertAccepted(harness.action("ivanov", "cmp-1", "submit"), "review_kb");

        // Обозначение не задано — тот же переход отклоняется.
        harness.start("ivanov", "cmp-undefined");
        assertRejected(harness.action("ivanov", "cmp-undefined", "declare"), "declared");
    }

    // ------------------------------------------------------------------ «ИЛИ»

    @Test
    @DisplayName("OR: approve_to выполняется по первому дизъюнкту (tech_approved)")
    void orConditionFirstDisjunct() {
        TestHarness harness = TestHarness.standard();
        harness.start("ivanov", "cmp-1");
        harness.action("ivanov", "cmp-1", "declare");
        harness.action("ivanov", "cmp-1", "submit");
        harness.action("petrov", "cmp-1", "approve_kb");

        // cmp-1: tech_process_approved = "yes", масса 0.35 — истинны оба дизъюнкта.
        assertAccepted(harness.action("sokolova", "cmp-1", "approve_to"), "suppliers");
    }

    @Test
    @DisplayName("OR: approve_to выполняется по второму дизъюнкту (mass_le_10) при несогласованной технологии")
    void orConditionSecondDisjunct() throws IOException {
        // Объект с mass = 5 (второй дизъюнкт истинен) и tech_process_approved = "no".
        Path config = configWithExtraObject(String.join("\n",
                "  - id: cmp-light-no-tech",
                "    class: component",
                "    values:",
                "      designation: АБВГ.757412.101",
                "      mass: 5",
                "      is_produced: \"yes\"",
                "      suppliers_count: 2",
                "      tech_process_approved: \"no\"",
                ""));
        TestHarness harness = TestHarness.from(config);

        harness.start("ivanov", "cmp-light-no-tech");
        harness.action("ivanov", "cmp-light-no-tech", "declare");
        harness.action("ivanov", "cmp-light-no-tech", "submit");
        harness.action("petrov", "cmp-light-no-tech", "approve_kb");

        Transition approveTo = singleTransition(harness, "review_to", "approve_to");
        Obj obj = harness.model().objects().require("cmp-light-no-tech");
        assertTrue(approveTo.isApplicable(obj, harness.ctx()),
                "первый дизъюнкт ложен, второй истинен");

        assertAccepted(harness.action("sokolova", "cmp-light-no-tech", "approve_to"), "suppliers");
    }

    @Test
    @DisplayName("OR: оба дизъюнкта ложны → отказ, состояние не меняется")
    void orConditionBothDisjunctsFalse() throws IOException {
        // mass = 50 (вне mass_le_10) и технология не согласована.
        Path config = configWithExtraObject(String.join("\n",
                "  - id: cmp-heavy-no-tech",
                "    class: component",
                "    values:",
                "      designation: АБВГ.757412.102",
                "      mass: 50",
                "      is_produced: \"yes\"",
                "      suppliers_count: 2",
                "      tech_process_approved: \"no\"",
                ""));
        TestHarness harness = TestHarness.from(config);

        harness.start("ivanov", "cmp-heavy-no-tech");
        harness.action("ivanov", "cmp-heavy-no-tech", "declare");
        harness.action("ivanov", "cmp-heavy-no-tech", "submit");
        harness.action("petrov", "cmp-heavy-no-tech", "approve_kb");

        assertRejected(harness.action("sokolova", "cmp-heavy-no-tech", "approve_to"), "review_to");
        assertEquals("review_to", harness.state("cmp-heavy-no-tech"));
        assertEquals("review_to", harness.lastEntry().stateBefore());
        assertEquals("review_to", harness.lastEntry().stateAfter());
    }

    // ------------------------------------------------------------------ not

    @Test
    @DisplayName("NOT: no_production доступен только когда продукция не производится")
    void notConditionSelectsSoleMatchingBranch() {
        TestHarness harness = TestHarness.standard();

        // Производится → not produced ложно, переход отклоняется.
        harness.start("ivanov", "cmp-1");
        harness.action("ivanov", "cmp-1", "declare");
        harness.action("ivanov", "cmp-1", "submit");
        harness.action("petrov", "cmp-1", "approve_kb");
        harness.action("sokolova", "cmp-1", "approve_to");
        assertRejected(harness.action("kuznetsov", "cmp-1", "no_production"), "suppliers");

        // Не производится → переход выполняется.
        harness.start("ivanov", "cmp-custom");
        harness.action("ivanov", "cmp-custom", "declare");
        harness.action("ivanov", "cmp-custom", "submit");
        harness.action("petrov", "cmp-custom", "approve_kb");
        harness.action("sokolova", "cmp-custom", "approve_to");
        assertAccepted(harness.action("kuznetsov", "cmp-custom", "no_production"),
                "requirements");
    }

    @Test
    @DisplayName("NOT: allow и forbid — дополнения по де Моргану, вместе не выполняются")
    void notConditionKeepsAllowAndForbidComplementary() {
        TestHarness harness = TestHarness.standard();
        StateMachine machine = harness.model().templates().requireForClass("component");
        Transition allow = singleTransition(harness, "permission", "allow");
        Transition forbid = singleTransition(harness, "permission", "forbid");

        for (boolean produced : new boolean[]{true, false}) {
            for (boolean approved : new boolean[]{true, false}) {
                Obj probe = component("probe", produced, 3, approved);
                boolean allowTrue = allow.isApplicable(probe, harness.ctx());
                boolean forbidTrue = forbid.isApplicable(probe, harness.ctx());
                assertTrue(allowTrue ^ forbidTrue,
                        () -> "для produced=" + produced + ", supplier_approved=" + approved
                                + " истинно ровно одно из условий allow/forbid");
            }
        }
        assertTrue(machine.states().get("permission").roles().contains("chief_designer"),
                "решение принимает главный конструктор");
    }

    @Test
    @DisplayName("NOT: forbid отклоняется для производимого изделия с поставщиками")
    void forbidRejectedWhenAllowHolds() {
        TestHarness harness = TestHarness.standard();
        harness.start("ivanov", "cmp-1");
        harness.action("ivanov", "cmp-1", "declare");
        harness.action("ivanov", "cmp-1", "submit");
        harness.action("petrov", "cmp-1", "approve_kb");
        harness.action("sokolova", "cmp-1", "approve_to");
        harness.action("kuznetsov", "cmp-1", "find_suppliers");
        assertEquals("permission", harness.state("cmp-1"));

        assertRejected(harness.action("sidorov", "cmp-1", "forbid"), "permission");
        assertEquals("permission", harness.state("cmp-1"));
        assertAccepted(harness.action("sidorov", "cmp-1", "allow"), "allowed");
    }

    // ------------------------------------------------------------------ in_range

    @Test
    @DisplayName("in_range: масса выше max переопределённого параметра блокирует submit")
    void inRangeUsesOverriddenBounds() {
        TestHarness harness = TestHarness.standard();
        harness.start("ivanov", "cmp-heavy");
        harness.action("ivanov", "cmp-heavy", "declare");

        assertRejected(harness.action("ivanov", "cmp-heavy", "submit"), "editing");
        assertEquals("editing", harness.state("cmp-heavy"));
        assertTrue(harness.lastEntry().reason().contains("условие"));

        // Для базового класса то же значение допустимо — проверка идёт по действующему описанию.
        ParamPredicate inRange = new ParamPredicate("mass", "in_range", null);
        Obj asItem = new Obj("probe", "item", Map.of("mass", new BigDecimal("320")));
        Obj asComponent = new Obj("probe", "component", Map.of("mass", new BigDecimal("320")));
        assertTrue(inRange.eval(asItem, harness.ctx()),
                "320 в диапазоне базового класса item (max 1000)");
        assertFalse(inRange.eval(asComponent, harness.ctx()),
                "320 вне диапазона подкласса component (max 250)");
    }

    // ------------------------------------------------------------------ предикат класса

    @Test
    @DisplayName("Предикат класса: подкласс проходит, посторонний класс — нет")
    void classPredicateRejectsForeignClass() {
        TestHarness harness = TestHarness.standard();
        ClassPredicate isItem = new ClassPredicate("item");

        assertTrue(isItem.eval(harness.model().objects().require("cmp-1"), harness.ctx()));
        assertFalse(isItem.eval(new Obj("x", "unknown_class", Map.of()), harness.ctx()),
                "неизвестный класс не является изделием");
        assertTrue(new Not(isItem).eval(new Obj("x", "unknown_class", Map.of()), harness.ctx()),
                "not инвертирует булев результат предиката");
    }

    @Test
    @DisplayName("ДНФ компилируется в Or(And(...)) и в And для одного дизъюнкта")
    void conditionTreeShapes() {
        TestHarness harness = TestHarness.standard();
        StateMachine machine = harness.model().templates().requireForClass("component");

        // Один дизъюнкт из двух предикатов → And.
        assertInstanceOf(And.class, machine.transitionsFrom("editing", "submit").getFirst().condition());
        // Два дизъюнкта → Or.
        assertInstanceOf(Or.class, machine.transitionsFrom("review_to", "approve_to").getFirst().condition());
        assertInstanceOf(Or.class, machine.transitionsFrom("permission", "allow").getFirst().condition());
        // «not produced» превращается в Not внутри дизъюнкта.
        Or forbid = assertInstanceOf(Or.class,
                machine.transitionsFrom("permission", "forbid").getFirst().condition());
        And firstDisjunct = assertInstanceOf(And.class, forbid.items().getFirst());
        assertInstanceOf(Not.class, firstDisjunct.items().getFirst());
    }

    // ------------------------------------------------------------------ помощники

    private Obj component(String id, boolean produced, long suppliers, boolean approved) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("designation", "probe");
        values.put("mass", new BigDecimal("1.0"));
        values.put("is_produced", produced ? "yes" : "no");
        values.put("suppliers_count", suppliers);
        values.put("supplier_approved", approved ? "yes" : "no");
        values.put("tech_process_approved", "yes");
        return new Obj(id, "component", values);
    }

    private static Transition singleTransition(TestHarness harness, String stateId, String actionId) {
        List<Transition> transitions = harness.model().templates()
                .requireForClass("component").transitionsFrom(stateId, actionId);
        assertEquals(1, transitions.size(), "ровно один переход " + stateId + "/" + actionId);
        return transitions.getFirst();
    }

    /** Копирует штатную конфигурацию во временный каталог и дописывает объект в objects.yaml. */
    private Path configWithExtraObject(String objectBlock) throws IOException {
        TestHarness.copyStandardConfig(tempDir);
        Path objects = tempDir.resolve("objects.yaml");
        String base = Files.readString(objects, StandardCharsets.UTF_8);
        TestHarness.write(objects, base + objectBlock);
        return tempDir;
    }
}
