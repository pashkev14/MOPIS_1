package ru.etu.mopis.type;

import java.math.BigDecimal;
import java.util.Optional;

import ru.etu.mopis.error.ConfigurationException;

/**
 * Описание параметра класса (Type Object).
 *
 * <p>Неизменяемое общее описание: создаётся один раз при загрузке конфигурации.
 * {@code min} / {@code max} и {@code defaultValue} необязательны; незаданная граница
 * не проверяется, а {@code default} подставляется только если поле пропущено в объекте.</p>
 */
public final class Param {

    private final String id;
    private final String title;
    private final ParamType type;
    private final BigDecimal min;
    private final BigDecimal max;
    private final Object defaultValue;
    private final boolean hasDefault;

    public Param(String id,
                 String title,
                 ParamType type,
                 BigDecimal min,
                 BigDecimal max,
                 Object defaultValue,
                 boolean hasDefault) {
        this.id = id;
        this.title = title;
        this.type = type;
        this.min = min;
        this.max = max;
        this.defaultValue = defaultValue;
        this.hasDefault = hasDefault;
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    public ParamType type() {
        return type;
    }

    public Optional<BigDecimal> min() {
        return Optional.ofNullable(min);
    }

    public Optional<BigDecimal> max() {
        return Optional.ofNullable(max);
    }

    public Optional<Object> defaultValue() {
        return Optional.ofNullable(defaultValue);
    }

    public boolean hasDefault() {
        return hasDefault;
    }

    /**
     * Приведение «сырого» значения из YAML к типу параметра.
     *
     * @throws ConfigurationException если значение не приводится к объявленному типу
     */
    public Object coerce(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return switch (type) {
                case INTEGER -> toLong(raw);
                case DECIMAL -> toBigDecimal(raw);
                case STRING -> String.valueOf(raw);
            };
        } catch (NumberFormatException e) {
            throw new ConfigurationException(
                    "значение параметра '" + id + "' не соответствует типу " + type.wireName() + ": " + raw, e);
        }
    }

    /**
     * Действующее значение по умолчанию, приведённое к типу параметра.
     */
    public Object effectiveDefault() {
        return hasDefault ? coerce(defaultValue) : null;
    }

    private static Long toLong(Object raw) {
        if (raw instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(raw).trim());
    }

    private static BigDecimal toBigDecimal(Object raw) {
        if (raw instanceof BigDecimal decimal) {
            return decimal;
        }
        if (raw instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        return new BigDecimal(String.valueOf(raw).trim());
    }

    @Override
    public String toString() {
        return "Param[" + id + ":" + type.wireName() + "]";
    }
}
