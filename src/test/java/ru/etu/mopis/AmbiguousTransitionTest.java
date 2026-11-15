package ru.etu.mopis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static ru.etu.mopis.support.TestHarness.assertAccepted;
import static ru.etu.mopis.support.TestHarness.assertRejected;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ru.etu.mopis.command.CommandResult;
import ru.etu.mopis.config.ApplicationModel;
import ru.etu.mopis.config.ConfigurationLoader;
import ru.etu.mopis.error.CommandOutcome;
import ru.etu.mopis.error.ConfigurationException;
import ru.etu.mopis.support.TestHarness;

/**
 * Сценарии T9 и проверки общности механизма: состояние не меняется при неоднозначном
 * переходе, ошибки конфигурации понятны, а механизм исполнения управляется только YAML.
 *
 * <p>Неоднозначность и другие изменения правил моделируются во <b>временной копии</b>
 * конфигурации; штатный {@code config/} не изменяется, {@code src/main/java} не трогается.</p>
 */
@DisplayName("Неоднозначные переходы и общность механизма")
class AmbiguousTransitionTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("T9: два перехода с истинными условиями → error, состояние не меняется")
    void ambiguousTransitionReportsError() throws IOException {
        ApplicationModel model = modelWithProcessYaml(processWithDuplicatedSubmit());
        TestHarness harness = TestHarness.of(model);

        harness.start("ivanov", "cmp-1");
        harness.action("ivanov", "cmp-1", "declare");
        assertEquals("editing", harness.state("cmp-1"));

        CommandResult ambiguous = harness.action("ivanov", "cmp-1", "submit");
        assertEquals(CommandOutcome.ERROR, ambiguous.outcome(),
                "подходят два перехода — это ошибка, а не выбор первого попавшегося");
        assertEquals("editing", ambiguous.stateBefore());
        assertEquals("editing", ambiguous.stateAfter(), "состояние не изменилось");
        assertEquals("editing", harness.state("cmp-1"));
        assertTrue(ambiguous.reason().contains("неоднознач"),
                "причина сообщает о неоднозначности: " + ambiguous.reason());
        assertEquals(CommandOutcome.ERROR, harness.lastEntry().result(),
                "ошибка тоже попадает в журнал");
    }

    @Test
    @DisplayName("Правило переходов остаётся данным: новый переход из YAML начинает работать")
    void newTransitionComesFromConfiguration() throws IOException {
        // Добавляем в шаблон действие и переход declared → requirements, которых нет в штатной конфигурации.
        String process = Files.readString(TestHarness.CONFIG_DIR.resolve("process.yaml"), StandardCharsets.UTF_8)
                .replace("  - id: declare\n", "  - id: fast_track\n    title: Ускоренный маршрут\n  - id: declare\n")
                .replace("transitions:\n", """
                        transitions:
                          - from: declared
                            action: fast_track
                            to: requirements
                            condition:
                              - [not produced]
                        """);
        TestHarness harness = TestHarness.of(modelWithProcessYaml(process));

        harness.start("ivanov", "cmp-custom");
        CommandResult fastTrack = harness.action("ivanov", "cmp-custom", "fast_track");
        assertAccepted(fastTrack, "requirements");

        // На производимом объекте тот же переход отклоняется по условию.
        harness.start("ivanov", "cmp-1");
        assertRejected(harness.action("ivanov", "cmp-1", "fast_track"), "declared");
    }

    @Test
    @DisplayName("Агрегатор в конфигурации — понятная ошибка, а не подмена алгоритма")
    void aggregateIsRejectedWithClearError() throws IOException {
        String process = Files.readString(TestHarness.CONFIG_DIR.resolve("process.yaml"), StandardCharsets.UTF_8)
                .replace("    parameter: suppliers_count\n",
                        "    parameter: suppliers_count\n    aggregate: all\n");
        Path dir = configDirWithProcessYaml(process);
        ConfigurationException error = assertThrows(ConfigurationException.class,
                () -> new ConfigurationLoader().load(dir));
        assertTrue(error.getMessage().contains("агрегатор"),
                "сообщение объясняет причину: " + error.getMessage());
    }

    @Test
    @DisplayName("Путь 'состав.параметр' не поддерживается и даёт понятную ошибку")
    void dottedPathIsRejected() throws IOException {
        String process = Files.readString(TestHarness.CONFIG_DIR.resolve("process.yaml"), StandardCharsets.UTF_8)
                .replace("parameter: suppliers_count", "parameter: suppliers.count");
        Path dir = configDirWithProcessYaml(process);
        ConfigurationException error = assertThrows(ConfigurationException.class,
                () -> new ConfigurationLoader().load(dir));
        assertTrue(error.getMessage().contains("состав"),
                "сообщение объясняет причину: " + error.getMessage());
    }

    @Test
    @DisplayName("Условие с неизвестным предикатом не загружается")
    void unknownPredicateIsRejected() throws IOException {
        String process = Files.readString(TestHarness.CONFIG_DIR.resolve("process.yaml"), StandardCharsets.UTF_8)
                .replace("[designation_filled, mass_in_range]", "[designation_filled, no_such_predicate]");
        Path dir = configDirWithProcessYaml(process);
        ConfigurationException error = assertThrows(ConfigurationException.class,
                () -> new ConfigurationLoader().load(dir));
        assertTrue(error.getMessage().contains("no_such_predicate"),
                "сообщение называет неизвестный предикат: " + error.getMessage());
    }

    @Test
    @DisplayName("Переопределение параметра с другим типом обнаруживается при загрузке")
    void typeChangeOnOverrideIsRejected() throws IOException {
        Path dir = tempDir.resolve("bad_classes");
        TestHarness.copyStandardConfig(dir);
        String classes = Files.readString(dir.resolve("classes.yaml"), StandardCharsets.UTF_8);
        // Меняем тип только у переопределённого mass подкласса component (после маркера переопределения).
        int marker = classes.indexOf("# переопределение");
        String head = classes.substring(0, marker);
        String tail = classes.substring(marker)
                .replaceFirst("type: decimal", "type: integer");
        TestHarness.write(dir.resolve("classes.yaml"), head + tail);

        ConfigurationException error = assertThrows(ConfigurationException.class,
                () -> new ConfigurationLoader().load(dir));
        assertTrue(error.getMessage().contains("mass"), "сообщение называет параметр: " + error.getMessage());
    }

    @Test
    @DisplayName("Одно начальное состояние обязательно")
    void exactlyOneInitialStateRequired() throws IOException {
        String process = Files.readString(TestHarness.CONFIG_DIR.resolve("process.yaml"), StandardCharsets.UTF_8)
                .replace("    initial: true\n", "");
        Path dir = configDirWithProcessYaml(process);
        ConfigurationException error = assertThrows(ConfigurationException.class,
                () -> new ConfigurationLoader().load(dir));
        assertTrue(error.getMessage().contains("начальн"),
                "сообщение объясняет требование: " + error.getMessage());
    }

    @Test
    @DisplayName("Действие в состоянии без перехода отклоняется")
    void actionWithoutTransitionIsRejected() {
        TestHarness harness = TestHarness.standard();
        harness.start("ivanov", "cmp-1");
        harness.action("ivanov", "cmp-1", "declare");
        assertEquals("editing", harness.state("cmp-1"));

        // allow объявлено в шаблоне, но перехода из editing нет.
        assertRejected(harness.action("ivanov", "cmp-1", "allow"), "editing");
        assertEquals("editing", harness.state("cmp-1"));
        assertFalse(harness.lastEntry().reason().isBlank());
    }

    @Test
    @DisplayName("Действие над незапущенным процессом отклоняется")
    void actionWithoutStartedProcessIsRejected() {
        TestHarness harness = TestHarness.standard();

        CommandResult result = harness.action("ivanov", "cmp-1", "declare");
        assertRejected(result);
        assertNull(result.stateBefore(), "процесса не было");
        assertNull(result.stateAfter());
        assertNull(harness.state("cmp-1"));
        assertEquals(CommandOutcome.REJECTED, harness.lastEntry().result());
    }

    // ------------------------------------------------------------------ помощники

    /** Сохраняет изменённый process.yaml во временном каталоге и возвращает каталог. */
    private Path configDirWithProcessYaml(String processYaml) throws IOException {
        Path dir = tempDir.resolve("config_" + Math.abs(processYaml.hashCode()));
        TestHarness.copyStandardConfig(dir);
        TestHarness.write(dir.resolve("process.yaml"), processYaml);
        return dir;
    }

    /** То же, но возвращает уже загруженную модель. */
    private ApplicationModel modelWithProcessYaml(String processYaml) throws IOException {
        return new ConfigurationLoader().load(configDirWithProcessYaml(processYaml));
    }

    private static String processWithDuplicatedSubmit() throws IOException {
        // Дублируем переход editing→review_kb с тем же условием: оба истинны одновременно.
        String base = Files.readString(TestHarness.CONFIG_DIR.resolve("process.yaml"), StandardCharsets.UTF_8);
        String duplicate = """
                  - from: editing
                    action: submit
                    to: review_kb
                    condition:
                      - [designation_filled, mass_in_range]
                """.stripIndent();
        int index = base.indexOf("  - from: editing\n");
        return base.substring(0, index) + duplicate + base.substring(index);
    }
}
