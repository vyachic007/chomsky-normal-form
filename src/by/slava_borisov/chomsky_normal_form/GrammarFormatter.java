package by.slava_borisov.chomsky_normal_form;

import java.util.Collection;
import java.util.List;

public class GrammarFormatter {

    private static final String EPS = "ε";

    /// Список символов преобразуем в строку для вывода на экран
    public static String formatRightCompact(List<String> right) {
        if (right.isEmpty()) {
            return EPS;
        }

        return String.join("", right);
    }

    // Выводит элементы коллекции в квадратных скобках через запятую.
    public static String formatSet(Collection<String> values) {
        return "[" + String.join(", ", values) + "]";
    }
}