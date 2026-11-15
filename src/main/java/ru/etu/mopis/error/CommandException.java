package ru.etu.mopis.error;

/**
 * Исключение обработки команды: несёт исход и понятную причину для журнала.
 */
public class CommandException extends RuntimeException {

    private final CommandOutcome outcome;

    public CommandException(CommandOutcome outcome, String reason) {
        super(reason);
        this.outcome = outcome;
    }

    public CommandException(CommandOutcome outcome, String reason, Throwable cause) {
        super(reason, cause);
        this.outcome = outcome;
    }

    public CommandOutcome outcome() {
        return outcome;
    }

    public String reason() {
        return getMessage();
    }
}
