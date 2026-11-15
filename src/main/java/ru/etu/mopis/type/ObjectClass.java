package ru.etu.mopis.type;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Описание класса объектов. Владеет только собственными объявлениями параметров:
 * унаследованные остаются у родителя, действующий набор собирает {@link ClassRegistry}.
 */
public final class ObjectClass {

    private final String id;
    private final String title;
    private final String parentId;
    private final Map<String, Param> ownParams;

    public ObjectClass(String id, String title, String parentId, Map<String, Param> ownParams) {
        this.id = id;
        this.title = title;
        this.parentId = parentId;
        this.ownParams = Collections.unmodifiableMap(new LinkedHashMap<>(ownParams));
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    public Optional<String> parentId() {
        return Optional.ofNullable(parentId);
    }

    /** Собственные параметры класса в порядке объявления. */
    public Map<String, Param> ownParams() {
        return ownParams;
    }

    public Optional<Param> ownParam(String paramId) {
        return Optional.ofNullable(ownParams.get(paramId));
    }

    @Override
    public String toString() {
        return "ObjectClass[" + id + "]";
    }
}
