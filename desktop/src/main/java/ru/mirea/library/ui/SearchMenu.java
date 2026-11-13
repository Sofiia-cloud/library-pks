package ru.mirea.library.ui;

import ru.mirea.library.exception.BusinessException;
import ru.mirea.library.exception.DatabaseException;
import ru.mirea.library.model.BookRequest;
import ru.mirea.library.service.SearchService;

import java.util.List;

public class SearchMenu {

    private final SearchService service;

    public SearchMenu(SearchService service) {
        this.service = service;
    }

    public void show() {
        while (true) {
            System.out.println("\n===== ПОИСК ЗАЯВОК =====");
            System.out.println("1. По названию книги");
            System.out.println("2. По автору");
            System.out.println("3. По ФИО читателя");
            System.out.println("4. По номеру билета");
            System.out.println("0. Назад");
            int c = InputHelper.readInt("Выбор: ");
            try {
                switch (c) {
                    case 1 -> print(service.byTitle(InputHelper.readString("Название: ")));
                    case 2 -> print(service.byAuthor(InputHelper.readString("Автор: ")));
                    case 3 -> print(service.byReaderName(InputHelper.readString("ФИО: ")));
                    case 4 -> print(service.byReaderCard(InputHelper.readString("Билет: ")));
                    case 0 -> { return; }
                    default -> System.out.println("Нет такого пункта.");
                }
            } catch (BusinessException | DatabaseException e) {
                System.out.println("Ошибка: " + e.getMessage());
            }
        }
    }

    private void print(List<BookRequest> list) {
        if (list.isEmpty()) { System.out.println("Ничего не найдено."); return; }
        list.forEach(System.out::println);
        System.out.println("Всего: " + list.size());
    }
}