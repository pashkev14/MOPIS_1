package ru.etu.mopis.audit;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Журнал JSONL: один JSON-объект на строку. Файл открывается в режиме перезаписи
 * при старте приложения и пишется с {@code flush()} после каждой команды.
 */
public final class JsonLinesLog implements AuditLog, AutoCloseable {

    private final Path path;
    private final ObjectMapper mapper;
    private final Writer writer;

    public JsonLinesLog(Path path, ObjectMapper mapper) {
        this.path = path;
        this.mapper = mapper;
        try {
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            this.writer = new BufferedWriter(Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING));
        } catch (IOException e) {
            throw new IllegalStateException("не удалось открыть журнал " + path, e);
        }
    }

    @Override
    public void write(AuditEntry entry) {
        try {
            String json;
            try (java.io.StringWriter buffer = new java.io.StringWriter()) {
                // ORDER_MAP_ENTRIES_BY_KEYS отключён: порядок полей задаётся кодом ниже.
                try (JsonGenerator generator = mapper.getFactory().createGenerator(buffer)) {
                    writeEntry(generator, entry);
                }
                json = buffer.toString();
            }
            writer.write(json);
            writer.write('\n');
            writer.flush();
        } catch (IOException e) {
            throw new IllegalStateException("не удалось записать запись журнала в " + path, e);
        }
    }

    private static void writeEntry(JsonGenerator generator, AuditEntry entry) throws IOException {
        generator.writeStartObject();

        generator.writeFieldName("command");
        generator.writeStartObject();
        generator.writeStringField("type", entry.commandType());
        generator.writeStringField("user", entry.userId());
        generator.writeStringField("object", entry.objectId());
        if ("action".equals(entry.commandType())) {
            generator.writeStringField("action", entry.actionId());
        }
        generator.writeEndObject();

        generator.writeStringField("user", entry.userId());
        generator.writeStringField("object", entry.objectId());
        generator.writeStringField("state_before", entry.stateBefore());
        generator.writeStringField("state_after", entry.stateAfter());
        generator.writeStringField("result", entry.result() == null ? null : entry.result().wireName());
        generator.writeStringField("reason", entry.reason());

        generator.writeEndObject();
    }

    public Path path() {
        return path;
    }

    @Override
    public void close() {
        try {
            writer.close();
        } catch (IOException ignored) {
            // Закрытие журнала не должно влиять на результат команд.
        }
    }
}
