package ua.edu.chnu.awards.analytics.export;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.function.Function;

import ua.edu.chnu.awards.analytics.dto.Figure;
import ua.edu.chnu.awards.award.dto.YearRange;

/**
 * Number, change and bar rules of the board report.
 */
final class BoardFormatHelper {

    static final String NONE = "—";

    private static final String MINUS = "−";
    private static final int FULL = 100;

    private BoardFormatHelper() {
    }

    static String change(long delta, BigDecimal percent) {
        String change = signed(BigDecimal.valueOf(delta));
        return percent == null ? change : change + " (" + signed(percent) + " %)";
    }

    static String change(Figure figure) {
        return change(figure.delta(), figure.deltaPercent());
    }

    static List<String> counts(long current, long previous, long delta, BigDecimal percent) {
        return List.of(String.valueOf(current), String.valueOf(previous), change(delta, percent));
    }

    static List<String> counts(long current, long previous) {
        Figure figure = Figure.of(current, previous);
        return counts(current, previous, figure.delta(), figure.deltaPercent());
    }

    static String decimal(BigDecimal value) {
        return value == null ? NONE : value.toPlainString();
    }

    static String years(YearRange range) {
        return range.from() == range.to() ? String.valueOf(range.from()) : range.from() + "–" + range.to();
    }

    static <T> List<BoardReport.Bar> bars(List<T> items, Function<T, String> label, Function<T, BigDecimal> value) {
        BigDecimal largest = items.stream().map(value).reduce(BigDecimal.ZERO, BigDecimal::max);
        return items.stream().map(item -> new BoardReport.Bar(label.apply(item), decimal(value.apply(item)),
            width(value.apply(item), largest))).toList();
    }

    private static int width(BigDecimal value, BigDecimal largest) {
        if (largest.signum() == 0) {
            return 0;
        }
        return value.multiply(BigDecimal.valueOf(FULL)).divide(largest, 0, RoundingMode.HALF_UP).intValue();
    }

    private static String signed(BigDecimal value) {
        String plain = value.abs().toPlainString();
        return switch (value.signum()) {
            case 1 -> "+" + plain;
            case -1 -> MINUS + plain;
            default -> plain;
        };
    }
}
