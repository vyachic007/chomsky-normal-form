package by.slava_borisov.chomsky_normal_form;

import java.util.Scanner;

public class Main {

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

            System.out.println();
            System.out.println("Этап 1. Удаление ε-продукций");
            Grammar result = grammar.removeEmpty();
            System.out.println("Грамматика после удаления ε-продукций:");
            result.print();

            System.out.println();
            System.out.println("Этап 2. Удаление цепных правил");
            result = result.removeUnit();
            System.out.println("Грамматика после удаления цепных правил:");
            result.print();

            System.out.println();
            System.out.println("Этап 3. Удаление бесполезных символов");
            result = result.removeUseless();
            System.out.println("Грамматика после удаления бесполезных символов:");
            result.print();

            System.out.println();
            System.out.println("Этап 4. Замена терминалов в длинных правых частях");
            result = result.replaceTerminals();
            System.out.println("Грамматика после замены терминалов:");
            result.print();

            System.out.println();
            System.out.println("Этап 5. Бинаризация правил");
            result = result.binarize();
            System.out.println("Грамматика после бинаризации:");
            result.print();

            System.out.println();
            System.out.println("Итоговая грамматика в НФХ:");
            result.print();

        } catch (IllegalArgumentException exception) {
            System.out.println("Ошибка: " + exception.getMessage());
        }
    }
}