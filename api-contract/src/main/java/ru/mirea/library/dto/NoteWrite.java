package ru.mirea.library.dto;

import jakarta.validation.constraints.*;

import ru.mirea.library.model.*;

import java.time.*;
import java.util.*;

public record NoteWrite(
        @PositiveOrZero Long version, @NotBlank @Size(max = 4000) String text, boolean pinned) {}
