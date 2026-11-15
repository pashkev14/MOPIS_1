package ru.etu.mopis.error;

/**
 * Ошибка загрузки/проверки конфигурации (fail-fast, понятное сообщение).
 */
public class ConfigurationException extends RuntimeException {

    public ConfigurationException(String message) {
        super(message);
    }

    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
