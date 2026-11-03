package by.slava_borisov.chomsky_normal_form;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GrammarParser {

    private static final Pattern TOKEN_PATTERN =
            Pattern.compile(":=|[A-Z][0-9]*|[a-z]+|[^\\s]");

    public static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        Matcher matcher = TOKEN_PATTERN.matcher(text);

        while (matcher.find()) {
            tokens.add(matcher.group());
        }

        return tokens;
    }
}