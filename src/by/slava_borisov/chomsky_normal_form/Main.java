package by.slava_borisov.chomsky_normal_form;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {

    private static final String EPS = "ε";

    private static final Pattern TOKEN_PATTERN =
            Pattern.compile(":=|[A-Z][0-9]*|[a-z]+|[^\\s]");

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("Введите грамматику:");

        Grammar grammar = new Grammar();

        while (scanner.hasNextLine()) {
            String line = scanner.nextLine().trim();

            if (line.isEmpty()) {
                break;
            }

            try {
                grammar.addRule(line);
            } catch (IllegalArgumentException exception) {
                System.out.println("Ошибка: " + exception.getMessage());
                return;
            }
        }

        if (grammar.isEmpty()) {
            System.out.println("Грамматика не введена.");
            return;
        }

        try {
            grammar.validate();

            System.out.println();
            System.out.println("Исходная грамматика:");
            grammar.print();

            Grammar result = grammar.removeEmpty();
            result = result.removeUnit();
            result = result.removeUseless();
            result = result.replaceTerminals();
            result = result.binarize();

            System.out.println();
            System.out.println("Итоговая грамматика в НФХ:");
            result.print();

        } catch (IllegalArgumentException exception) {
            System.out.println("Ошибка: " + exception.getMessage());
        }
    }

    private static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        Matcher matcher = TOKEN_PATTERN.matcher(text);

        while (matcher.find()) {
            tokens.add(matcher.group());
        }

        return tokens;
    }

    private static String formatRightCompact(List<String> right) {
        if (right.isEmpty()) {
            return EPS;
        }

        return String.join("", right);
    }

    private static String formatSet(Collection<String> values) {
        return "[" + String.join(", ", values) + "]";
    }

    private static class Grammar {

        private final LinkedHashMap<String, LinkedHashSet<List<String>>> rules;
        private String start;

        public Grammar() {
            rules = new LinkedHashMap<>();
        }

        private Grammar(
                String start,
                LinkedHashMap<String, LinkedHashSet<List<String>>> rules
        ) {
            this.start = start;
            this.rules = rules;
        }

        public boolean isEmpty() {
            return rules.isEmpty();
        }

        public void addRule(String line) {
            String[] parts = line.split("->", 2);

            if (parts.length != 2) {
                throw new IllegalArgumentException(
                        "Правило должно иметь формат A -> α."
                );
            }

            String left = parts[0].trim();

            if (!left.matches("[A-Z][0-9]*")) {
                throw new IllegalArgumentException(
                        "Левая часть должна быть нетерминалом."
                );
            }

            if (start == null) {
                start = left;
            }

            LinkedHashSet<List<String>> alternatives =
                    rules.computeIfAbsent(
                            left,
                            key -> new LinkedHashSet<>()
                    );

            String rightText = parts[1].trim();

            if (rightText.isEmpty()) {
                throw new IllegalArgumentException(
                        "Правая часть не может быть пустой. Используйте ε."
                );
            }

            String[] variants = rightText.split("\\|");

            for (String variant : variants) {
                String trimmed = variant.trim();

                if (trimmed.isEmpty() || trimmed.equals(EPS)) {
                    alternatives.add(new ArrayList<>());
                } else {
                    alternatives.add(tokenize(trimmed));
                }
            }
        }

        public void validate() {
            Set<String> nonTerminals = rules.keySet();

            for (Map.Entry<String, LinkedHashSet<List<String>>> entry
                    : rules.entrySet()) {

                for (List<String> right : entry.getValue()) {
                    for (String symbol : right) {
                        if (isNonTerminal(symbol)
                                && !nonTerminals.contains(symbol)) {

                            throw new IllegalArgumentException(
                                    "Для нетерминала " + symbol
                                            + " отсутствует правило."
                            );
                        }
                    }
                }
            }
        }

        private boolean isNonTerminal(String symbol) {
            return symbol.matches("[A-Z][0-9]*");
        }

        private boolean isUnit(List<String> right) {
            return right.size() == 1
                    && isNonTerminal(right.get(0));
        }

        private Grammar copy() {
            LinkedHashMap<String, LinkedHashSet<List<String>>> copiedRules =
                    new LinkedHashMap<>();

            for (Map.Entry<String, LinkedHashSet<List<String>>> entry
                    : rules.entrySet()) {

                LinkedHashSet<List<String>> copiedAlternatives =
                        new LinkedHashSet<>();

                for (List<String> right : entry.getValue()) {
                    copiedAlternatives.add(new ArrayList<>(right));
                }

                copiedRules.put(entry.getKey(), copiedAlternatives);
            }

            return new Grammar(start, copiedRules);
        }

        private Set<String> getAllSymbols() {
            Set<String> symbols = new LinkedHashSet<>(rules.keySet());

            for (Set<List<String>> alternatives : rules.values()) {
                for (List<String> right : alternatives) {
                    symbols.addAll(right);
                }
            }

            return symbols;
        }

        private String createFreshNonTerminal(String prefix) {
            Set<String> usedSymbols = getAllSymbols();

            int index = 0;
            String candidate;

            do {
                candidate = prefix + index;
                index++;
            } while (usedSymbols.contains(candidate));

            return candidate;
        }

        private String createFreshNonTerminal(
                String prefix,
                Set<String> reservedSymbols
        ) {
            int index = 0;
            String candidate;

            do {
                candidate = prefix + index;
                index++;
            } while (reservedSymbols.contains(candidate));

            reservedSymbols.add(candidate);
            return candidate;
        }

        public Grammar removeEmpty() {
            System.out.println();
            System.out.println("Этап 1. Удаление ε-продукций");

            Set<String> nullable = new LinkedHashSet<>();

            for (Map.Entry<String, LinkedHashSet<List<String>>> entry
                    : rules.entrySet()) {

                for (List<String> right : entry.getValue()) {
                    if (right.isEmpty()) {
                        nullable.add(entry.getKey());
                    }
                }
            }

            boolean changed;

            do {
                changed = false;

                for (Map.Entry<String, LinkedHashSet<List<String>>> entry
                        : rules.entrySet()) {

                    String left = entry.getKey();

                    if (nullable.contains(left)) {
                        continue;
                    }

                    for (List<String> right : entry.getValue()) {
                        if (!right.isEmpty()
                                && right.stream().allMatch(nullable::contains)) {

                            nullable.add(left);
                            changed = true;
                            break;
                        }
                    }
                }
            } while (changed);

            System.out.println("Множество nullable-символов: "
                    + formatSet(nullable));

            Grammar result = new Grammar();
            result.start = start;

            Set<String> reservedSymbols = new LinkedHashSet<>(getAllSymbols());

            boolean startNullable = nullable.contains(start);

            if (startNullable) {
                String newStart =
                        createFreshNonTerminal("S", reservedSymbols);

                result.start = newStart;

                result.rules.put(
                        newStart,
                        new LinkedHashSet<>()
                );

                result.rules.get(newStart).add(
                        new ArrayList<>(List.of(start))
                );

                result.rules.get(newStart).add(
                        new ArrayList<>()
                );

                System.out.println(
                        "Добавлен новый стартовый символ: " + newStart
                );
            }

            for (Map.Entry<String, LinkedHashSet<List<String>>> entry
                    : rules.entrySet()) {

                String left = entry.getKey();

                result.rules.computeIfAbsent(
                        left,
                        key -> new LinkedHashSet<>()
                );

                for (List<String> right : entry.getValue()) {
                    if (right.isEmpty()) {
                        continue;
                    }

                    List<List<String>> variants =
                            generateNullableVariants(right, nullable);

                    for (List<String> variant : variants) {
                        if (!variant.isEmpty()) {
                            result.rules.get(left).add(variant);
                        }
                    }
                }
            }

            if (startNullable) {
                result.rules.get(result.start).add(new ArrayList<>());
            }

            System.out.println();
            System.out.println("Грамматика после удаления ε-продукций:");
            result.print();

            return result;
        }

        private List<List<String>> generateNullableVariants(
                List<String> right,
                Set<String> nullable
        ) {
            List<List<String>> variants = new ArrayList<>();
            variants.add(new ArrayList<>());

            for (String symbol : right) {
                List<List<String>> nextVariants = new ArrayList<>();

                for (List<String> variant : variants) {
                    List<String> included = new ArrayList<>(variant);
                    included.add(symbol);
                    nextVariants.add(included);

                    if (nullable.contains(symbol)) {
                        nextVariants.add(new ArrayList<>(variant));
                    }
                }

                variants = nextVariants;
            }

            return variants;
        }

        public Grammar removeUnit() {
            System.out.println();
            System.out.println("Этап 2. Удаление цепных правил");

            Set<String> unitRules = new LinkedHashSet<>();

            for (Map.Entry<String, LinkedHashSet<List<String>>> entry
                    : rules.entrySet()) {

                for (List<String> right : entry.getValue()) {
                    if (isUnit(right)) {
                        unitRules.add(
                                entry.getKey() + " → " + right.get(0)
                        );
                    }
                }
            }

            System.out.println("Множество цепных правил: "
                    + formatSet(unitRules));

            Grammar result = new Grammar();
            result.start = start;

            for (String nonTerminal : rules.keySet()) {
                Set<String> reachableByUnits = new LinkedHashSet<>();
                Queue<String> queue = new ArrayDeque<>();

                reachableByUnits.add(nonTerminal);
                queue.add(nonTerminal);

                while (!queue.isEmpty()) {
                    String current = queue.poll();

                    for (List<String> right : rules.get(current)) {
                        if (isUnit(right)) {
                            String next = right.get(0);

                            if (reachableByUnits.add(next)) {
                                queue.add(next);
                            }
                        }
                    }
                }

                LinkedHashSet<List<String>> newAlternatives =
                        new LinkedHashSet<>();

                for (String reachable : reachableByUnits) {
                    for (List<String> right : rules.get(reachable)) {
                        if (!isUnit(right)) {
                            newAlternatives.add(new ArrayList<>(right));
                        }
                    }
                }

                result.rules.put(nonTerminal, newAlternatives);
            }

            System.out.println();
            System.out.println("Грамматика после удаления цепных правил:");
            result.print();

            return result;
        }

        public Grammar removeUseless() {
            System.out.println();
            System.out.println("Этап 3. Удаление бесполезных символов");

            Set<String> productive = new LinkedHashSet<>();
            boolean changed;

            do {
                changed = false;

                for (Map.Entry<String, LinkedHashSet<List<String>>> entry
                        : rules.entrySet()) {

                    String left = entry.getKey();

                    if (productive.contains(left)) {
                        continue;
                    }

                    for (List<String> right : entry.getValue()) {
                        boolean allProductive = true;

                        for (String symbol : right) {
                            if (isNonTerminal(symbol)
                                    && !productive.contains(symbol)) {

                                allProductive = false;
                                break;
                            }
                        }

                        if (allProductive) {
                            productive.add(left);
                            changed = true;
                            break;
                        }
                    }
                }
            } while (changed);

            System.out.println();
            System.out.println("3.1. Удаление непорождающих символов");
            System.out.println("Множество порождающих нетерминалов: "
                    + formatSet(productive));

            if (!productive.contains(start)) {
                throw new IllegalArgumentException(
                        "Стартовый символ не является продуктивным."
                );
            }

            Grammar productiveGrammar = new Grammar();
            productiveGrammar.start = start;

            for (String nonTerminal : rules.keySet()) {
                if (!productive.contains(nonTerminal)) {
                    continue;
                }

                LinkedHashSet<List<String>> alternatives =
                        new LinkedHashSet<>();

                for (List<String> right : rules.get(nonTerminal)) {
                    boolean valid = true;

                    for (String symbol : right) {
                        if (isNonTerminal(symbol)
                                && !productive.contains(symbol)) {

                            valid = false;
                            break;
                        }
                    }

                    if (valid) {
                        alternatives.add(new ArrayList<>(right));
                    }
                }

                productiveGrammar.rules.put(nonTerminal, alternatives);
            }

            System.out.println();
            System.out.println(
                    "Грамматика после удаления непорождающих символов:"
            );
            productiveGrammar.print();

            System.out.println();
            System.out.println("3.2. Удаление недостижимых символов");

            Set<String> reachable = new LinkedHashSet<>();
            Queue<String> queue = new ArrayDeque<>();

            reachable.add(start);
            queue.add(start);

            while (!queue.isEmpty()) {
                String current = queue.poll();

                for (List<String> right : productiveGrammar.rules.getOrDefault(
                        current,
                        new LinkedHashSet<>())) {

                    for (String symbol : right) {
                        if (isNonTerminal(symbol)
                                && reachable.add(symbol)) {

                            queue.add(symbol);
                        }
                    }
                }
            }

            System.out.println("Множество достижимых нетерминалов: "
                    + formatSet(reachable));

            Grammar result = new Grammar();
            result.start = start;

            for (String nonTerminal : productiveGrammar.rules.keySet()) {
                if (!reachable.contains(nonTerminal)) {
                    continue;
                }

                result.rules.put(
                        nonTerminal,
                        new LinkedHashSet<>()
                );

                for (List<String> right
                        : productiveGrammar.rules.get(nonTerminal)) {

                    result.rules.get(nonTerminal).add(
                            new ArrayList<>(right)
                    );
                }
            }

            System.out.println();
            System.out.println(
                    "Грамматика после удаления недостижимых символов:"
            );
            result.print();

            return result;
        }

        public Grammar replaceTerminals() {
            System.out.println();
            System.out.println(
                    "Этап 4. Замена терминалов в длинных правых частях"
            );

            Grammar result = copy();
            Map<String, String> terminalMap = new LinkedHashMap<>();

            for (String left : new ArrayList<>(result.rules.keySet())) {
                LinkedHashSet<List<String>> updatedAlternatives =
                        new LinkedHashSet<>();

                for (List<String> right : result.rules.get(left)) {
                    if (right.size() <= 1) {
                        updatedAlternatives.add(new ArrayList<>(right));
                        continue;
                    }

                    List<String> updatedRight = new ArrayList<>();

                    for (String symbol : right) {
                        if (isNonTerminal(symbol)) {
                            updatedRight.add(symbol);
                        } else {
                            String replacement = terminalMap.get(symbol);

                            if (replacement == null) {
                                replacement =
                                        result.createFreshNonTerminal("T");

                                terminalMap.put(symbol, replacement);

                                result.rules.put(
                                        replacement,
                                        new LinkedHashSet<>(
                                                List.of(List.of(symbol))
                                        )
                                );
                            }

                            updatedRight.add(replacement);
                        }
                    }

                    updatedAlternatives.add(updatedRight);
                }

                result.rules.put(left, updatedAlternatives);
            }

            System.out.println();
            System.out.println(
                    "Грамматика после замены терминалов:"
            );
            result.print();

            return result;
        }

        public Grammar binarize() {
            System.out.println();
            System.out.println("Этап 5. Бинаризация правил");

            Grammar result = copy();

            LinkedHashMap<String, LinkedHashSet<List<String>>> updatedRules =
                    new LinkedHashMap<>();

            Set<String> reservedSymbols =
                    new LinkedHashSet<>(result.getAllSymbols());

            for (Map.Entry<String, LinkedHashSet<List<String>>> entry
                    : result.rules.entrySet()) {

                String left = entry.getKey();

                for (List<String> right : entry.getValue()) {
                    if (right.size() <= 2) {
                        updatedRules.computeIfAbsent(
                                left,
                                key -> new LinkedHashSet<>()
                        ).add(new ArrayList<>(right));

                        continue;
                    }

                    String currentLeft = left;

                    for (int i = 0; i < right.size() - 2; i++) {
                        String nextNonTerminal =
                                createFreshNonTerminal("X", reservedSymbols);

                        updatedRules.computeIfAbsent(
                                currentLeft,
                                key -> new LinkedHashSet<>()
                        ).add(new ArrayList<>(
                                List.of(right.get(i), nextNonTerminal)
                        ));

                        currentLeft = nextNonTerminal;
                    }

                    updatedRules.computeIfAbsent(
                            currentLeft,
                            key -> new LinkedHashSet<>()
                    ).add(new ArrayList<>(
                            right.subList(right.size() - 2, right.size())
                    ));
                }
            }

            result.rules.clear();
            result.rules.putAll(updatedRules);

            System.out.println();
            System.out.println("Грамматика после бинаризации:");
            result.print();

            return result;
        }

        public void print() {
            for (Map.Entry<String, LinkedHashSet<List<String>>> entry
                    : rules.entrySet()) {

                System.out.print(entry.getKey() + " → ");

                List<String> alternatives = new ArrayList<>();

                for (List<String> right : entry.getValue()) {
                    alternatives.add(formatRightCompact(right));
                }

                System.out.println(String.join(" | ", alternatives));
            }
        }
    }
}