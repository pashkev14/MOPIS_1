package ru.etu.mopis.command;

import ru.etu.mopis.audit.AuditEntry;
import ru.etu.mopis.audit.AuditLog;
import ru.etu.mopis.error.CommandException;
import ru.etu.mopis.error.CommandOutcome;
import ru.etu.mopis.fsm.ProcessEngine;

/**
 * Единственная точка обработки команд и записи в журнал: на каждую обработанную команду
 * (успех, отказ, ошибка) ровно одна запись.
 */
public final class ProcessService {

    private final ProcessEngine engine;
    private final AuditLog audit;

    public ProcessService(ProcessEngine engine, AuditLog audit) {
        this.engine = engine;
        this.audit = audit;
    }

    public CommandResult send(CommandMessage message) {
        String stateBefore = message == null ? null : engine.stateOf(message.objectId());
        CommandResult result;
        try {
            Command command = createCommand(message);
            result = command.execute();
        } catch (CommandException e) {
            result = failure(message, e.outcome(), e.reason(), stateBefore);
        } catch (RuntimeException e) {
            result = failure(message, CommandOutcome.ERROR,
                    "ошибка обработки команды: " + describe(e), stateBefore);
        }

        audit.write(new AuditEntry(
                message == null ? null : message.type(),
                message == null ? null : message.userId(),
                message == null ? null : message.objectId(),
                message == null ? null : message.actionId(),
                result.stateBefore(),
                result.stateAfter(),
                result.outcome(),
                result.reason()));
        return result;
    }

    private Command createCommand(CommandMessage message) {
        if (message == null) {
            throw new ru.etu.mopis.error.RejectedException("команда не задана");
        }
        if (message.userId() == null || message.userId().isBlank()) {
            throw new ru.etu.mopis.error.RejectedException("не задан пользователь команды");
        }
        if (message.objectId() == null || message.objectId().isBlank()) {
            throw new ru.etu.mopis.error.RejectedException("не задан объект команды");
        }
        if (message.isStart()) {
            return new StartCommand(engine, message.userId(), message.objectId());
        }
        if (message.isAction()) {
            if (message.actionId() == null || message.actionId().isBlank()) {
                throw new ru.etu.mopis.error.RejectedException("не задано действие команды");
            }
            return new ActionCommand(engine, message.userId(), message.objectId(), message.actionId());
        }
        throw new ru.etu.mopis.error.CommandException(CommandOutcome.ERROR,
                "неизвестный тип команды: " + message.type());
    }

    /**
     * Отказ/ошибка не меняет состояние: {@code state_after} совпадает с {@code state_before}.
     */
    private CommandResult failure(CommandMessage message, CommandOutcome outcome, String reason, String stateBefore) {
        String current = engine.stateOf(message == null ? null : message.objectId());
        String stateAfter = current != null ? current : stateBefore;
        return new CommandResult(outcome, stateBefore, stateAfter, reason, reason);
    }

    private static String describe(RuntimeException e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }

    public ProcessEngine engine() {
        return engine;
    }
}
