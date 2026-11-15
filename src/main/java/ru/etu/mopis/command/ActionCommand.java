package ru.etu.mopis.command;

import ru.etu.mopis.error.CommandOutcome;
import ru.etu.mopis.error.RejectedException;
import ru.etu.mopis.fsm.ProcessEngine;
import ru.etu.mopis.fsm.ProcessInstance;
import ru.etu.mopis.user.User;

/**
 * Команда выполнения действия над запущенным процессом.
 */
public final class ActionCommand implements Command {

    private final ProcessEngine engine;
    private final String userId;
    private final String objectId;
    private final String actionId;

    public ActionCommand(ProcessEngine engine, String userId, String objectId, String actionId) {
        this.engine = engine;
        this.userId = userId;
        this.objectId = objectId;
        this.actionId = actionId;
    }

    public String userId() {
        return userId;
    }

    public String objectId() {
        return objectId;
    }

    public String actionId() {
        return actionId;
    }

    @Override
    public CommandResult execute() {
        String stateBefore = engine.stateOf(objectId);
        User user = engine.users().find(userId).orElseThrow(
                () -> new RejectedException("пользователь не найден: " + userId));
        ProcessInstance instance = engine.act(user, objectId, actionId);
        return new CommandResult(CommandOutcome.ACCEPTED, stateBefore, instance.stateId(), null,
                "переход выполнен");
    }
}
