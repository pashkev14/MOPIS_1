package ru.etu.mopis.expr;

import ru.etu.mopis.type.Obj;

/**
 * Отрицание одного элемента условия. Инвертирует уже полученный булев результат.
 */
public final class Not implements Expression {

    private final Expression inner;

    public Not(Expression inner) {
        this.inner = inner;
    }

    public Expression inner() {
        return inner;
    }

    @Override
    public boolean eval(Obj obj, EvalContext ctx) {
        return !inner.eval(obj, ctx);
    }

    @Override
    public String toString() {
        return "NOT " + inner;
    }
}
