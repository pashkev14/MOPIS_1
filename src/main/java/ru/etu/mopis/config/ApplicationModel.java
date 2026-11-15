package ru.etu.mopis.config;

import com.fasterxml.jackson.databind.JsonNode;

import ru.etu.mopis.type.ClassRegistry;
import ru.etu.mopis.type.ObjectRepository;
import ru.etu.mopis.user.UserDirectory;
import ru.etu.mopis.fsm.TemplateCatalog;

/**
 * Полностью загруженная модель приложения: реестры типов, объектов, пользователей
 * и шаблонов процессов. Создаётся один раз при старте.
 */
public record ApplicationModel(ClassRegistry classes,
                               ObjectRepository objects,
                               UserDirectory users,
                               TemplateCatalog templates) {
}
