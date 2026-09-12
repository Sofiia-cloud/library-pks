package ru.mirea.library.service;

import ru.mirea.library.exception.BusinessException;
import ru.mirea.library.exception.EntityNotFoundException;
import ru.mirea.library.model.Reader;
import ru.mirea.library.repository.ReaderRepository;

import java.util.List;
import java.util.regex.Pattern;

public class ReaderService {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[\\w.-]+@[\\w.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern CARD_PATTERN =
            Pattern.compile("^LIB-\\d{4}$");
    private static final Pattern PHONE_PATTERN =
            Pattern.compile("^\\+?\\d{10,15}$");

    private final ReaderRepository repository;

    public ReaderService(ReaderRepository repository) {
        this.repository = repository;
    }

    /** Бзнес-правило 1: обязательные поля. */
    public Reader create(String fullName, String email, String phone, String cardNumber) {
        validateRequired(fullName, email, cardNumber);
        validateEmail(email);
        validateCard(cardNumber);
        if (phone != null && !phone.isBlank()) validatePhone(phone);

        if (repository.existsByEmail(email)) {
            throw new BusinessException("Читатель с email '" + email + "' уже существует");
        }
        if (repository.existsByCardNumber(cardNumber)) {
            throw new BusinessException("Номер читательского билета '" + cardNumber + "' уже занят");
        }

        Reader reader = new Reader(fullName.trim(), email.trim().toLowerCase(),
                                   phone, cardNumber.trim());
        return repository.save(reader);
    }

    public Reader getById(int id) {
        return repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Читатель с ID=" + id + " не найден"));
    }

    public List<Reader> getAll() {
        return repository.findAll();
    }

    public Reader update(int id, String fullName, String email, String phone, String cardNumber) {
        Reader existing = getById(id);
        validateRequired(fullName, email, cardNumber);
        validateEmail(email);
        validateCard(cardNumber);
        if (phone != null && !phone.isBlank()) validatePhone(phone);

        if (!existing.getEmail().equalsIgnoreCase(email) && repository.existsByEmail(email)) {
            throw new BusinessException("Email '" + email + "' уже используется");
        }
        if (!existing.getLibraryCardNumber().equals(cardNumber)
                && repository.existsByCardNumber(cardNumber)) {
            throw new BusinessException("Билет '" + cardNumber + "' уже занят");
        }

        existing.setFullName(fullName.trim());
        existing.setEmail(email.trim().toLowerCase());
        existing.setPhone(phone);
        existing.setLibraryCardNumber(cardNumber.trim());

        if (!repository.update(existing)) {
            throw new BusinessException("Не удалось обновить читателя ID=" + id);
        }
        return existing;
    }

    /** Бизнес-правило: нельзя удалить читателя с активными заявками. */
    public void delete(int id) {
        getById(id);
        if (repository.hasActiveRequests(id)) {
            throw new BusinessException(
                "Нельзя удалить читателя: у него есть активные заявки");
        }
        if (!repository.deleteById(id)) {
            throw new BusinessException("Не удалось удалить читателя ID=" + id);
        }
    }

    public boolean existsById(int id) {
        return repository.findById(id).isPresent();
    }

    /**  приватная валидация */

    private void validateRequired(String fullName, String email, String card) {
        if (fullName == null || fullName.isBlank())
            throw new BusinessException("ФИО обязательно");
        if (email == null || email.isBlank())
            throw new BusinessException("Email обязателен");
        if (card == null || card.isBlank())
            throw new BusinessException("Номер читательского билета обязателен");
    }

    private void validateEmail(String email) {
        if (!EMAIL_PATTERN.matcher(email).matches())
            throw new BusinessException("Некорректный email: " + email);
    }

    private void validateCard(String card) {
        if (!CARD_PATTERN.matcher(card).matches())
            throw new BusinessException("Формат билета: LIB-XXXX (4 цифры)");
    }

    private void validatePhone(String phone) {
        if (!PHONE_PATTERN.matcher(phone).matches())
            throw new BusinessException("Некорректный телефон: " + phone);
    }
}