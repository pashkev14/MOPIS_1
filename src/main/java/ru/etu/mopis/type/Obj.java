package ru.etu.mopis.type;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Готовый объект предметной области: ссылка на описание класса и значения параметров.
 *
 * <p>Незаданное значение и явный {@code null} неразличимы: оба означают «значение не задано».</p>
 */
public final class Obj {

    private final String id;
    private final String classId;
    private final Map<String, Object> values;

    public Obj(String id, String classId, Map<String, Object> values) {
        this.id = id;
        this.classId = classId;
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public String id() {
        return id;
    }

    public String classId() {
        return classId;
    }

    public Map<String, Object> values() {
        return values;
    }

    /** Значение параметра или {@code null}, если значение не задано. */
    public Object value(String paramId) {
        return values.get(paramId);
    }

    public Optional<Object> valueOptional(String paramId) {
        return Optional.ofNullable(values.get(paramId));
    }

    @Override
    public String toString() {
        return "Obj[" + id + ":" + classId + "]";
    }
}
