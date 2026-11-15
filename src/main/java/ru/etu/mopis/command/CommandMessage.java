package ru.etu.mopis.command;

/**
 * Команда, поступившая на обработку: тип ({@code start}/{@code action}), пользователь,
 * объект и действие (только для {@code action}).
 */
public record CommandMessage(String type, String userId, String objectId, String actionId) {

    public static final String TYPE_START = "start";
    public static final String TYPE_ACTION = "action";

    public static CommandMessage start(String userId, String objectId) {
        return new CommandMessage(TYPE_START, userId, objectId, null);
    }

    public static CommandMessage action(String userId, String objectId, String actionId) {
        return new CommandMessage(TYPE_ACTION, userId, objectId, actionId);
    }

    public boolean isStart() {
        return TYPE_START.equals(type);
    }

    public boolean isAction() {
        return TYPE_ACTION.equals(type);
    }
}
