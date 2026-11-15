package ru.etu.mopis.fsm;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import ru.etu.mopis.user.User;

/**
 * Состояние шаблона процесса. Неизменяемые общие данные.
 */
public final class State {

    private final String id;
    private final String title;
    private final boolean initial;
    private final boolean isFinal;
    private final Set<String> roles;

    public State(String id, String title, boolean initial, boolean isFinal, Collection<String> roles) {
        this.id = id;
        this.title = title;
        this.initial = initial;
        this.isFinal = isFinal;
        this.roles = Collections.unmodifiableSet(new LinkedHashSet<>(roles));
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    public boolean isInitial() {
        return initial;
    }

    public boolean isFinal() {
        return isFinal;
    }

    public Set<String> roles() {
        return roles;
    }

    /** Достаточно одной подходящей роли. */
    public boolean allows(User user) {
        return user != null && user.hasAnyRole(roles);
    }

    @Override
    public String toString() {
        return "State[" + id + "]";
    }
}
