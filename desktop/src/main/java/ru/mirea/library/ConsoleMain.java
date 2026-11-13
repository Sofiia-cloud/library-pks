package ru.mirea.library;

import ru.mirea.library.ui.ConsoleMenu;

public class ConsoleMain {
    public static void main(String[] args) {
        try {
            new ConsoleMenu().show();
        } catch (RuntimeException e) {
            System.out.println(e.getMessage());
        }
    }
}
