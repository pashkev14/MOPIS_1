package ru.etu.mopis.command;

import ru.etu.mopis.error.CommandOutcome;
import ru.etu.mopis.error.RejectedException;
import ru.etu.mopis.fsm.ProcessEngine;
import ru.etu.mopis.fsm.ProcessInstance;
import ru.etu.mopis.user.User;

/**
 * Команда запуска процесса над существующим объектом.
 */
public final class StartCommand implements Command {

    private final ProcessEngine engine;
    private final String userId;
    private final String objectId;

    public StartCommand(ProcessEngine engine, String userId, String objectId) {
        this.engine = engine;
        this.userId = userId;
        this.objectId = objectId;
    }

    public String userId() {
        return userId;
    }

    public String objectId() {
        return objectId;
    }

    @Override
    public CommandResult execute() {
        User user = engine.users().find(userId).orElseThrow(
                () -> new RejectedException("пользователь не найден: " + userId));
        ProcessInstance instance = engine.start(user, objectId);
        return new CommandResult(CommandOutcome.ACCEPTED, null, instance.stateId(), null,
                "процесс " + instance.processId() + " запущен");
    }
}
