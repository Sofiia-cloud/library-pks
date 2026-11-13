package ru.mirea.library.service;

import org.springframework.data.domain.Page;

import ru.mirea.library.dto.PageDto;
import ru.mirea.library.error.BusinessException;

public final class Paging {
    private Paging() {}

    public static void check(int page, int size) {
        if (page < 0 || size < 1 || size > 100)
            throw new BusinessException("Номер страницы от 0, размер от 1 до 100");
    }

    public static <T> PageDto<T> dto(Page<T> page) {
        return new PageDto<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
