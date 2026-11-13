package ru.mirea.library.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.validation.Validator;

import org.apache.commons.csv.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.mirea.library.domain.*;
import ru.mirea.library.domain.Reader;
import ru.mirea.library.dto.*;
import ru.mirea.library.error.*;
import ru.mirea.library.filter.RequestFilter;
import ru.mirea.library.model.*;
import ru.mirea.library.repository.*;

import java.io.*;
import java.time.*;
import java.util.*;

@Service
public class CsvService {
    private static final String[] HEADER = {
        "externalCode",
        "readerCard",
        "bookTitle",
        "bookAuthor",
        "isbn",
        "status",
        "desiredReturnDate",
        "comment",
        "requestedCopies",
        "urgent",
        "tags"
    };
    private final BookRequestRepository requests;
    private final ReaderRepository readers;
    private final ImportBatchRepository batches;
    private final BookRequestService service;
    private final RequestQueryService query;
    private final AuditService audit;
    private final Validator validator;
    private final ObjectMapper json;

    public CsvService(
            BookRequestRepository requests,
            ReaderRepository readers,
            ImportBatchRepository batches,
            BookRequestService service,
            RequestQueryService query,
            AuditService audit,
            Validator validator,
            ObjectMapper json) {
        this.requests = requests;
        this.readers = readers;
        this.batches = batches;
        this.service = service;
        this.query = query;
        this.audit = audit;
        this.validator = validator;
        this.json = json;
    }

    private record Row(int line, String code, BookRequestCreate data, BookRequest existing) {}

    private record Parsed(
            List<Row> rows, List<CsvPreview.RowError> errors, Map<String, Long> versions) {}

    @Transactional
    public CsvPreview preview(CsvPreviewRequest b) {
        if (b.content().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 5000000)
            throw new BusinessException("CSV: не более 5 МБ");
        batches.deleteExpired(Instant.now());
        Parsed p = parse(b.content(), b.mode());
        ImportBatch batch = new ImportBatch();
        batch.ownerId = AuditService.current().id();
        batch.content = b.content();
        batch.mode = b.mode();
        batch.versions = encode(p.versions());
        batch.expiresAt = Instant.now().plusSeconds(900);
        batch.validRows = p.rows().size();
        batch.invalidRows = p.errors().size();
        batches.saveAndFlush(batch);
        return new CsvPreview(
                batch.id, batch.validRows, batch.invalidRows, p.errors(), batch.expiresAt);
    }

