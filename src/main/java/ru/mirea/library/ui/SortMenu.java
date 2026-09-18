package ru.mirea.library.ui;

import ru.mirea.library.exception.BusinessException;
import ru.mirea.library.exception.DatabaseException;
import ru.mirea.library.model.BookRequest;
import ru.mirea.library.service.SortService;

import java.util.List;

public class SortMenu {

    private final SortService service;

    public SortMenu(SortService service) {
        this.service = service;
    }

    public void show() {
        while (true) {
            System.out.println("\n===== СОРТИРОВКА ЗАЯВОК =====");
            System.out.println("1. По дате создания (возр.)");
            System.out.println("2. По дате создания (убыв.)");
            System.out.println("3. По названию книги (А-Я)");
            System.out.println("4. По названию книги (Я-А)");
            System.out.println("5. По статусу (возр.)");
            System.out.println("6. По статусу (убыв.)");
            System.out.println("0. Назад");
            int c = InputHelper.readInt("Выбор: ");
            try {
                switch (c) {
                    case 1 -> print(service.byCreatedAt(true));
                    case 2 -> print(service.byCreatedAt(false));
                    case 3 -> print(service.byBookTitle(true));
                    case 4 -> print(service.byBookTitle(false));
                    case 5 -> print(service.byStatus(true));
                    case 6 -> print(service.byStatus(false));
                    case 0 -> { return; }
                    default -> System.out.println("Нет такого пункта.");
                }
            } catch (BusinessException | DatabaseException e) {
                System.out.println("Ошибка: " + e.getMessage());
            }
        }
    }

    private void print(List<BookRequest> list) {
        if (list.isEmpty()) { System.out.println("Нет данных."); return; }
        list.forEach(System.out::println);
        System.out.println("Всего: " + list.size());
    }
}