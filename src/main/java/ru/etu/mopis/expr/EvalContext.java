package ru.etu.mopis.expr;

import ru.etu.mopis.type.ClassRegistry;
import ru.etu.mopis.type.ObjectRepository;

/**
 * Контекст вычисления предиката: доступ к описаниям классов (действующие параметры
 * после наследования) и к готовым объектам.
 */
public final class EvalContext {

    private final ClassRegistry classes;
    private final ObjectRepository objects;

    public EvalContext(ClassRegistry classes, ObjectRepository objects) {
        this.classes = classes;
        this.objects = objects;
    }

    public ClassRegistry classes() {
        return classes;
    }

    public ObjectRepository objects() {
        return objects;
    }
}
