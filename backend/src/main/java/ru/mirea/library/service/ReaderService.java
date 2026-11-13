package ru.mirea.library.service;

import org.springframework.data.domain.Sort;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mirea.library.domain.Reader;
import ru.mirea.library.error.BusinessException;
import ru.mirea.library.error.EntityNotFoundException;
import ru.mirea.library.model.RequestStatus;
import ru.mirea.library.repository.BookRequestRepository;
import ru.mirea.library.repository.ReaderRepository;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@Transactional(readOnly = true)
public class ReaderService {
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9_.-]+@[A-Za-z0-9_.-]+[.][A-Za-z]{2,}$");
    private static final Pattern CARD = Pattern.compile("^LIB-[0-9]{4}$");
    private static final Pattern PHONE = Pattern.compile("^[+]?[0-9]{10,15}$");
    private static final List<RequestStatus> ACTIVE_STATUSES = List.of(
            RequestStatus.CREATED, RequestStatus.APPROVED, RequestStatus.ISSUED);

    private final ReaderRepository repository;
    private final BookRequestRepository requests;

    public ReaderService(ReaderRepository repository, BookRequestRepository requests) {
        this.repository = repository;
        this.requests = requests;
    }

    @Transactional
    public Reader create(String fullName, String email, String phone, String cardNumber) {
        String name = required(fullName, "ФИО", 150);
        String normalizedEmail = normalizeEmail(email);
        String card = normalizeCard(cardNumber);
        String normalizedPhone = normalizePhone(phone);
        if (repository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new BusinessException("Читатель с таким email уже существует");
        }
        if (repository.existsByLibraryCardNumber(card)) {
            throw new BusinessException("Номер читательского билета уже занят");
        }
        return repository.saveAndFlush(new Reader(name, normalizedEmail, normalizedPhone, card));
    }

    public Reader getById(UUID id) {
        requireId(id);
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("Читатель не найден: " + id));
    }

    public List<Reader> getAll() {
        return repository.findAll(Sort.by("readerNumber"));
    }

    public boolean existsById(UUID id) {
        requireId(id);
        return repository.existsById(id);
    }

    @Transactional
    public Reader update(UUID id, long version, String fullName, String email, String phone, String cardNumber) {
        Reader reader = getById(id);
        checkVersion(reader, version);
        String name = required(fullName, "ФИО", 150);
        String normalizedEmail = normalizeEmail(email);
        String card = normalizeCard(cardNumber);
        String normalizedPhone = normalizePhone(phone);
        if (repository.existsByEmailIgnoreCaseAndIdNot(normalizedEmail, id)) {
            throw new BusinessException("Email уже используется другим читателем");
        }
        if (repository.existsByLibraryCardNumberAndIdNot(card, id)) {
            throw new BusinessException("Номер читательского билета уже занят");
        }
        reader.setFullName(name);
        reader.setEmail(normalizedEmail);
        reader.setPhone(normalizedPhone);
        reader.setLibraryCardNumber(card);
        return repository.saveAndFlush(reader);
    }

    @Transactional
    public void delete(UUID id, long version) {
        Reader reader = getById(id);
        checkVersion(reader, version);
        if (requests.existsByReader_IdAndStatusIn(id, ACTIVE_STATUSES)) {
            throw new BusinessException("Нельзя удалить читателя: у него есть активные заявки");
        }
        if (requests.existsByReader_Id(id)) {
            throw new BusinessException("Нельзя удалить читателя: на него ссылаются сохранённые заявки");
        }
        repository.delete(reader);
        repository.flush();
    }

    private static void checkVersion(Reader reader, long expected) {
        if (expected < 0) throw new BusinessException("Версия не может быть отрицательной");
        if (reader.getVersion() != expected) throw new ObjectOptimisticLockingFailureException(Reader.class, reader.getId());
    }

    private static String normalizeEmail(String email) {
        String value = required(email, "Email", 150).toLowerCase(Locale.ROOT);
        if (!EMAIL.matcher(value).matches()) throw new BusinessException("Некорректный email");
        return value;
    }

    private static String normalizeCard(String card) {
        String value = required(card, "Номер билета", 30);
        if (!CARD.matcher(value).matches()) throw new BusinessException("Формат билета: LIB-XXXX (4 цифры)");
        return value;
    }

    private static String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) return null;
        String value = phone.trim();
        if (!PHONE.matcher(value).matches()) throw new BusinessException("Некорректный телефон");
        return value;
    }

    private static String required(String text, String field, int maxLength) {
        if (text == null || text.isBlank()) throw new BusinessException(field + ": обязательное поле");
        String value = text.trim();
        if (value.length() > maxLength) throw new BusinessException(field + ": не более " + maxLength + " символов");
        return value;
    }

    private static void requireId(UUID id) {
        if (id == null) throw new BusinessException("UUID читателя обязателен");
    }
}
