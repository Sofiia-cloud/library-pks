package ru.mirea.library;

import ru.mirea.library.repository.ReaderRepository;
import ru.mirea.library.service.ReaderService;
import ru.mirea.library.ui.ReaderMenu;

import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        ReaderRepository repo = new ReaderRepository();
        ReaderService service = new ReaderService(repo);
        ReaderMenu menu = new ReaderMenu(service, new Scanner(System.in));
        menu.show();
    }
}