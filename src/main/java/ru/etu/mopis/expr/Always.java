package ru.etu.mopis.expr;

import ru.etu.mopis.type.Obj;

/**
 * Переход без условия: разрешён всегда.
 */
public final class Always implements Expression {

    public static final Always INSTANCE = new Always();

    public Always() {
    }

    @Override
    public boolean eval(Obj obj, EvalContext ctx) {
        return true;
    }

    @Override
    public String toString() {
        return "ALWAYS";
    }
}
