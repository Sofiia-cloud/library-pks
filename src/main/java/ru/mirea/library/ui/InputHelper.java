package ru.mirea.library.ui;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class InputHelper {

    private static final BufferedReader READER =
            new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));

    private InputHelper() { }

    public static int readInt(String prompt) {
        while (true) {
            System.out.print(prompt);
            try {
                String line = READER.readLine();
                if (line == null) throw new IOException("EOF");
                return Integer.parseInt(line.trim());
            } catch (NumberFormatException e) {
                System.out.println("Ошибка: нужно целое число. Повторите ввод.");
            } catch (IOException e) {
                throw new RuntimeException("Ошибка чтения ввода", e);
            }
        }
    }

    public static String readString(String prompt) {
        System.out.print(prompt);
        try {
            String line = READER.readLine();
            return line == null ? "" : line.trim();
        } catch (IOException e) {
            throw new RuntimeException("Ошибка чтения ввода", e);
        }
    }
}