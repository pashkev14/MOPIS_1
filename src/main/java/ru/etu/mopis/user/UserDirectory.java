package ru.etu.mopis.user;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import ru.etu.mopis.error.ConfigurationException;

/**
 * Справочник ролей и пользователей, загруженный из {@code users.yaml}.
 */
public final class UserDirectory {

    private final Map<String, String> roles;
    private final Map<String, User> users;

    public UserDirectory(Map<String, String> roles, Map<String, User> users) {
        this.roles = Collections.unmodifiableMap(new LinkedHashMap<>(roles));
        this.users = Collections.unmodifiableMap(new LinkedHashMap<>(users));
    }

    public User require(String userId) {
        User user = users.get(userId);
        if (user == null) {
            throw new IllegalArgumentException("пользователь не найден: " + userId);
        }
        return user;
    }

    public Optional<User> find(String userId) {
        return Optional.ofNullable(users.get(userId));
    }

    public String requireRole(String roleId) {
        String title = roles.get(roleId);
        if (title == null) {
            throw new ConfigurationException("неизвестная роль: " + roleId);
        }
        return title;
    }

    public boolean hasRole(String roleId) {
        return roles.containsKey(roleId);
    }

    public Map<String, String> roles() {
        return roles;
    }

    public Map<String, User> users() {
        return users;
    }
}
