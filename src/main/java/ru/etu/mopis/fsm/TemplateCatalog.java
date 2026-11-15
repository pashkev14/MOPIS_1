package ru.etu.mopis.fsm;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import ru.etu.mopis.error.ConfigurationException;

/**
 * Каталог шаблонов процессов: не более одного шаблона на класс объекта.
 * Заполняется при загрузке конфигурации; после загрузки используется только на чтение.
 */
public final class TemplateCatalog {

    private final Map<String, StateMachine> byClass = new LinkedHashMap<>();

    public TemplateCatalog() {
    }

    public TemplateCatalog(Map<String, StateMachine> byClass) {
        byClass.values().forEach(this::register);
    }

    public void register(StateMachine machine) {
        StateMachine previous = byClass.putIfAbsent(machine.classId(), machine);
        if (previous != null) {
            throw new ConfigurationException("более одного шаблона процесса для класса " + machine.classId());
        }
    }

    /** Только точное совпадение класса: шаблоны процессов не наследуются. */
    public Optional<StateMachine> forClass(String classId) {
        return Optional.ofNullable(byClass.get(classId));
    }

    public StateMachine requireForClass(String classId) {
        return forClass(classId).orElseThrow(() -> new ConfigurationException(
                "нет шаблона процесса для класса " + classId));
    }

    public Map<String, StateMachine> all() {
        return Collections.unmodifiableMap(byClass);
    }
}
