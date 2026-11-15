package ru.etu.mopis.audit;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Журнал в памяти — используется в тестах; обработчик при этом не меняется.
 */
public final class MemoryLog implements AuditLog {

    private final List<AuditEntry> entries = new CopyOnWriteArrayList<>();

    @Override
    public void write(AuditEntry entry) {
        entries.add(entry);
    }

    public List<AuditEntry> entries() {
        return Collections.unmodifiableList(entries);
    }

    public AuditEntry last() {
        return entries.isEmpty() ? null : entries.get(entries.size() - 1);
    }

    public void clear() {
        entries.clear();
    }

    public int size() {
        return entries.size();
    }
}
