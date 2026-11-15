package ru.etu.mopis.error;

/**
 * Исход обработки команды. Значение {@link #wireName()} попадает в журнал (поле {@code result}).
 */
public enum CommandOutcome {

    /** Команда выполнена. */
    ACCEPTED("accepted"),
    /** Команда отклонена проверкой (роль, условие, состояние и т. п.). */
    REJECTED("rejected"),
    /** Ошибка обработки: неоднозначный переход, некорректная команда, сбой конфигурации. */
    ERROR("error");

    private final String wireName;

    CommandOutcome(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    @Override
    public String toString() {
        return wireName;
    }
}
