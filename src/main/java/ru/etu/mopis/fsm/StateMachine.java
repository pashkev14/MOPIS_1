package ru.etu.mopis.fsm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import ru.etu.mopis.error.ConfigurationException;
import ru.etu.mopis.user.User;

/**
 * Шаблон процесса (табличный автомат). Общие неизменяемые данные: создаётся один раз
 * при загрузке конфигурации и <b>не хранит</b> текущее состояние — его хранит
 * {@link ProcessInstance}.
 */
public final class StateMachine {

    private final String processId;
    private final String title;
    private final String classId;
    private final Set<String> createRoles;
    private final Map<String, State> states;
    private final Map<String, ActionDef> actions;
    private final List<Transition> transitions;
    private final Map<String, List<Transition>> table;
    private final Map<String, State> initialStates;

    public StateMachine(String processId,
                        String title,
                        String classId,
                        Set<String> createRoles,
                        Map<String, State> states,
                        Map<String, ActionDef> actions,
                        List<Transition> transitions) {
        this.processId = processId;
        this.title = title;
        this.classId = classId;
        this.createRoles = Collections.unmodifiableSet(new LinkedHashSet<>(createRoles));
        this.states = Collections.unmodifiableMap(new LinkedHashMap<>(states));
        this.actions = Collections.unmodifiableMap(new LinkedHashMap<>(actions));
        this.transitions = List.copyOf(transitions);

        Map<String, List<Transition>> index = new LinkedHashMap<>();
        for (Transition transition : transitions) {
            index.computeIfAbsent(key(transition.sourceId(), transition.actionId()),
                    ignored -> new ArrayList<>()).add(transition);
        }
        Map<String, List<Transition>> frozen = new LinkedHashMap<>();
        index.forEach((k, v) -> frozen.put(k, List.copyOf(v)));
        this.table = Collections.unmodifiableMap(frozen);

        Map<String, State> initial = new LinkedHashMap<>();
        for (State state : this.states.values()) {
            if (state.isInitial()) {
                initial.put(state.id(), state);
            }
        }
        if (initial.size() != 1) {
            throw new ConfigurationException("шаблон '" + processId
                    + "' должен иметь ровно одно начальное состояние, найдено: " + initial.size());
        }
        this.initialStates = Collections.unmodifiableMap(initial);
    }

    public String processId() {
        return processId;
    }

    public String title() {
        return title;
    }

    /** Класс объекта, к которому привязан шаблон (точное совпадение, без наследования). */
    public String classId() {
        return classId;
    }

    public Set<String> createRoles() {
        return createRoles;
    }

    public Map<String, State> states() {
        return states;
    }

    public Map<String, ActionDef> actions() {
        return actions;
    }

    public List<Transition> transitions() {
        return transitions;
    }

    public State initialState() {
        return initialStates.values().iterator().next();
    }

    public State state(String stateId) {
        State state = states.get(stateId);
        if (state == null) {
            throw new ConfigurationException("неизвестное состояние шаблона '" + processId + "': " + stateId);
        }
        return state;
    }

    public Optional<ActionDef> action(String actionId) {
        return Optional.ofNullable(actions.get(actionId));
    }

    /** Кандидаты по паре (состояние, действие) — ещё без проверки условий. */
    public List<Transition> transitionsFrom(String stateId, String actionId) {
        return table.getOrDefault(key(stateId, actionId), List.of());
    }

    /** Может ли пользователь создать процесс по этому шаблону. */
    public boolean canCreate(User user) {
        return user != null && user.hasAnyRole(createRoles);
    }

    private static String key(String stateId, String actionId) {
        return stateId + '\u0000' + actionId;
    }

    @Override
    public String toString() {
        return "StateMachine[" + processId + ":" + classId + "]";
    }
}