    @Transactional
    public CsvResult apply(UUID id) {
        ImportBatch b =
                batches.lockById(id)
                        .orElseThrow(() -> new EntityNotFoundException("Проверка CSV не найдена"));
        if (!b.ownerId.equals(AuditService.current().id()))
            throw new org.springframework.security.access.AccessDeniedException(
                    "Чужая проверка CSV");
        if (!b.expiresAt.isAfter(Instant.now()))
            throw new BusinessException("Проверка истекла, загрузите CSV заново");
        if (b.applied) return new CsvResult(b.createdCount, b.updatedCount);
        if (b.invalidRows > 0)
            throw new BusinessException("Исправьте ошибки CSV и проверьте файл заново");
        Parsed p = parse(b.content, b.mode);
        if (!p.errors().isEmpty() || !p.versions().equals(decode(b.versions)))
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT,
                    "После проверки данные изменились. Проверьте CSV заново");
        for (Row row : p.rows()) {
            var d = row.data();
            if (row.existing() == null) {
                service.create(d);
                b.createdCount++;
            } else {
                service.update(
                        row.existing().getId(),
                        new BookRequestUpdate(
                                row.existing().getVersion(),
                                d.bookTitle(),
                                d.bookAuthor(),
                                d.isbn(),
                                d.desiredReturnDate(),
                                d.comment(),
                                d.status(),
                                d.requestedCopies(),
                                d.urgent(),
                                d.tags()));
                b.updatedCount++;
            }
        }
        b.applied = true;
        batches.saveAndFlush(b);
        audit.record(
                "CSV_IMPORT",
                "IMPORT",
                b.id,
                "Создано: " + b.createdCount + ", обновлено: " + b.updatedCount);
        return new CsvResult(b.createdCount, b.updatedCount);
    }

    @Transactional(readOnly = true)
    public String export(RequestFilter filter) {
        StringWriter out = new StringWriter();
        try (CSVPrinter printer =
                new CSVPrinter(out, CSVFormat.RFC4180.builder().setHeader(HEADER).get())) {
            Map<UUID, String> cards = new HashMap<>();
            readers.findAll().forEach(r -> cards.put(r.getId(), r.getLibraryCardNumber()));
            for (var b : query.all(filter))
                printer.printRecord(
                        safe(b.externalCode()),
                        safe(cards.get(b.readerId())),
                        safe(b.bookTitle()),
                        safe(b.bookAuthor()),
                        safe(b.isbn()),
                        b.status(),
                        b.desiredReturnDate(),
                        safe(b.comment()),
                        b.requestedCopies(),
                        b.urgent(),
                        safe(String.join("|", new TreeSet<>(b.tags()))));
            return "\uFEFF" + out;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private Parsed parse(String content, ImportMode mode) {
        var rows = new ArrayList<Row>();
        var errors = new ArrayList<CsvPreview.RowError>();
        var versions = new LinkedHashMap<String, Long>();
        var seen = new HashSet<String>();
        Map<String, Reader> refs = new HashMap<>();
        readers.findAll().forEach(r -> refs.put(r.getLibraryCardNumber(), r));
        try (var parser =
                CSVFormat.RFC4180
                        .builder()
                        .setHeader()
                        .setSkipHeaderRecord(true)
                        .setDuplicateHeaderMode(DuplicateHeaderMode.DISALLOW)
                        .get()
                        .parse(
                                new StringReader(
                                        content.startsWith("\uFEFF")
                                                ? content.substring(1)
                                                : content))) {
            if (!parser.getHeaderNames().equals(List.of(HEADER)))
                throw new BusinessException("Заголовок CSV: " + String.join(",", HEADER));
            int count = 0;
            for (CSVRecord r : parser) {
                if (++count > 5000) throw new BusinessException("CSV: не более 5000 строк");
                int line = (int) r.getRecordNumber() + 1;
                try {
                    if (r.size() != HEADER.length)
                        throw new BusinessException("Неверное количество столбцов");
                    String code = uns(r.get("externalCode")).trim();
                    if (!code.isEmpty() && !code.matches("[A-Za-z0-9._-]{1,64}"))
                        throw new BusinessException("Некорректный внешний код");
                    if (!code.isEmpty() && !seen.add(code))
                        throw new BusinessException("Повтор внешнего кода в файле");
                    Reader reader = refs.get(uns(r.get("readerCard")).trim());
                    if (reader == null)
                        throw new BusinessException("Читатель не найден по номеру билета");
                    BookRequest existing =
                            code.isEmpty() ? null : requests.findByExternalCode(code).orElse(null);
                    if (existing != null && mode == ImportMode.NEW_ONLY)
                        throw new BusinessException("Внешний код уже существует");
                    if (existing != null
                            && (existing.isArchived()
                                    || !existing.getReader().getId().equals(reader.getId())))
                        throw new BusinessException("Архивная заявка или другой читатель");
                    if (existing == null && !reader.isActive())
                        throw new BusinessException("Читатель отключён");
                    String date = r.get("desiredReturnDate").trim();
                    LocalDate due = date.isEmpty() ? null : LocalDate.parse(date);
                    if (due != null
                            && due.isBefore(LocalDate.now())
                            && (existing == null || !due.equals(existing.getDesiredReturnDate())))
                        throw new BusinessException("Дата возврата в прошлом");
                    RequestStatus status = RequestStatus.valueOf(r.get("status").trim());
                    RequestStatus current =
                            existing == null ? RequestStatus.CREATED : existing.getStatus();
                    if (current != status && !current.canTransitionTo(status))
                        throw new BusinessException("Недопустимый переход статуса");
                    String flag = r.get("urgent").trim();
                    if (!flag.equals("true") && !flag.equals("false"))
                        throw new BusinessException("urgent: true или false");
                    String tagText = uns(r.get("tags"));
                    Set<String> tags =
                            BookRequestService.normalizeTags(
                                    tagText.isBlank()
                                            ? Set.of()
                                            : new HashSet<>(
                                                    Arrays.asList(tagText.split("\\|", -1))));
                    var data =
                            new BookRequestCreate(
                                    reader.getId(),
                                    uns(r.get("bookTitle")),
                                    uns(r.get("bookAuthor")),
                                    uns(r.get("isbn")),
                                    due,
                                    uns(r.get("comment")),
                                    status,
                                    Integer.valueOf(r.get("requestedCopies").trim()),
                                    Boolean.parseBoolean(flag),
                                    tags,
                                    code.isEmpty() ? null : code);
                    var violations = validator.validate(data);
                    if (!violations.isEmpty())
                        throw new BusinessException(
                                violations.stream()
                                        .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                                        .sorted()
                                        .collect(java.util.stream.Collectors.joining("; ")));
                    rows.add(new Row(line, code, data, existing));
                    versions.put(
                            code.isEmpty() ? "row:" + line : code,
                            existing == null ? -1L : existing.getVersion());
                } catch (BusinessException e) {
                    errors.add(new CsvPreview.RowError(line, e.getMessage()));
                } catch (IllegalArgumentException | DateTimeException e) {
                    errors.add(
                            new CsvPreview.RowError(line, "Некорректное число, дата или статус"));
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (IOException | UncheckedIOException | IllegalArgumentException e) {
            throw new BusinessException("Не удалось прочитать CSV: проверьте кавычки и заголовок");
        }
        if (rows.isEmpty() && errors.isEmpty())
            throw new BusinessException("CSV не содержит записей");
        return new Parsed(rows, errors, versions);
    }

    private String encode(Map<String, Long> map) {
        try {
            return json.writeValueAsString(map);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<String, Long> decode(String value) {
        try {
            return json.readValue(value, new TypeReference<Map<String, Long>>() {});
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    // Reversible escaping for spreadsheet formula prefixes, including multiline text.
    private static boolean formula(String value) {
        String trimmed = value.stripLeading();
        return !trimmed.isEmpty() && "=+@-".indexOf(trimmed.charAt(0)) >= 0;
    }

    private static String safe(String s) {
        if (s == null) return "";
        return s.startsWith("'") || formula(s) ? "'" + s : s;
    }

    private static String uns(String s) {
        return s.startsWith("''") || (s.startsWith("'") && formula(s.substring(1)))
                ? s.substring(1)
                : s;
    }
}
