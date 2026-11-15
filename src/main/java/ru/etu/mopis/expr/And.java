package ru.etu.mopis.expr;

import java.util.List;
import java.util.stream.Collectors;

import ru.etu.mopis.type.Obj;

/**
 * Конъюнкция: между предикатами дизъюнкта ДНФ отношение «И».
 */
public final class And implements Expression {

    private final List<Expression> items;

    public And(List<Expression> items) {
        this.items = List.copyOf(items);
    }

    public List<Expression> items() {
        return items;
    }

    @Override
    public boolean eval(Obj obj, EvalContext ctx) {
        for (Expression item : items) {
            if (!item.eval(obj, ctx)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return items.stream().map(Object::toString).collect(Collectors.joining(" AND ", "(", ")"));
    }
}
