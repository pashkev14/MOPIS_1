package ru.etu.mopis.error;

/**
 * Ошибка: по паре (состояние, действие) подходит более одного перехода.
 * Выбирать первый попавшийся переход запрещено, состояние не меняется.
 */
public class AmbiguousTransitionException extends CommandException {

    public AmbiguousTransitionException(String reason) {
        super(CommandOutcome.ERROR, reason);
    }
}
