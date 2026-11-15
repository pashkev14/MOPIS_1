package ru.etu.mopis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static ru.etu.mopis.support.TestHarness.assertAccepted;
import static ru.etu.mopis.support.TestHarness.assertRejected;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ru.etu.mopis.error.RejectedException;
import ru.etu.mopis.expr.ClassPredicate;
import ru.etu.mopis.expr.ParamPredicate;
import ru.etu.mopis.support.TestHarness;
import ru.etu.mopis.type.Obj;
import ru.etu.mopis.type.Param;
import ru.etu.mopis.type.ParamType;

/**
 * Сценарии T5–T7: наследование параметров, переопределение описания параметра в подклассе,
 * подстановка {@code default} и различие «поле пропущено» / «явный null»,
 * предикат класса.
 *
 * <p>Данные берутся из настоящей конфигурации варианта 1: {@code item → component}.</p>
 */
@DisplayName("Наследование параметров, default и явный null")
class InheritanceAndValuesTest {

    private static BigDecimal decimal(Object value) {
        return value == null ? null : new BigDecimal(String.valueOf(value));
    }

    private static void assertNumberEquals(String expected, Object actual) {
        BigDecimal actualDecimal = decimal(actual);
        assertEquals(0, new BigDecimal(expected).compareTo(actualDecimal),
                () -> "ожидалось " + expected + ", получено " + actual);
    }

    @Test
    @DisplayName("T5: подкласс наследует designation и полностью переопределяет mass")
    void subclassInheritsAndOverridesParameters() {
        TestHarness harness = TestHarness.standard();

        Param inherited = harness.model().classes().param("component", "designation").orElseThrow();
        assertEquals(ParamType.STRING, inherited.type(), "designation унаследован тем же типом");
        assertFalse(inherited.hasDefault(), "у designation нет значения по умолчанию");
        assertTrue(inherited.min().isEmpty() && inherited.max().isEmpty(), "у строки нет границ");

        Param overridden = harness.model().classes().param("component", "mass").orElseThrow();
        assertEquals(ParamType.DECIMAL, overridden.type(), "тип при переопределении сохранён");
        assertEquals(0, new BigDecimal("250").compareTo(overridden.max().orElseThrow()),
                "переопределённый max подкласса");
        assertEquals(0, new BigDecimal("0.001").compareTo(overridden.min().orElseThrow()),
                "переопределённый min подкласса");
        assertTrue(overridden.hasDefault(), "у mass есть значение по умолчанию");
        assertEquals(0, new BigDecimal("0.5").compareTo(decimal(overridden.effectiveDefault())),
                "default подкласса, а не родителя");

        Param parentMass = harness.model().classes().param("item", "mass").orElseThrow();
        assertEquals(0, new BigDecimal("1000").compareTo(parentMass.max().orElseThrow()),
                "у базового класса диапазон шире");
        assertEquals(0, new BigDecimal("1.0").compareTo(decimal(parentMass.effectiveDefault())),
                "default базового класса отличается от подкласса");

        // Действующий набор подкласса = наследованные + собственные параметры.
        Map<String, Param> componentParams = harness.model().classes().params("component");
        assertTrue(componentParams.keySet().containsAll(
                        java.util.Set.of("designation", "mass", "is_produced",
                                "suppliers_count", "supplier_approved", "tech_process_approved")),
                "подкласс содержит и унаследованные, и собственные параметры: " + componentParams.keySet());
    }

