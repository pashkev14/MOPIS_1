package ru.etu.mopis.config;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import ru.etu.mopis.error.ConfigurationException;
import ru.etu.mopis.expr.ClassPredicate;
import ru.etu.mopis.expr.Expression;
import ru.etu.mopis.expr.ParamPredicate;
import ru.etu.mopis.fsm.ActionDef;
import ru.etu.mopis.fsm.State;
import ru.etu.mopis.fsm.StateMachine;
import ru.etu.mopis.fsm.TemplateCatalog;
import ru.etu.mopis.fsm.Transition;
import ru.etu.mopis.type.ClassRegistry;
import ru.etu.mopis.type.Obj;
import ru.etu.mopis.type.ObjectClass;
import ru.etu.mopis.type.ObjectRepository;
import ru.etu.mopis.type.Param;
import ru.etu.mopis.type.ParamType;
import ru.etu.mopis.user.User;
import ru.etu.mopis.user.UserDirectory;

/**
 * Загрузчик YAML-конфигурации ({@code classes.yaml}, {@code objects.yaml}, {@code users.yaml},
 * {@code process.yaml}). Ошибки структуры и неизвестные ссылки — {@link ConfigurationException}
 * (fail-fast, понятное сообщение).
 *
 * <p>В Java-коде нет ни одного идентификатора предметной области: всё приходит из файлов.</p>
 */
public final class ConfigurationLoader {

