package ru.mirea.library.ui;

import ru.mirea.library.exception.BusinessException;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public final class InputHelper {

    private static final Charset INPUT_CHARSET = detectConsoleCharset();

    private static final BufferedReader READER =
            new BufferedReader(new InputStreamReader(System.in, INPUT_CHARSET));

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private InputHelper() { }

    private static Charset detectConsoleCharset() {
        String override = System.getProperty("console.encoding");
        if (override != null && !override.isBlank()) {
            try {
                return Charset.forName(override);
            } catch (Exception ignored) { }
        }

        Console console = System.console();
        if (console != null && console.charset() != null) {
            return console.charset();
        }

        String stdinEnc = System.getProperty("stdin.encoding");
        if (stdinEnc != null && !stdinEnc.isBlank()) {
            try {
                return Charset.forName(stdinEnc);
            } catch (Exception ignored) { }
        }

        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            return Charset.forName("CP866");
        }
        return StandardCharsets.UTF_8;
    }

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

    public static LocalDate readOptionalDate(String prompt) {
        String raw = readString(prompt);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim(), DATE_FMT);
        } catch (Exception e) {
            throw new BusinessException(
                    "Неверный формат даты: " + raw + " (ожидается дд.ММ.гггг)");
        }
    }

    public static LocalDate readDate(String prompt) {
        LocalDate d = readOptionalDate(prompt);
        if (d == null) {
            throw new BusinessException("Дата обязательна");
        }
        return d;
    }

    public static boolean readYesNo(String prompt) {
        while (true) {
            String raw = readString(prompt);
            if (raw == null) return false;
            String v = raw.trim().toLowerCase();
            if (v.equals("y") || v.equals("yes") || v.equals("д") || v.equals("да")) {
                return true;
            }
            if (v.equals("n") || v.equals("no") || v.equals("н") || v.equals("нет")) {
                return false;
            }
            System.out.println("Введите y или n.");
        }
    }
}