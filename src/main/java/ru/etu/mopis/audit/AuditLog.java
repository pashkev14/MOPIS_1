package ru.etu.mopis.audit;

/**
 * Журнал обработки команд. Внедряется в сервис через конструктор, поэтому в тестах
 * подменяется на {@link MemoryLog} без изменения обработчика.
 */
@FunctionalInterface
public interface AuditLog {

    void write(AuditEntry entry);
}
