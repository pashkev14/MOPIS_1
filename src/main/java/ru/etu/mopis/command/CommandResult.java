package ru.etu.mopis.command;

import ru.etu.mopis.error.CommandOutcome;

/**
 * Результат команды для вызывающей стороны.
 *
 * @param outcome     исход обработки
 * @param stateBefore состояние до обработки ({@code null}, если процесса не было)
 * @param stateAfter  состояние после обработки
 * @param reason      причина отказа/ошибки ({@code null} при успехе)
 * @param message     краткое человекочитаемое сообщение
 */
public record CommandResult(CommandOutcome outcome,
                            String stateBefore,
                            String stateAfter,
                            String reason,
                            String message) {

    public boolean accepted() {
        return outcome == CommandOutcome.ACCEPTED;
    }
}