    private static final String CLASSES = "classes.yaml";
    private static final String OBJECTS = "objects.yaml";
    private static final String USERS = "users.yaml";
    private static final String PROCESS = "process.yaml";

    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory());

    public ApplicationModel load(Path configDir) {
        if (configDir == null || !Files.isDirectory(configDir)) {
            throw new ConfigurationException("каталог конфигурации не найден: " + configDir);
        }
        ClassRegistry classes = loadClasses(configDir.resolve(CLASSES));
        UserDirectory users = loadUsers(configDir.resolve(USERS));
        ObjectRepository objects = loadObjects(configDir.resolve(OBJECTS), classes);
        TemplateCatalog templates = loadTemplates(configDir.resolve(PROCESS), classes, users);
        return new ApplicationModel(classes, objects, users, templates);
    }

    // ------------------------------------------------------------------ classes

    private ClassRegistry loadClasses(Path file) {
        JsonNode root = read(file);
        JsonNode list = requireArray(root, "classes", file);

        Map<String, ObjectClass> classes = new LinkedHashMap<>();
        for (JsonNode node : list) {
            String id = requireText(node, "id", file);
            String title = optionalText(node, "title", id);
            String parent = optionalText(node, "parent", null);
            if (parent != null) {
                rejectDottedPath(parent, "parent класса '" + id + "'");
            }
            Map<String, Param> own = new LinkedHashMap<>();
            JsonNode params = node.get("parameters");
            if (params != null && !params.isNull()) {
                if (!params.isArray()) {
                    throw new ConfigurationException(file + ": classes[].parameters должен быть списком");
                }
                for (JsonNode paramNode : params) {
                    Param param = parseParam(paramNode, file, id);
                    if (own.putIfAbsent(param.id(), param) != null) {
                        throw new ConfigurationException("дублирующийся параметр '" + param.id()
                                + "' в классе '" + id + "'");
                    }
                }
            }
            ObjectClass objectClass = new ObjectClass(id, title, parent, own);
            if (classes.putIfAbsent(id, objectClass) != null) {
                throw new ConfigurationException("дублирующийся id класса: " + id);
            }
        }
        if (classes.isEmpty()) {
            throw new ConfigurationException(file + ": не описано ни одного класса");
        }

        // ClassRegistry разрешает наследование и проверяет ссылки/циклы/типы.
        ClassRegistry registry = new ClassRegistry(classes);
        for (ObjectClass objectClass : classes.values()) {
            objectClass.parentId().ifPresent(parent -> registry.require(parent));
        }
        return registry;
    }

    private Param parseParam(JsonNode node, Path file, String classId) {
        String id = requireText(node, "id", file);
        rejectDottedPath(id, "параметр класса '" + classId + "'");
        rejectComposition(node, file, "параметр '" + id + "' класса '" + classId + "'");
        String title = optionalText(node, "title", id);
        ParamType type = ParamType.fromWire(requireText(node, "type", file));

        BigDecimal min = numeric(node, "min", file);
        BigDecimal max = numeric(node, "max", file);
        if (!type.isNumeric()) {
            // Границы определены только для чисел; для строк они не применяются.
            min = null;
            max = null;
        }

        JsonNode defaultNode = node.get("default");
        boolean hasDefault = defaultNode != null && !defaultNode.isNull();
        Object defaultValue = hasDefault ? scalar(defaultNode) : null;
        return new Param(id, title, type, min, max, defaultValue, hasDefault);
    }

    // ------------------------------------------------------------------ users

    private UserDirectory loadUsers(Path file) {
        JsonNode root = read(file);

        Map<String, String> roles = new LinkedHashMap<>();
        for (JsonNode node : requireArray(root, "roles", file)) {
            String id = requireText(node, "id", file);
            String title = optionalText(node, "title", id);
            if (roles.putIfAbsent(id, title) != null) {
                throw new ConfigurationException("дублирующаяся роль: " + id);
            }
        }
        if (roles.isEmpty()) {
            throw new ConfigurationException(file + ": не описано ни одной роли");
        }

        Map<String, User> users = new LinkedHashMap<>();
        for (JsonNode node : requireArray(root, "users", file)) {
            String id = requireText(node, "id", file);
            String name = optionalText(node, "name", id);
            JsonNode rolesNode = node.get("roles");
            if (rolesNode == null || !rolesNode.isArray() || rolesNode.isEmpty()) {
                throw new ConfigurationException("у пользователя '" + id + "' не задан непустой список ролей");
            }
            Set<String> userRoles = new LinkedHashSet<>();
            for (JsonNode roleNode : rolesNode) {
                String roleId = roleNode.asText();
                if (!roles.containsKey(roleId)) {
                    throw new ConfigurationException("у пользователя '" + id + "' неизвестная роль: " + roleId);
                }
                userRoles.add(roleId);
            }
            User user = new User(id, name, userRoles);
            if (users.putIfAbsent(id, user) != null) {
                throw new ConfigurationException("дублирующийся пользователь: " + id);
            }
        }
        return new UserDirectory(roles, users);
    }

    // ------------------------------------------------------------------ objects

    private ObjectRepository loadObjects(Path file, ClassRegistry classes) {
        JsonNode root = read(file);
        List<Obj> objects = new ArrayList<>();
        Set<String> ids = new LinkedHashSet<>();

        for (JsonNode node : requireArray(root, "objects", file)) {
            String id = requireText(node, "id", file);
            if (!ids.add(id)) {
                throw new ConfigurationException("дублирующийся id объекта: " + id);
            }
            String classId = requireText(node, "class", file);
            Map<String, Param> params = classes.params(classId);

            Map<String, Object> rawValues = new LinkedHashMap<>();
            JsonNode values = node.get("values");
            if (values != null && !values.isNull()) {
                if (!values.isObject()) {
                    throw new ConfigurationException(file + ": objects[].values должен быть словарём");
                }
                Iterator<Map.Entry<String, JsonNode>> fields = values.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> entry = fields.next();
                    JsonNode valueNode = entry.getValue();
                    rawValues.put(entry.getKey(), valueNode.isNull() ? null : scalar(valueNode));
                }
            }

            Map<String, Object> resolvedValues = new LinkedHashMap<>();
            for (Param param : params.values()) {
                if (rawValues.containsKey(param.id())) {
                    // Присутствующий ключ берётся как есть, включая явный null.
                    resolvedValues.put(param.id(), param.coerce(rawValues.get(param.id())));
                } else if (param.hasDefault()) {
                    resolvedValues.put(param.id(), param.effectiveDefault());
                } else {
                    resolvedValues.put(param.id(), null);
                }
            }
            objects.add(new Obj(id, classId, resolvedValues));
        }
        return new ObjectRepository(objects);
    }

    // ------------------------------------------------------------------ process

    private TemplateCatalog loadTemplates(Path file, ClassRegistry classes, UserDirectory users) {
        JsonNode root = read(file);

        String processId = requireText(root, "process", file);
        String title = optionalText(root, "title", processId);
        String classId = requireText(root, "class", file);
        classes.require(classId);

        Set<String> createRoles = readRoles(root.get("create_roles"), users, file,
                "create_roles шаблона '" + processId + "'");

        Map<String, ActionDef> actions = new LinkedHashMap<>();
        for (JsonNode node : requireArray(root, "actions", file)) {
            String id = requireText(node, "id", file);
            String actionTitle = optionalText(node, "title", id);
            if (actions.putIfAbsent(id, new ActionDef(id, actionTitle)) != null) {
                throw new ConfigurationException("дублирующееся действие: " + id);
            }
        }
        if (actions.isEmpty()) {
            throw new ConfigurationException(file + ": не описано ни одного действия");
        }

        Map<String, Expression> predicates = loadPredicates(root, file, classes);

        Map<String, State> states = new LinkedHashMap<>();
        for (JsonNode node : requireArray(root, "states", file)) {
            String id = requireText(node, "id", file);
            String stateTitle = optionalText(node, "title", id);
            boolean initial = node.path("initial").asBoolean(false);
            boolean isFinal = node.path("final").asBoolean(false);
            Set<String> roles = isFinal
                    ? Set.of()
                    : readRoles(node.get("roles"), users, file, "состояние '" + id + "'");
            State state = new State(id, stateTitle, initial, isFinal, roles);
            if (states.putIfAbsent(id, state) != null) {
                throw new ConfigurationException("дублирующееся состояние: " + id);
            }
        }
        if (states.isEmpty()) {
            throw new ConfigurationException(file + ": не описано ни одного состояния");
        }

        ConditionCompiler compiler = new ConditionCompiler(predicates);
        List<Transition> transitions = new ArrayList<>();
        JsonNode transitionsNode = root.get("transitions");
        if (transitionsNode != null && !transitionsNode.isNull()) {
            if (!transitionsNode.isArray()) {
                throw new ConfigurationException(file + ": transitions должен быть списком");
            }
            for (JsonNode node : transitionsNode) {
                String from = requireText(node, "from", file);
                String action = requireText(node, "action", file);
                String to = requireText(node, "to", file);
                if (!states.containsKey(from)) {
                    throw new ConfigurationException("переход ссылается на неизвестное состояние: " + from);
                }
                if (!states.containsKey(to)) {
                    throw new ConfigurationException("переход ссылается на неизвестное состояние: " + to);
                }
                if (!actions.containsKey(action)) {
                    throw new ConfigurationException("переход ссылается на неизвестное действие: " + action);
                }
                Expression condition = compiler.compile(node.get("condition"));
                transitions.add(new Transition(from, action, to, condition));
            }
        }

        StateMachine machine = new StateMachine(processId, title, classId, createRoles,
                states, actions, transitions);

        TemplateCatalog catalog = new TemplateCatalog();
        catalog.register(machine);
        return catalog;
    }

    private Map<String, Expression> loadPredicates(JsonNode root, Path file, ClassRegistry classes) {
        Map<String, Expression> predicates = new LinkedHashMap<>();
        JsonNode list = root.get("predicates");
        if (list == null || list.isNull()) {
            return predicates;
        }
        if (!list.isArray()) {
            throw new ConfigurationException(file + ": predicates должен быть списком");
        }
        for (JsonNode node : list) {
            String id = requireText(node, "id", file);
            if (node.has("aggregate")) {
                throw new ConfigurationException("предикат '" + id
                        + "': агрегаторы не поддерживаются (механизм составности не реализован)");
            }
            boolean hasParameter = node.hasNonNull("parameter");
            boolean hasClass = node.hasNonNull("class_id");
            if (hasParameter == hasClass) {
                throw new ConfigurationException("предикат '" + id
                        + "': требуется ровно одно из полей parameter / class_id");
            }

            Expression expression;
            if (hasClass) {
                String target = node.get("class_id").asText();
                classes.require(target);
                expression = new ClassPredicate(target);
            } else {
                String parameter = node.get("parameter").asText();
                rejectDottedPath(parameter, "предикат '" + id + "'");
                String operator = optionalText(node, "operator", "filled");
                JsonNode valueNode = node.get("value");
                Object operand = valueNode == null || valueNode.isNull() ? null : scalar(valueNode);
                expression = new ParamPredicate(parameter, operator, operand);
            }
            if (predicates.putIfAbsent(id, expression) != null) {
                throw new ConfigurationException("дублирующийся предикат: " + id);
            }
        }
        return predicates;
    }

    // ------------------------------------------------------------------ helpers

    private Set<String> readRoles(JsonNode node, UserDirectory users, Path file, String where) {
        if (node == null || !node.isArray() || node.isEmpty()) {
            throw new ConfigurationException(file + ": не задан непустой список ролей для " + where);
        }
        Set<String> roles = new LinkedHashSet<>();
        for (JsonNode roleNode : node) {
            String roleId = roleNode.asText();
            if (!users.hasRole(roleId)) {
                throw new ConfigurationException(where + ": неизвестная роль " + roleId);
            }
            roles.add(roleId);
        }
        return roles;
    }

    private JsonNode read(Path file) {
        if (!Files.isRegularFile(file)) {
            throw new ConfigurationException("файл конфигурации не найден: " + file);
        }
        try (InputStream in = Files.newInputStream(file)) {
            JsonNode node = yaml.readTree(in);
            if (node == null || node.isNull() || node.isMissingNode()) {
                throw new ConfigurationException("пустой файл конфигурации: " + file);
            }
            return node;
        } catch (IOException e) {
            throw new ConfigurationException("не удалось прочитать " + file + ": " + e.getMessage(), e);
        }
    }

    private static JsonNode requireArray(JsonNode root, String field, Path file) {
        JsonNode node = root.get(field);
        if (node == null || !node.isArray()) {
            throw new ConfigurationException(file + ": поле '" + field + "' должно быть списком");
        }
        return node;
    }

    private static String requireText(JsonNode node, String field, Path file) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            throw new ConfigurationException(file + ": не задано обязательное поле '" + field + "'");
        }
        return value.asText().trim();
    }

    private static String optionalText(JsonNode node, String field, String fallback) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return fallback;
        }
        String text = value.asText();
        return text.isBlank() ? fallback : text;
    }

    private static BigDecimal numeric(JsonNode node, String field, Path file) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isNumber()) {
            throw new ConfigurationException(file + ": поле '" + field + "' должно быть числом");
        }
        return value.decimalValue();
    }

    /** Скалярное значение YAML → Java-значение (Long / BigDecimal / Boolean / String). */
    private static Object scalar(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isIntegralNumber()) {
            return node.longValue();
        }
        if (node.isFloatingPointNumber()) {
            return node.decimalValue();
        }
        return node.asText();
    }

    private static void rejectDottedPath(String value, String where) {
        if (value != null && value.contains(".")) {
            throw new ConfigurationException(where + ": пути вида 'состав.параметр' не поддерживаются "
                    + "(механизм составности не реализован): " + value);
        }
    }

    private static void rejectComposition(JsonNode node, Path file, String where) {
        if (node.has("quantity_min") || node.has("quantity_max")) {
            throw new ConfigurationException(file + ": " + where
                    + ": параметры-составы (quantity_min/quantity_max) не поддерживаются");
        }
        if (node.has("aggregate")) {
            throw new ConfigurationException(file + ": " + where + ": агрегаторы не поддерживаются");
        }
    }

}
