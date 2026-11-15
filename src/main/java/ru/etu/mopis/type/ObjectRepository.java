package ru.etu.mopis.type;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Хранилище готовых объектов, загруженных из конфигурации при старте приложения.
 */
public final class ObjectRepository {

    private final Map<String, Obj> objects;

    public ObjectRepository(List<Obj> objects) {
        Map<String, Obj> byId = new LinkedHashMap<>();
        for (Obj obj : objects) {
            byId.put(obj.id(), obj);
        }
        this.objects = Collections.unmodifiableMap(byId);
    }

    public Obj require(String id) {
        Obj obj = objects.get(id);
        if (obj == null) {
            throw new IllegalArgumentException("объект не найден: " + id);
        }
        return obj;
    }

    public Optional<Obj> find(String id) {
        return Optional.ofNullable(objects.get(id));
    }

    public boolean contains(String id) {
        return objects.containsKey(id);
    }

    public List<Obj> all() {
        return Collections.unmodifiableList(new ArrayList<>(objects.values()));
    }
}
