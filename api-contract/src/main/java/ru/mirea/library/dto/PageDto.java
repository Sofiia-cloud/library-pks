package ru.mirea.library.dto;

import jakarta.validation.constraints.*;

import ru.mirea.library.model.*;

import java.time.*;
import java.util.*;

public record PageDto<T>(List<T> items, int page, int size, long totalElements, int totalPages) {
    public PageDto {
        items = List.copyOf(items);
    }
}
