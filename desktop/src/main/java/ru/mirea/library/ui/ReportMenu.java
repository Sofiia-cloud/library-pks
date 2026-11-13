package ru.mirea.library.ui;

import ru.mirea.library.exception.BusinessException;
import ru.mirea.library.exception.DatabaseException;
import ru.mirea.library.service.ReportService;

import java.util.List;
import java.util.Map;

public class ReportMenu {

    private final ReportService service;

    public ReportMenu(ReportService service) {
        this.service = service;
    }

    public void show() {
        while (true) {
            printMenu();
            int choice = InputHelper.readInt("Выберите пункт: ");
            try {
                switch (choice) {
                    case 1 -> printSummary();
                    case 2 -> printStatusDistribution();
                    case 3 -> printTopBooks();
                    case 4 -> printTopAuthors();
                    case 5 -> printTopReaders();
                    case 6 -> printReaderActivity();
                    case 7 -> printReadersWithoutRequests();
                    case 8 -> printRequestsByMonth();
                    case 0 -> { return; }
                    default -> System.out.println("Нет такого пункта.");
                }
            } catch (BusinessException | DatabaseException e) {
                System.out.println("Ошибка: " + e.getMessage());
            }
        }
    }

    private void printMenu() {
        System.out.println();
        System.out.println("========= ОТЧЁТЫ И СТАТИСТИКА =========");
        System.out.println("1. Сводка (общее / активные / просроченные / среднее)");
        System.out.println("2. Распределение заявок по статусам");
        System.out.println("3. Топ книг по числу заявок");
        System.out.println("4. Топ авторов по числу заявок");
        System.out.println("5. Топ читателей по активности");
        System.out.println("6. Активность всех читателей");
        System.out.println("7. Читатели без заявок");
        System.out.println("8. Заявки по месяцам за год");
        System.out.println("0. Назад");
        System.out.println("=======================================");
    }

    private void printSummary() {
        Map<String, Long> s = service.summary();
        System.out.printf("%-25s %10d%n", "Всего заявок:", s.get("Всего заявок"));
        System.out.printf("%-25s %10d%n", "Активных:", s.get("Активных"));
        System.out.printf("%-25s %10d%n", "Просроченных:", s.get("Просроченных"));
        System.out.printf("%-25s %10d%n", "Всего читателей:", s.get("Всего читателей"));
        System.out.printf("%-25s %10.2f%n", "Среднее на читателя:", service.averageRequestsPerReader());
    }

    private void printStatusDistribution() {
        Map<String, Long> map = service.countGroupedByStatus();
        long total = map.values().stream().mapToLong(Long::longValue).sum();
        if (total == 0) {
            System.out.println("Нет данных.");
            return;
        }
        System.out.printf("%-15s %10s %10s%n", "Статус", "Кол-во", "Доля");
        for (Map.Entry<String, Long> e : map.entrySet()) {
            double pct = 100.0 * e.getValue() / total;
            System.out.printf("%-15s %10d %9.1f%%%n", e.getKey(), e.getValue(), pct);
        }
        System.out.printf("%-15s %10d%n", "ИТОГО", total);
    }

    private void printTopBooks() {
        int n = InputHelper.readInt("Сколько книг показать (N): ");
        List<Map.Entry<String, Long>> list = service.topBooks(n);
        if (list.isEmpty()) { System.out.println("Нет данных."); return; }
        System.out.printf("%-40s %10s%n", "Книга", "Заявок");
        for (Map.Entry<String, Long> e : list) {
            System.out.printf("%-40s %10d%n", truncate(e.getKey(), 40), e.getValue());
        }
    }

    private void printTopAuthors() {
        int n = InputHelper.readInt("Сколько авторов показать (N): ");
        List<Map.Entry<String, Long>> list = service.topAuthors(n);
        if (list.isEmpty()) { System.out.println("Нет данных."); return; }
        System.out.printf("%-40s %10s%n", "Автор", "Заявок");
        for (Map.Entry<String, Long> e : list) {
            System.out.printf("%-40s %10d%n", truncate(e.getKey(), 40), e.getValue());
        }
    }

    private void printTopReaders() {
        int n = InputHelper.readInt("Сколько читателей показать (N): ");
        List<Map.Entry<String, Long>> list = service.topReaders(n);
        if (list.isEmpty()) { System.out.println("Нет данных."); return; }
        System.out.printf("%-40s %10s%n", "Читатель", "Заявок");
        for (Map.Entry<String, Long> e : list) {
            System.out.printf("%-40s %10d%n", truncate(e.getKey(), 40), e.getValue());
        }
    }

    private void printReaderActivity() {
        Map<String, Long> map = service.readerActivity();
        if (map.isEmpty()) { System.out.println("Нет данных."); return; }
        System.out.printf("%-40s %10s%n", "Читатель", "Заявок");
        map.forEach((name, cnt) -> System.out.printf("%-40s %10d%n", truncate(name, 40), cnt));
    }

    private void printReadersWithoutRequests() {
        List<String> list = service.readersWithoutRequests();
        if (list.isEmpty()) { System.out.println("Все читатели активны."); return; }
        System.out.println("Читатели без заявок:");
        list.forEach(n -> System.out.println("  - " + n));
    }

    private void printRequestsByMonth() {
        int year = InputHelper.readInt("Год: ");
        Map<Integer, Long> map = service.requestsByMonth(year);
        long total = map.values().stream().mapToLong(Long::longValue).sum();
        if (total == 0) { System.out.println("Нет данных за " + year + " год."); return; }
        System.out.printf("%-12s %10s%n", "Месяц", "Заявок");
        for (Map.Entry<Integer, Long> e : map.entrySet()) {
            System.out.printf("%-12s %10d%n", monthName(e.getKey()), e.getValue());
        }
        System.out.printf("%-12s %10d%n", "ИТОГО", total);
    }

    private String monthName(int m) {
        return switch (m) {
            case 1 -> "Январь"; case 2 -> "Февраль"; case 3 -> "Март";
            case 4 -> "Апрель"; case 5 -> "Май"; case 6 -> "Июнь";
            case 7 -> "Июль"; case 8 -> "Август"; case 9 -> "Сентябрь";
            case 10 -> "Октябрь"; case 11 -> "Ноябрь"; case 12 -> "Декабрь";
            default -> "?";
        };
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}