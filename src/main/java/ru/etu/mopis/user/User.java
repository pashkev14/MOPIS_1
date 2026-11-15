package ru.etu.mopis.user;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Пользователь системы с одной или несколькими ролями.
 *
 * <p>Аутентификация не выполняется: пользователь указывается в команде.
 * Для разрешения операции достаточно одной подходящей роли.</p>
 */
public final class User {

    private final String id;
    private final String name;
    private final Set<String> roles;

    public User(String id, String name, Collection<String> roles) {
        this.id = id;
        this.name = name;
        this.roles = Collections.unmodifiableSet(new LinkedHashSet<>(roles));
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public Set<String> roles() {
        return roles;
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    public boolean hasAnyRole(Collection<String> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return false;
        }
        for (String role : candidates) {
            if (roles.contains(role)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "User[" + id + ":" + roles + "]";
    }
}
