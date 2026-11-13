package ru.mirea.library.error;

public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}