    @Test
    @DisplayName("T5: масса 320 вне диапазона component, но в диапазоне item")
    void overriddenBoundsDecideTransition() {
        TestHarness harness = TestHarness.standard();

        ParamPredicate inRange = new ParamPredicate("mass", "in_range", null);
        Obj asItem = new Obj("probe", "item", Map.of("mass", new BigDecimal("320")));
        Obj asComponent = new Obj("probe", "component", Map.of("mass", new BigDecimal("320")));

        assertTrue(inRange.eval(asItem, harness.ctx()),
                "для item 320 кг в диапазоне 0.001–1000");
        assertFalse(inRange.eval(asComponent, harness.ctx()),
                "для component 320 кг вне диапазона 0.001–250");

        // Тот же вывод через реальную команду: cmp-heavy не проходит submit.
        assertAccepted(harness.start("ivanov", "cmp-heavy"), "declared");
        assertAccepted(harness.action("ivanov", "cmp-heavy", "declare"), "editing");
        assertRejected(harness.action("ivanov", "cmp-heavy", "submit"), "editing");
        assertEquals("editing", harness.state("cmp-heavy"), "состояние не изменилось");
        assertTrue(harness.lastEntry().reason().contains("условие"),
                "причина отказа — невыполненное условие перехода");
    }

    @Test
    @DisplayName("T6: пропущенное поле получает default, явный null — нет")
    void defaultSubstitutionVersusExplicitNull() {
        TestHarness harness = TestHarness.standard();

        Obj undefined = harness.model().objects().require("cmp-undefined");
        assertNumberEquals("0.5", undefined.value("mass"));
        assertEquals("no", undefined.value("is_produced"));
        assertEquals("no", undefined.value("supplier_approved"));
        assertNumberEquals("0", undefined.value("suppliers_count"));
        assertNull(undefined.value("designation"), "у designation нет default — значение не задано");

        Obj explicitNull = harness.model().objects().require("cmp-explicit-null");
        assertNull(explicitNull.value("suppliers_count"),
                "явный null не заменяется значением по умолчанию 0");
        assertNumberEquals("5.0", explicitNull.value("mass"));

        Obj withDefaults = harness.model().objects().require("cmp-1");
        assertEquals("no", withDefaults.value("supplier_approved"),
                "пропущенное поле получило default \"no\"");
        assertNumberEquals("0.35", withDefaults.value("mass"));
    }

    @Test
    @DisplayName("T4/T6: незаданное обозначение блокирует declare, default-значения — нет")
    void undefinedDesignationBlocksDeclare() {
        TestHarness harness = TestHarness.standard();

        assertAccepted(harness.start("ivanov", "cmp-undefined"), "declared");
        assertRejected(harness.action("ivanov", "cmp-undefined", "declare"), "declared");
        assertEquals("declared", harness.state("cmp-undefined"));

        // Тот же переход выполняется, если обозначение задано и объект — изделие.
        assertAccepted(harness.start("ivanov", "cmp-custom"), "declared");
        assertAccepted(harness.action("ivanov", "cmp-custom", "declare"), "editing");
    }

    @Test
    @DisplayName("T7: предикат класса is_item истинен для подкласса component")
    void classPredicateAcceptsSubclass() {
        TestHarness harness = TestHarness.standard();

        ClassPredicate isItem = new ClassPredicate("item");
        assertTrue(isItem.eval(harness.model().objects().require("cmp-1"), harness.ctx()),
                "component — подкласс item");
        assertTrue(isItem.eval(harness.model().objects().require("itm-1"), harness.ctx()),
                "предикат рефлексивен для самого item");

        assertTrue(harness.model().classes().isInstanceOf("component", "item"));
        assertFalse(harness.model().classes().isInstanceOf("item", "component"),
                "обратное наследование не выполняется");
    }

    @Test
    @DisplayName("T5: шаблон привязан к точному классу — для item процесса нет")
    void templateIsNotInherited() {
        TestHarness harness = TestHarness.standard();

        assertTrue(harness.model().templates().forClass("item").isEmpty(),
                "шаблонов для базового класса нет");
        assertEquals("component", harness.model().templates().all().values().iterator().next().classId());

        assertRejected(harness.start("ivanov", "itm-1"));
        assertNull(harness.state("itm-1"), "процесс не создан");
        assertNull(harness.lastEntry().stateBefore(), "процесса не было");
        assertNull(harness.lastEntry().stateAfter());
        assertThrows(RejectedException.class,
                () -> harness.engine().start(harness.model().users().require("ivanov"), "itm-1"),
                "движок отказывает в запуске по классу без шаблона");
    }
}
