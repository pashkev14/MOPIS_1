package ru.etu.mopis.fsm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import ru.etu.mopis.error.AmbiguousTransitionException;
import ru.etu.mopis.error.RejectedException;
import ru.etu.mopis.expr.EvalContext;
import ru.etu.mopis.type.ClassRegistry;
import ru.etu.mopis.type.Obj;
import ru.etu.mopis.type.ObjectRepository;
import ru.etu.mopis.user.User;
import ru.etu.mopis.user.UserDirectory;

/**
 * Исполнитель процессов: общий механизм, не содержащий ни одного идентификатора
 * класса, состояния, действия, роли или предиката конкретного варианта.
 *
 * <p>Порядок диагностики зафиксирован {@code spec.md} §5.4 для воспроизводимости.
 * Состояние процесса изменяется <b>только</b> при единственном подходящем переходе,
 * поэтому отказ по построению не меняет состояние.</p>
 */
public final class ProcessEngine {

    private final ClassRegistry classes;
    private final ObjectRepository objects;
    private final UserDirectory users;
    private final TemplateCatalog templates;
    private final EvalContext evalContext;
    private final Map<String, ProcessInstance> running = new LinkedHashMap<>();

    public ProcessEngine(ClassRegistry classes,
                         ObjectRepository objects,
                         UserDirectory users,
                         TemplateCatalog templates) {
        this.classes = classes;
        this.objects = objects;
        this.users = users;
        this.templates = templates;
        this.evalContext = new EvalContext(classes, objects);
    }

    /**
     * Запуск процесса над существующим объектом.
     * Проверки: пользователь → объект → точный шаблон класса → роль запуска → отсутствие процесса.
     */
    public ProcessInstance start(User user, String objectId) {
        if (user == null || users.find(user.id()).isEmpty()) {
            throw new RejectedException("пользователь не найден: " + (user == null ? "null" : user.id()));
        }
        Obj obj = objects.find(objectId).orElseThrow(
                () -> new RejectedException("объект не найден: " + objectId));
        StateMachine machine = templates.forClass(obj.classId()).orElseThrow(
                () -> new RejectedException("нет шаблона процесса для класса " + obj.classId()));
        if (!machine.canCreate(user)) {
            throw new RejectedException("требуется роль " + join(machine.createRoles())
                    + " для запуска процесса " + machine.processId());
        }
        if (running.containsKey(obj.id())) {
            throw new RejectedException("процесс уже запущен");
        }

        ProcessInstance instance = new ProcessInstance(
                obj.id(), obj.classId(), machine.processId(), machine.initialState().id());
        running.put(obj.id(), instance);
        return instance;
    }

    /**
     * Выполнение действия над запущенным процессом.
     * Проверки: пользователь → процесс → конечное состояние → действие → роль →
     * наличие переходов → вычисление условий → однозначность.
     */
    public ProcessInstance act(User user, String objectId, String actionId) {
        if (user == null || users.find(user.id()).isEmpty()) {
            throw new RejectedException("пользователь не найден: " + (user == null ? "null" : user.id()));
        }
        ProcessInstance instance = running.get(objectId);
        if (instance == null) {
            throw new RejectedException("процесс не запущен для объекта " + objectId);
        }
        Obj obj = objects.require(objectId);
        StateMachine machine = templates.requireForClass(instance.classId());
        State current = machine.state(instance.stateId());

        if (current.isFinal()) {
            throw new RejectedException("процесс завершён: из конечного состояния действия не выполняются");
        }
        if (machine.action(actionId).isEmpty()) {
            throw new RejectedException("неизвестное действие: " + actionId);
        }
        if (!current.allows(user)) {
            throw new RejectedException("требуется роль " + join(current.roles())
                    + " в состоянии " + current.id());
        }

        List<Transition> candidates = machine.transitionsFrom(current.id(), actionId);
        if (candidates.isEmpty()) {
            throw new RejectedException("нет перехода по действию " + actionId + " из состояния " + current.id());
        }

        List<Transition> matched = new ArrayList<>();
        for (Transition candidate : candidates) {
            if (candidate.isApplicable(obj, evalContext)) {
                matched.add(candidate);
            }
        }
        if (matched.isEmpty()) {
            throw new RejectedException("условие перехода не выполнено");
        }
        if (matched.size() > 1) {
            throw new AmbiguousTransitionException("неоднозначный переход: из состояния " + current.id()
                    + " по действию " + actionId + " подходит " + matched.size() + " перехода ("
                    + matched.stream().map(t -> t.sourceId() + "->" + t.targetId())
                            .collect(Collectors.joining(", ")) + ")");
        }

        Transition chosen = matched.getFirst();
        instance.moveTo(chosen.targetId());
        return instance;
    }

    public Optional<ProcessInstance> instanceOf(String objectId) {
        return Optional.ofNullable(running.get(objectId));
    }

    /** Текущее состояние процесса или {@code null}, если процесса нет. */
    public String stateOf(String objectId) {
        ProcessInstance instance = running.get(objectId);
        return instance == null ? null : instance.stateId();
    }

    public ClassRegistry classes() {
        return classes;
    }

    public ObjectRepository objects() {
        return objects;
    }

    public UserDirectory users() {
        return users;
    }

    public TemplateCatalog templates() {
        return templates;
    }

    public EvalContext evalContext() {
        return evalContext;
    }

    private static String join(Iterable<String> values) {
        List<String> list = new ArrayList<>();
        values.forEach(list::add);
        return String.join(", ", list);
    }
}
