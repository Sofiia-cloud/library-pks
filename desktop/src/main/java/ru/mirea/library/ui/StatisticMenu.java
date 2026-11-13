package ru.mirea.library.ui;

import ru.mirea.library.exception.BusinessException;
import ru.mirea.library.exception.DatabaseException;
import ru.mirea.library.service.StatisticService;

public class StatisticMenu {

    private final StatisticService service;

    public StatisticMenu(StatisticService service) {
        this.service = service;
    }

    public void show() {
        while (true) {
            System.out.println("\n===== СТАТИСТИКА =====");
            System.out.println("1. Всего читателей");
            System.out.println("2. Всего заявок");
            System.out.println("3. Активных заявок");
            System.out.println("4. Возвращённых");
            System.out.println("5. Отменённых/отклонённых");
            System.out.println("6. Заявок за последние 30 дней");
            System.out.println("0. Назад");
            int c = InputHelper.readInt("Выбор: ");
            try {
                switch (c) {
                    case 1 -> System.out.println("Всего читателей: " + service.totalReaders());
                    case 2 -> System.out.println("Всего заявок: " + service.totalRequests());
                    case 3 -> System.out.println("Активных: " + service.activeRequests());
                    case 4 -> System.out.println("Возвращённых: " + service.returnedRequests());
                    case 5 -> System.out.println("Отменённых/отклонённых: " + service.cancelledOrRejected());
                    case 6 -> System.out.println("За 30 дней: " + service.requestsLast30Days());
                    case 0 -> { return; }
                    default -> System.out.println("Нет такого пункта.");
                }
            } catch (BusinessException | DatabaseException e) {
                System.out.println("Ошибка: " + e.getMessage());
            }
        }
    }
}