package ru.etu.mopis.audit;

import ru.etu.mopis.error.CommandOutcome;

/**
 * Одна запись журнала. Поля и их порядок фиксированы {@code spec.md} §5.5:
 * {@code command}, {@code user}, {@code object}, {@code state_before}, {@code state_after},
 * {@code result}, {@code reason}.
 *
 * @param commandType тип команды: {@code start} или {@code action}
 * @param userId      пользователь из команды
 * @param objectId    объект из команды
 * @param actionId    действие (только для {@code action}), иначе {@code null}
 * @param stateBefore состояние до обработки или {@code null}
 * @param stateAfter  состояние после обработки
 * @param result      исход обработки
 * @param reason      причина отказа/ошибки, {@code null} при успехе
 */
public record AuditEntry(String commandType,
                         String userId,
                         String objectId,
                         String actionId,
                         String stateBefore,
                         String stateAfter,
                         CommandOutcome result,
                         String reason) {
}
