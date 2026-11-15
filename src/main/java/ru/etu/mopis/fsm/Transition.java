package ru.etu.mopis.fsm;

import ru.etu.mopis.expr.EvalContext;
import ru.etu.mopis.expr.Expression;
import ru.etu.mopis.type.Obj;

/**
 * Переход шаблона: исходное состояние, действие, целевое состояние и условие в ДНФ.
 * Условие компилируется один раз при загрузке; при команде только вычисляется.
 */
public final class Transition {

    private final String sourceId;
    private final String actionId;
    private final String targetId;
    private final Expression condition;

    public Transition(String sourceId, String actionId, String targetId, Expression condition) {
        this.sourceId = sourceId;
        this.actionId = actionId;
        this.targetId = targetId;
        this.condition = condition;
    }

    public String sourceId() {
        return sourceId;
    }

    public String actionId() {
        return actionId;
    }

    public String targetId() {
        return targetId;
    }

    public Expression condition() {
        return condition;
    }

    public boolean isApplicable(Obj obj, EvalContext ctx) {
        return condition.eval(obj, ctx);
    }

    @Override
    public String toString() {
        return sourceId + " --" + actionId + "--> " + targetId + " if " + condition;
    }
}
