package ru.etu.mopis.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import ru.etu.mopis.error.ConfigurationException;
import ru.etu.mopis.expr.Always;
import ru.etu.mopis.expr.And;
import ru.etu.mopis.expr.Expression;
import ru.etu.mopis.expr.Not;
import ru.etu.mopis.expr.Or;

/**
 * Компиляция условия перехода из ДНФ в дерево выражений.
 *
 * <pre>
 * condition: [[a, "not b"], [c]]   →   Or( And(a, Not(b)), And(c) )
 * condition отсутствует            →   Always
 * [[a]]                            →   And(a)
 * </pre>
 *
 * <p>Дерево строится один раз при загрузке; при каждой команде только вычисляется.
 * Неизвестный идентификатор предиката — понятная ошибка конфигурации, а не подмена.</p>
 */
public final class ConditionCompiler {

    private static final String NOT_PREFIX = "not ";

    private final Map<String, Expression> predicates;

    public ConditionCompiler(Map<String, Expression> predicates) {
        this.predicates = Map.copyOf(predicates);
    }

    /**
     * @param condition узел условия (массив массивов) или {@code null}
     */
    public Expression compile(JsonNode condition) {
        if (condition == null || condition.isNull() || condition.isMissingNode()) {
            return new Always();
        }
        if (!condition.isArray()) {
            throw new ConfigurationException("условие перехода должно быть списком дизъюнктов");
        }
        if (condition.isEmpty()) {
            return new Always();
        }

        List<Expression> disjuncts = new ArrayList<>();
        for (JsonNode clause : condition) {
            disjuncts.add(compileClause(clause));
        }
        return disjuncts.size() == 1 ? disjuncts.getFirst() : new Or(disjuncts);
    }

    private Expression compileClause(JsonNode clause) {
        if (clause.isTextual()) {
            // Допускается плоская запись одного дизъюнкта: condition: [a, b].
            return compilePredicate(clause.asText());
        }
        if (!clause.isArray()) {
            throw new ConfigurationException("дизъюнкт условия должен быть списком предикатов");
        }
        List<Expression> conjuncts = new ArrayList<>();
        for (JsonNode item : clause) {
            if (!item.isTextual()) {
                throw new ConfigurationException("элемент дизъюнкта должен быть идентификатором предиката");
            }
            conjuncts.add(compilePredicate(item.asText()));
        }
        if (conjuncts.isEmpty()) {
            return new Always();
        }
        return conjuncts.size() == 1 ? conjuncts.getFirst() : new And(conjuncts);
    }

    private Expression compilePredicate(String raw) {
        String token = raw == null ? "" : raw.trim();
        if (token.isEmpty()) {
            throw new ConfigurationException("пустой идентификатор предиката в условии перехода");
        }
        boolean negated = false;
        if (token.regionMatches(true, 0, NOT_PREFIX, 0, NOT_PREFIX.length())) {
            negated = true;
            token = token.substring(NOT_PREFIX.length()).trim();
        }
        Expression predicate = predicates.get(token);
        if (predicate == null) {
            throw new ConfigurationException("неизвестный предикат в условии перехода: " + token);
        }
        return negated ? new Not(predicate) : predicate;
    }
}
