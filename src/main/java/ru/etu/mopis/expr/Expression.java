package ru.etu.mopis.expr;

import ru.etu.mopis.type.Obj;

/**
 * Узел дерева условий перехода (паттерн Interpreter).
 *
 * <p>Дерево строится один раз при загрузке конфигурации, при каждой команде только вычисляется.
 * Выражение <b>никогда</b> не бросает исключений из-за данных объекта.</p>
 */
@FunctionalInterface
public interface Expression {

    boolean eval(Obj obj, EvalContext ctx);
}
