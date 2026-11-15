package ru.etu.mopis.error;

/**
 * Отказ: команда недопустима, состояние процесса не изменяется.
 */
public class RejectedException extends CommandException {

    public RejectedException(String reason) {
        super(CommandOutcome.REJECTED, reason);
    }
}
