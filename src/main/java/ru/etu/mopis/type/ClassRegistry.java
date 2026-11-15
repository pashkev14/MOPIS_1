package ru.etu.mopis.type;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import ru.etu.mopis.error.ConfigurationException;

/**
 * Реестр описаний классов и единственное место, где вычисляется наследование параметров.
 *
 * <p>Действующий набор параметров класса = параметры родителя (рекурсивно, с сохранением
 * порядка объявления) + собственные параметры. Собственный параметр с тем же {@code id}
 * полностью заменяет унаследованный, но обязан сохранить его тип — иначе
 * {@link ConfigurationException} при загрузке.</p>
 */
public final class ClassRegistry {

    private final Map<String, ObjectClass> classes;
    private final Map<String, Map<String, Param>> effectiveParams;

    public ClassRegistry(Map<String, ObjectClass> classes) {
        this.classes = Collections.unmodifiableMap(new LinkedHashMap<>(classes));
        this.effectiveParams = resolveAll(this.classes);
    }

    public ObjectClass require(String classId) {
        ObjectClass objectClass = classes.get(classId);
        if (objectClass == null) {
            throw new ConfigurationException("неизвестный класс: " + classId);
        }
        return objectClass;
    }

    public Optional<ObjectClass> find(String classId) {
        return Optional.ofNullable(classes.get(classId));
    }

    public Set<String> ids() {
        return classes.keySet();
    }

    /** Действующие параметры класса с учётом наследования, в порядке объявления. */
    public Map<String, Param> params(String classId) {
        Map<String, Param> params = effectiveParams.get(classId);
        if (params == null) {
            throw new ConfigurationException("неизвестный класс: " + classId);
        }
        return params;
    }

    public Optional<Param> param(String classId, String paramId) {
        return Optional.ofNullable(params(classId).get(paramId));
    }

    /**
     * Проверка принадлежности классу: совпадение или подкласс (рефлексивно).
     */
    public boolean isInstanceOf(String classId, String targetClassId) {
        if (classId == null || targetClassId == null) {
            return false;
        }
        if (!classes.containsKey(targetClassId)) {
            throw new ConfigurationException("неизвестный класс: " + targetClassId);
        }
        String current = classId;
        Set<String> guard = new LinkedHashSet<>();
        while (current != null && guard.add(current)) {
            if (current.equals(targetClassId)) {
                return true;
            }
            ObjectClass objectClass = classes.get(current);
            if (objectClass == null) {
                return false;
            }
            current = objectClass.parentId().orElse(null);
        }
        return false;
    }

    private static Map<String, Map<String, Param>> resolveAll(Map<String, ObjectClass> classes) {
        Map<String, Map<String, Param>> resolved = new LinkedHashMap<>();
        for (String classId : classes.keySet()) {
            resolve(classId, classes, resolved, new LinkedHashSet<>());
        }
        return Collections.unmodifiableMap(resolved);
    }

    private static Map<String, Param> resolve(String classId,
                                              Map<String, ObjectClass> classes,
                                              Map<String, Map<String, Param>> resolved,
                                              Set<String> path) {
        Map<String, Param> cached = resolved.get(classId);
        if (cached != null) {
            return cached;
        }
        ObjectClass objectClass = classes.get(classId);
        if (objectClass == null) {
            throw new ConfigurationException("неизвестный класс: " + classId);
        }
        if (!path.add(classId)) {
            throw new ConfigurationException("циклическое наследование классов: " + String.join(" -> ", path));
        }

        Map<String, Param> params = new LinkedHashMap<>();
        Optional<String> parentId = objectClass.parentId();
        if (parentId.isPresent()) {
            String parent = parentId.get();
            if (parent.equals(classId)) {
                throw new ConfigurationException("класс '" + classId + "' не может наследовать сам себя");
            }
            Map<String, Param> parentParams = resolve(parent, classes, resolved, path);
            params.putAll(parentParams);
            for (Param own : objectClass.ownParams().values()) {
                Param inherited = parentParams.get(own.id());
                if (inherited != null && inherited.type() != own.type()) {
                    throw new ConfigurationException("параметр '" + own.id() + "' класса '" + classId
                            + "' переопределён с другим типом: " + inherited.type().wireName()
                            + " -> " + own.type().wireName());
                }
            }
        }
        for (Param own : objectClass.ownParams().values()) {
            // Переопределение полностью заменяет описание, но сохраняет позицию объявления родителя.
            params.put(own.id(), own);
        }

        path.remove(classId);
        Map<String, Param> frozen = Collections.unmodifiableMap(params);
        resolved.put(classId, frozen);
        return frozen;
    }
}
