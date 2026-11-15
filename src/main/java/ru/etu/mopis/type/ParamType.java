package ru.etu.mopis.type;

import ru.etu.mopis.error.ConfigurationException;

/**
 * Тип параметра класса. Механизм составности не реализуется, поэтому типов ровно три.
 */
public enum ParamType {

    INTEGER("integer"),
    DECIMAL("decimal"),
    STRING("string");

    private final String wireName;

    ParamType(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    public boolean isNumeric() {
        return this != STRING;
    }

    /**
     * Разбор типа из конфигурации. Неизвестный тип — понятная ошибка загрузки.
     */
    public static ParamType fromWire(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ConfigurationException("не задан тип параметра");
        }
        String normalized = raw.trim().toLowerCase(java.util.Locale.ROOT);
        for (ParamType type : values()) {
            if (type.wireName.equals(normalized)) {
                return type;
            }
        }
        throw new ConfigurationException("неизвестный тип параметра: " + raw);
    }
}
