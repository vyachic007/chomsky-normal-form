package by.slava_borisov.chomsky_normal_form;

import java.util.List;

public class GrammarFormatter {

    private static final String EPS = "ε";

    public static String formatRightCompact(List<String> right) {
        if (right.isEmpty()) {
            return EPS;
        }

        return String.join("", right);
    }
}