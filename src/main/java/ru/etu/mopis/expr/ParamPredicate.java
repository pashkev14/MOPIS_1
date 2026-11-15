package ru.etu.mopis.expr;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import ru.etu.mopis.error.ConfigurationException;
import ru.etu.mopis.type.Obj;
import ru.etu.mopis.type.Param;

/**
 * Предикат параметра: левый операнд — значение параметра объекта, правый — литерал из конфигурации.
 *
 * <p>Семантика ({@code spec.md} §5.2): значение {@code null}, а также параметр, отсутствующий
 * у класса объекта, дают {@code false} для <b>любого</b> параметрического предиката, включая
 * {@code filled}. Оператор {@code !=} не является исключением: при {@code null} он тоже
 * возвращает {@code false} (отрицание выполняется узлом {@link Not}).</p>
 *
 * <p>Описание параметра (границы для {@code in_range}) берётся из <b>действующего</b> набора
 * класса объекта, а не из класса, где параметр объявлен впервые.</p>
 */
public final class ParamPredicate implements Expression {

    public static final String FILLED = "filled";
    public static final String IN_RANGE = "in_range";
    public static final String EQ = "=";
    public static final String NE = "!=";
    public static final String LT = "<";
    public static final String LE = "<=";
    public static final String GT = ">";
    public static final String GE = ">=";

    private static final Set<String> SUPPORTED =
            Set.of(FILLED, IN_RANGE, EQ, NE, LT, LE, GT, GE);

    private final String paramId;
    private final String operator;
    private final Object operand;

    public ParamPredicate(String paramId, String operator, Object operand) {
        this.paramId = paramId;
        this.operator = normalize(operator);
        if (!SUPPORTED.contains(this.operator)) {
            throw new ConfigurationException("неподдерживаемый оператор предиката: " + operator);
        }
        if (!FILLED.equals(this.operator) && !IN_RANGE.equals(this.operator) && operand == null) {
            throw new ConfigurationException(
                    "оператор '" + this.operator + "' требует значение (параметр '" + paramId + "')");
        }
        this.operand = operand;
    }

    public String paramId() {
        return paramId;
    }

    public String operator() {
        return operator;
    }

    public Object operand() {
        return operand;
    }

    @Override
    public boolean eval(Obj obj, EvalContext ctx) {
        if (obj == null) {
            return false;
        }
        Optional<Param> param = ctx.classes().param(obj.classId(), paramId);
        if (param.isEmpty()) {
            // Параметра нет у класса объекта — предикат ложен для любого оператора.
            return false;
        }
        Object value = obj.value(paramId);
        if (value == null) {
            return false;
        }
        return switch (operator) {
            case FILLED -> true; // непустое присутствие значения: пустая строка считается заданной
            case EQ -> equalsValue(value, operand, param.get());
            case NE -> !equalsValue(value, operand, param.get());
            case LT, LE, GT, GE -> compare(value, operand, operator);
            case IN_RANGE -> inRange(value, param.get());
            default -> false;
        };
    }

    private static boolean equalsValue(Object value, Object operand, Param param) {
        if (value instanceof Number number) {
            BigDecimal left = toDecimal(number);
            BigDecimal right = toDecimalOrNull(operand);
            if (left != null && right != null) {
                return left.compareTo(right) == 0;
            }
        }
        return String.valueOf(value).equals(String.valueOf(operand));
    }

    private static boolean compare(Object value, Object operand, String operator) {
        BigDecimal left = toDecimalOrNull(value);
        BigDecimal right = toDecimalOrNull(operand);
        if (left == null || right == null) {
            // Сравнение порядка определено только для чисел.
            return false;
        }
        int cmp = left.compareTo(right);
        return switch (operator) {
            case LT -> cmp < 0;
            case LE -> cmp <= 0;
            case GT -> cmp > 0;
            case GE -> cmp >= 0;
            default -> false;
        };
    }

    private static boolean inRange(Object value, Param param) {
        if (!param.type().isNumeric()) {
            return false;
        }
        BigDecimal current = toDecimalOrNull(value);
        if (current == null) {
            return false;
        }
        Optional<BigDecimal> min = param.min();
        Optional<BigDecimal> max = param.max();
        if (min.isEmpty() && max.isEmpty()) {
            return true;
        }
        // Незаданная граница не проверяется.
        boolean aboveMin = min.isEmpty() || current.compareTo(min.get()) >= 0;
        boolean belowMax = max.isEmpty() || current.compareTo(max.get()) <= 0;
        return aboveMin && belowMax;
    }

    private static BigDecimal toDecimalOrNull(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof BigDecimal decimal) {
            return decimal;
        }
        if (raw instanceof Number number) {
            return toDecimal(number);
        }
        try {
            return new BigDecimal(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal toDecimal(Number number) {
        return number instanceof BigDecimal decimal ? decimal : new BigDecimal(number.toString());
    }

    private static String normalize(String operator) {
        if (operator == null) {
            throw new ConfigurationException("не задан оператор предиката");
        }
        return operator.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return operand == null
                ? paramId + " " + operator
                : paramId + " " + operator + " " + operand;
    }
}
