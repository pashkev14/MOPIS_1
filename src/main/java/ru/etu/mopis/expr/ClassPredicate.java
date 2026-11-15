package ru.etu.mopis.expr;

import ru.etu.mopis.type.ClassRegistry;
import ru.etu.mopis.type.Obj;

/**
 * Предикат класса: объект принадлежит указанному классу или его подклассу.
 * Без наследования сводится к точному совпадению, здесь наследование поддержано.
 */
public final class ClassPredicate implements Expression {

    private final String targetClassId;

    public ClassPredicate(String targetClassId) {
        this.targetClassId = targetClassId;
    }

    public String targetClassId() {
        return targetClassId;
    }

    @Override
    public boolean eval(Obj obj, EvalContext ctx) {
        if (obj == null) {
            return false;
        }
        ClassRegistry classes = ctx.classes();
        if (classes.find(obj.classId()).isEmpty()) {
            return false;
        }
        return classes.isInstanceOf(obj.classId(), targetClassId);
    }

    @Override
    public String toString() {
        return "class(" + targetClassId + ")";
    }
}
