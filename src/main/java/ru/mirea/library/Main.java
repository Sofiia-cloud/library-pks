package ru.mirea.library;

import ru.mirea.library.repository.ReaderRepository;
import ru.mirea.library.service.ReaderService;
import ru.mirea.library.ui.ReaderMenu;

public class Main {
    public static void main(String[] args) {
        ReaderRepository repo = new ReaderRepository();
        ReaderService service = new ReaderService(repo);
        ReaderMenu menu = new ReaderMenu(service);
        menu.show();
    }
}