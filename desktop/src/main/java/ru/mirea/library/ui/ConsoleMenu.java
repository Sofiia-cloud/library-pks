package ru.mirea.library.ui;

import ru.mirea.library.exception.BusinessException;
import ru.mirea.library.exception.DatabaseException;
import ru.mirea.library.exception.EntityNotFoundException;
import ru.mirea.library.repository.BookRequestRepository;
import ru.mirea.library.repository.ReaderRepository;
import ru.mirea.library.repository.ReportRepository;
import ru.mirea.library.service.*;
import ru.mirea.library.util.ExcelExporter;

public class ConsoleMenu {

    private final ReaderMenu readerMenu;
    private final BookRequestMenu bookRequestMenu;
    private final SearchMenu searchMenu;
    private final FilterMenu filterMenu;
    private final SortMenu sortMenu;
    private final ReportMenu reportMenu;
    private final StatisticMenu statisticMenu;
    private final DatabaseViewMenu databaseViewMenu;

    public ConsoleMenu() {
        ReaderRepository readerRepo = new ReaderRepository();
        BookRequestRepository bookRequestRepo = new BookRequestRepository();
        ReportRepository reportRepo = new ReportRepository();

        ReaderService readerService = new ReaderService(readerRepo);
        BookRequestService bookRequestService = new BookRequestService(bookRequestRepo, readerService);
        ReportService reportService = new ReportService(reportRepo);

        this.readerMenu       = new ReaderMenu(readerService);
        this.bookRequestMenu  = new BookRequestMenu(bookRequestService);
        this.searchMenu       = new SearchMenu(new SearchService(bookRequestRepo));
        this.filterMenu       = new FilterMenu(new FilterService(bookRequestRepo));
        this.sortMenu         = new SortMenu(new SortService(bookRequestRepo));
        this.reportMenu       = new ReportMenu(reportService);
        this.statisticMenu    = new StatisticMenu(new StatisticService(reportService));
        this.databaseViewMenu = new DatabaseViewMenu();
    }

    public void show() {
        while (true) {
            printMainMenu();
            int choice = InputHelper.readInt("  Выберите действие: ");
            try {
                switch (choice) {
                    case 1 -> readerMenu.show();
                    case 2 -> bookRequestMenu.show();
                    case 3 -> searchMenu.show();
                    case 4 -> filterMenu.show();
                    case 5 -> sortMenu.show();
                    case 6 -> reportStatisticMenu();
                    case 7 -> exportData();
                    case 8 -> databaseViewMenu.show();
                    case 0 -> {
                        System.out.println("\n  До свидания!\n");
                        return;
                    }
                    default -> System.out.println("  Нет такого пункта.");
                }
            } catch (EntityNotFoundException | BusinessException e) {
                System.out.println("  Ошибка: " + e.getMessage());
            } catch (DatabaseException e) {
                System.out.println("  Ошибка БД: " + e.getMessage());
            } catch (Exception e) {
                System.out.println("  Непредвиденная ошибка: " + e.getMessage());
            }
        }
    }

    private void reportStatisticMenu() {
        while (true) {
            System.out.println();
            System.out.println("════════════════════════════════════════════════════════════");
            System.out.println("  ОТЧЁТЫ И СТАТИСТИКА");
            System.out.println("════════════════════════════════════════════════════════════");
            System.out.println("  1. Отчёты (12 агрегатов)");
            System.out.println("  2. Краткая статистика (6 показателей)");
            System.out.println("  0. Назад");
            System.out.println("════════════════════════════════════════════════════════════");
            int c = InputHelper.readInt("  Выбор: ");
            switch (c) {
                case 1 -> reportMenu.show();
                case 2 -> statisticMenu.show();
                case 0 -> { return; }
                default -> System.out.println("  Нет такого пункта.");
            }
        }
    }

    private void exportData() {
        System.out.println();
        System.out.println("════════════════════════════════════════════════════════════");
        System.out.println("  ЭКСПОРТ ДАННЫХ");
        System.out.println("════════════════════════════════════════════════════════════");
        boolean ok = InputHelper.readYesNo("  Экспортировать заявки в library_requests.xlsx? (y/n): ");
        if (!ok) {
            System.out.println("  Экспорт отменён.");
            return;
        }
        try {
            ExcelExporter.exportBookRequests("library_requests.xlsx");
            System.out.println("  ✓ Файл сохранён: library_requests.xlsx");
        } catch (Exception e) {
            System.out.println("  Ошибка экспорта: " + e.getMessage());
        }
    }

    private void printMainMenu() {
        System.out.println();
        System.out.println("╔═════════════════════════════════════════════════════════╗");
        System.out.println("║          БИБЛИОТЕЧНАЯ ИНФОРМАЦИОННАЯ СИСТЕМА            ║");
        System.out.println("╠═════════════════════════════════════════════════════════╣");
        System.out.println("║   1.    Читатели                                        ║");
        System.out.println("║   2.    Заявки на выдачу книги                          ║");
        System.out.println("║   3.    Поиск                                           ║");
        System.out.println("║   4.    Фильтрация                                      ║");
        System.out.println("║   5.    Сортировка                                      ║");
        System.out.println("║   6.    Отчёты и статистика                             ║");
        System.out.println("║   7.    Экспорт данных (Excel)                          ║");
        System.out.println("║   8.    Показать таблицы БД                             ║");
        System.out.println("╠═════════════════════════════════════════════════════════╣");
        System.out.println("║   0.    Выход                                           ║");
        System.out.println("╚═════════════════════════════════════════════════════════╝");
    }
}