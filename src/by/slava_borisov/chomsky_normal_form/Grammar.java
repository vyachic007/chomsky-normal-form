package by.slava_borisov.chomsky_normal_form;

import java.util.*;

public class Grammar {

    private final LinkedHashMap<String, LinkedHashSet<List<String>>> rules;
    private String start;
    private Set<String> lastNullable = new LinkedHashSet<>();
    private String lastNewStart;
    private Set<String> lastUnitRules = new LinkedHashSet<>();
    private Set<String> lastProductive = new LinkedHashSet<>();
    private Set<String> lastReachable = new LinkedHashSet<>();
    private Grammar grammarAfterProductive;
    private Grammar grammarAfterReachable;

    public Set<String> getLastNullable() {
        return new LinkedHashSet<>(lastNullable);
    }

    public String getLastNewStart() {
        return lastNewStart;
    }

    public Set<String> getLastUnitRules() {
        return new LinkedHashSet<>(lastUnitRules);
    }

    public Set<String> getLastProductive() {
        return new LinkedHashSet<>(lastProductive);
    }

    public Set<String> getLastReachable() {
        return new LinkedHashSet<>(lastReachable);
    }

    public Grammar getGrammarAfterProductive() {
        return grammarAfterProductive;
    }

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
            throw new IllegalArgumentException("Правило должно иметь формат A -> α.");
        }

        String left = parts[0].trim();

        if (!left.matches("[A-Z][0-9]*")) {
            throw new IllegalArgumentException("Левая часть должна быть нетерминалом.");
        }

        if (start == null) {
            start = left;
        }

        // Получаем набор вариантов правила или создаём новый, если его ещё нет
        LinkedHashSet<List<String>> alternatives = rules.computeIfAbsent(left, key -> new LinkedHashSet<>());
        String rightText = parts[1].trim();

        if (rightText.isEmpty()) {
            throw new IllegalArgumentException("Правая часть не может быть пустой. Используйте ε.");
        }

        String[] variants = rightText.split("\\|");

        for (String variant : variants) {
            String trimmed = variant.trim();

            if (trimmed.isEmpty() || trimmed.equals("ε")) {
                alternatives.add(new ArrayList<>());
            } else {
                // Разбиваем вариант на символы и добавляем в набор правил
                alternatives.add(GrammarParser.tokenize(trimmed));
            }
        }
    }

    /// Проверяет, что для каждого используемого нетерминала есть правило
    public void validate() {
        Set<String> nonTerminals = rules.keySet();

        for (Map.Entry<String, LinkedHashSet<List<String>>> entry : rules.entrySet()) {
            for (List<String> right : entry.getValue()) {
                for (String symbol : right) {
                    if (isNonTerminal(symbol) && !nonTerminals.contains(symbol)) {
                        throw new IllegalArgumentException("Для нетерминала " + symbol + " отсутствует правило."
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
        return right.size() == 1 && isNonTerminal(right.get(0));
    }

    /// Создаёт копию грамматики
    private Grammar copy() {
        LinkedHashMap<String, LinkedHashSet<List<String>>> copiedRules = new LinkedHashMap<>();

        for (Map.Entry<String, LinkedHashSet<List<String>>> entry : rules.entrySet()) {

            // Создаём новый набор вариантов правила
            LinkedHashSet<List<String>> copiedAlternatives = new LinkedHashSet<>();

            // Копируем каждый вариант правой части.
            for (List<String> right : entry.getValue()) {
                copiedAlternatives.add(new ArrayList<>(right));
            }

            // Добавляем скопированные варианты в новую карту
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

    /// Создаёт новый нетерминал, которого ещё нет в грамматике
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

    /// Создаёт новый нетерминал, которого ещё нет среди зарезервированных символов
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

    ///Удаляем пустые
    public Grammar removeEmpty() {
        // Находим нетерминалы, которые могут порождать ε
        Set<String> nullable = new LinkedHashSet<>();

        for (Map.Entry<String, LinkedHashSet<List<String>>> entry : rules.entrySet()) {

            for (List<String> right : entry.getValue()) {
                if (right.isEmpty()) {
                    nullable.add(entry.getKey());
                }
            }
        }

        boolean changed;

        // Повторяем проверку, пока находим новые nullable-символы
        do {
            changed = false;

            for (Map.Entry<String, LinkedHashSet<List<String>>> entry : rules.entrySet()) {

                String left = entry.getKey();

                // Пропускаем нетерминалы, которые уже могут порождать ε
                if (nullable.contains(left)) {
                    continue;
                }

                // Проверяем все варианты правой части правила
                for (List<String> right : entry.getValue()) {
                    // Если все символы правой части nullable,
                    // добавляем нетерминал слева в nullable.
                    if (!right.isEmpty() && right.stream().allMatch(nullable::contains)) {
                        nullable.add(left);
                        changed = true;
                        break;
                    }
                }
            }
        } while (changed);

        lastNullable = new LinkedHashSet<>(nullable);
        lastNewStart = null;

        // Создаём новую грамматику без ε-продукций
        Grammar result = new Grammar();
        result.start = start;

        // Сохраняем все используемые символы, чтобы не создать совпадающий нетерминал
        Set<String> reservedSymbols = new LinkedHashSet<>(getAllSymbols());

        // Проверяем, может ли начальный символ порождать ε
        boolean startNullable = nullable.contains(start);

        // Если стартовый символ может порождать ε, добавляем новый стартовый символ.
        if (startNullable) {
            String newStart = createFreshNonTerminal("S", reservedSymbols);
            lastNewStart = newStart;
            result.start = newStart;
            result.rules.put(newStart, new LinkedHashSet<>());
            result.rules.get(newStart).add(new ArrayList<>(List.of(start)));
            result.rules.get(newStart).add(new ArrayList<>());
        }

        // Перебираем все правила исходной грамматики
        for (Map.Entry<String, LinkedHashSet<List<String>>> entry : rules.entrySet()) {

            String left = entry.getKey();

            // Создаём набор вариантов в новой грамматике, если его ещё нет
            result.rules.computeIfAbsent(left, key -> new LinkedHashSet<>());

            for (List<String> right : entry.getValue()) {
                // Пропускаем пустые правила, так как удаляем ε-продукции
                if (right.isEmpty()) {
                    continue;
                }

                // Создаём варианты правой части с возможным удалением nullable-символов
                List<List<String>> variants = generateNullableVariants(right, nullable);

                for (List<String> variant : variants) {
                    if (!variant.isEmpty()) {
                        result.rules.get(left).add(variant);
                    }
                }
            }
        }

        //Сохраняем ε-продукцию для нового стартового символа, если он может порождать ε
        if (startNullable) {
            result.rules.get(result.start).add(new ArrayList<>());
        }

        return result;
    }

    /// Создаёт варианты правила, удаляя nullable-символы или оставляя их
    private List<List<String>> generateNullableVariants(
            List<String> right,
            Set<String> nullable
    ) {
        // Начинаем с пустого варианта
        List<List<String>> variants = new ArrayList<>();
        variants.add(new ArrayList<>());

        for (String symbol : right) {
            List<List<String>> nextVariants = new ArrayList<>();

            for (List<String> variant : variants) {
                // Добавляем вариант, в котором символ остаётся
                List<String> included = new ArrayList<>(variant);
                included.add(symbol);
                nextVariants.add(included);

                // Если символ nullable, добавляем вариант без него
                if (nullable.contains(symbol)) {
                    nextVariants.add(new ArrayList<>(variant));
                }
            }

            // Сохраняем новые варианты для следующего символа
            variants = nextVariants;
        }

        return variants;
    }

    ///Удаляет цепные правила
    public Grammar removeUnit() {
        // Находим все цепные правила грамматики
        Set<String> unitRules = new LinkedHashSet<>();

        for (Map.Entry<String, LinkedHashSet<List<String>>> entry : rules.entrySet()) {

            for (List<String> right : entry.getValue()) {
                if (isUnit(right)) {
                    unitRules.add(entry.getKey() + " → " + right.get(0));
                }
            }
        }

        lastUnitRules = new LinkedHashSet<>(unitRules);

        // Создаём новую грамматику без цепных правил
        Grammar result = new Grammar();
        result.start = start;

        // Для каждого нетерминала ищем связанные с ним цепными правилами символы
        for (String nonTerminal : rules.keySet()) {
            Set<String> reachableByUnits = new LinkedHashSet<>();
            Queue<String> queue = new ArrayDeque<>();

            reachableByUnits.add(nonTerminal);
            queue.add(nonTerminal);

            // Ищем нетерминалы, связанные цепными правилами
            while (!queue.isEmpty()) {
                // Берём следующий нетерминал из очереди
                String current = queue.poll();

                // Проверяем его правила
                for (List<String> right : rules.get(current)) {
                    if (isUnit(right)) {
                        // Получаем нетерминал из цепного правила
                        String next = right.get(0);

                        // Если его ещё не нашли, добавляем в очередь для проверки
                        if (reachableByUnits.add(next)) {
                            queue.add(next);
                        }
                    }
                }
            }

            // Собираем обычные правила найденных нетерминалов
            LinkedHashSet<List<String>> newAlternatives = new LinkedHashSet<>();

            for (String reachable : reachableByUnits) {
                for (List<String> right : rules.get(reachable)) {
                    if (!isUnit(right)) {
                        newAlternatives.add(new ArrayList<>(right));
                    }
                }
            }

            // Сохраняем новые варианты правила.
            result.rules.put(nonTerminal, newAlternatives);
        }

        return result;
    }

    /// Удаляет непорождающие и недостижимые символы
    public Grammar removeUseless() {
        Set<String> productive = new LinkedHashSet<>();
        boolean changed;

        // Повторяем поиск, пока находим новые порождающие нетерминалы
        do {
            changed = false;

            for (Map.Entry<String, LinkedHashSet<List<String>>> entry : rules.entrySet()) {

                String left = entry.getKey();

                // Пропускаем нетерминалы, которые уже найдены
                if (productive.contains(left)) {
                    continue;
                }

                // Проверяем варианты правой части правила
                for (List<String> right : entry.getValue()) {
                    boolean allProductive = true;

                    // Проверяем, могут ли все нетерминалы справа порождать строки
                    for (String symbol : right) {
                        if (isNonTerminal(symbol) && !productive.contains(symbol)) {

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

        lastProductive = new LinkedHashSet<>(productive);

        if (!productive.contains(start)) {
            throw new IllegalArgumentException(
                    "Стартовый символ не является продуктивным."
            );
        }

        // Создаём новую грамматику только с порождающими нетерминалами
        Grammar productiveGrammar = new Grammar();
        productiveGrammar.start = start;

        for (String nonTerminal : rules.keySet()) {
            if (!productive.contains(nonTerminal)) {
                continue;
            }

            // Создаём набор подходящих вариантов правила
            LinkedHashSet<List<String>> alternatives =
                    new LinkedHashSet<>();

            for (List<String> right : rules.get(nonTerminal)) {
                boolean valid = true;

                // Проверяем, нет ли справа непорождающих нетерминалов
                for (String symbol : right) {
                    if (isNonTerminal(symbol)
                            && !productive.contains(symbol)) {

                        valid = false;
                        break;
                    }
                }

                // Добавляем правило, если все его символы подходят
                if (valid) {
                    alternatives.add(new ArrayList<>(right));
                }
            }

            // Сохраняем отобранные правила в новой грамматике
            productiveGrammar.rules.put(nonTerminal, alternatives);
        }

        grammarAfterProductive = productiveGrammar.copy();

        // Начинаем поиск достижимых нетерминалов со стартового символа
        Set<String> reachable = new LinkedHashSet<>();
        Queue<String> queue = new ArrayDeque<>();

        reachable.add(start);
        queue.add(start);

        // Ищем все нетерминалы, которые встречаются в правилах достижимых символов
        while (!queue.isEmpty()) {
            // Берём следующий нетерминал из очереди
            String current = queue.poll();

            // Получаем его правила из грамматики
            for (List<String> right : productiveGrammar.rules.getOrDefault(
                    current,
                    new LinkedHashSet<>())) {

                // Проверяем символы правой части
                for (String symbol : right) {

                    // Добавляем новый нетерминал в множество и очередь
                    if (isNonTerminal(symbol)
                            && reachable.add(symbol)) {

                        queue.add(symbol);
                    }
                }
            }
        }

        lastReachable = new LinkedHashSet<>(reachable);

        // Создаём итоговую грамматику без недостижимых нетерминалов
        Grammar result = new Grammar();
        result.start = start;

        // Перебираем правила грамматики после удаления непорождающих символов
        for (String nonTerminal : productiveGrammar.rules.keySet()) {

            // Пропускаем нетерминалы, до которых нельзя добраться от стартового
            if (!reachable.contains(nonTerminal)) {
                continue;
            }

            // Создаём набор правил для оставшегося нетерминала
            result.rules.put(nonTerminal, new LinkedHashSet<>());

            // Копируем его варианты правой части в итоговую грамматику
            for (List<String> right : productiveGrammar.rules.get(nonTerminal)) {
                result.rules.get(nonTerminal).add(new ArrayList<>(right));
            }
        }

        grammarAfterReachable = result.copy();

        return result;
    }

    /// Заменяет терминалы в длинных правилах на новые нетерминалы
    public Grammar replaceTerminals() {
        Grammar result = copy();

        // Храним соответствие терминалов и новых нетерминалов
        Map<String, String> terminalMap = new LinkedHashMap<>();

        for (String left : new ArrayList<>(result.rules.keySet())) {

            LinkedHashSet<List<String>> updatedAlternatives = new LinkedHashSet<>();

            for (List<String> right : result.rules.get(left)) {

                // Если справа один символ или меньше, оставляем правило без изменений
                if (right.size() <= 1) {
                    updatedAlternatives.add(new ArrayList<>(right));
                    continue;
                }

                // Создаём новую правую часть
                List<String> updatedRight = new ArrayList<>();

                for (String symbol : right) {

                    // Нетерминалы оставляем без изменений
                    if (isNonTerminal(symbol)) {
                        updatedRight.add(symbol);
                    } else {
                        // Ищем замену для терминала
                        String replacement = terminalMap.get(symbol);

                        // Если замены ещё нет, создаём новый нетерминал
                        if (replacement == null) {
                            replacement = result.createFreshNonTerminal("T");

                            // Сохраняем замену
                            terminalMap.put(symbol, replacement);

                            // Добавляем правило нового нетерминала для терминала
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

        return result;
    }

    /// Разбивает длинные правила на правила с двумя символами справа
    public Grammar binarize() {
        Grammar result = copy();

        LinkedHashMap<String, LinkedHashSet<List<String>>> updatedRules = new LinkedHashMap<>();

        // Запоминаем все существующие символы, чтобы не создавать совпадающие имена
        Set<String> reservedSymbols = new LinkedHashSet<>(result.getAllSymbols());

        for (Map.Entry<String, LinkedHashSet<List<String>>> entry : result.rules.entrySet()) {

            String left = entry.getKey();

            for (List<String> right : entry.getValue()) {

                // Если в правой части не больше двух символов, правило уже подходит
                if (right.size() <= 2) {
                    updatedRules.computeIfAbsent(
                            left,
                            key -> new LinkedHashSet<>()
                    ).add(new ArrayList<>(right));

                    continue;
                }

                // Начинаем цепочку новых правил с исходного нетерминала
                String currentLeft = left;

                // Разбиваем длинное правило на несколько правил с двумя символами справа
                for (int i = 0; i < right.size() - 2; i++) {

                    // Создаем новый нетерминал для следующего звена цепочки
                    String nextNonTerminal = createFreshNonTerminal("X", reservedSymbols);

                    // Добавляем правило из двух символов
                    updatedRules.computeIfAbsent(
                            currentLeft,
                            key -> new LinkedHashSet<>()
                    ).add(new ArrayList<>(
                            List.of(right.get(i), nextNonTerminal)
                    ));

                    // Продолжаем цепочку с созданного нетерминала
                    currentLeft = nextNonTerminal;
                }

                // Последнее правило содержит два оставшихся символа
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

        return result;
    }

    public void print() {
        for (Map.Entry<String, LinkedHashSet<List<String>>> entry : rules.entrySet()) {

            System.out.print(entry.getKey() + " → ");

            List<String> alternatives = new ArrayList<>();

            for (List<String> right : entry.getValue()) {
                alternatives.add(GrammarFormatter.formatRightCompact(right));
            }

            System.out.println(String.join(" | ", alternatives));
        }
    }
}