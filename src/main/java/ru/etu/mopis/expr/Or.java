package ru.etu.mopis.expr;

import java.util.List;
import java.util.stream.Collectors;

import ru.etu.mopis.type.Obj;

/**
 * Дизъюнкция: между дизъюнктами ДНФ отношение «ИЛИ».
 */
public final class Or implements Expression {

    private final List<Expression> items;

    public Or(List<Expression> items) {
        this.items = List.copyOf(items);
    }

    public List<Expression> items() {
        return items;
    }

    @Override
    public boolean eval(Obj obj, EvalContext ctx) {
        for (Expression item : items) {
            if (item.eval(obj, ctx)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return items.stream().map(Object::toString).collect(Collectors.joining(" OR ", "(", ")"));
    }
}
