package ru.mirea.library.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mirea.library.domain.Reader;
import ru.mirea.library.error.BusinessException;
import ru.mirea.library.model.RequestStatus;
import ru.mirea.library.repository.ReportRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ReportService {
    private final ReportRepository repository;

    public ReportService(ReportRepository repository) {
        this.repository = repository;
    }

    public Map<String, Long> summary() {
        Map<String, Long> result = new LinkedHashMap<>();
        result.put("Всего заявок", totalRequests());
        result.put("Активных", activeRequests());
        result.put("Просроченных", overdueRequests());
        result.put("Всего читателей", totalReaders());
        return result;
    }

    public long totalRequests() { return repository.totalRequests(); }
    public long totalReaders() { return repository.totalReaders(); }

    public long activeRequests() {
        return repository.countByStatuses(List.of(RequestStatus.CREATED, RequestStatus.APPROVED, RequestStatus.ISSUED));
    }

    public long overdueRequests() {
        return repository.overdueRequests(RequestStatus.ISSUED, LocalDate.now());
    }

    public long returnedRequests() {
        return repository.countByStatuses(List.of(RequestStatus.RETURNED));
    }

    public long cancelledOrRejected() {
        return repository.countByStatuses(List.of(RequestStatus.CANCELLED, RequestStatus.REJECTED));
    }

    public long requestsLast30Days() {
        return repository.requestsSince(LocalDateTime.now().minusDays(30));
    }

    public double averageRequestsPerReader() {
        return repository.averageRequestsPerReader();
    }

    public Map<String, Long> countGroupedByStatus() {
        Map<String, Long> result = new LinkedHashMap<>();
        for (RequestStatus status : RequestStatus.values()) result.put(status.name(), 0L);
        for (ReportRepository.StatusCount row : repository.countGroupedByStatus()) {
            result.put(row.getStatus().name(), row.getCount());
        }
        return result;
    }

    public List<NameCount> topBooks(int limit) {
        return repository.topBooks(page(limit)).stream().map(row -> new NameCount(row.getName(), row.getCount())).toList();
    }

    public List<NameCount> topAuthors(int limit) {
        return repository.topAuthors(page(limit)).stream().map(row -> new NameCount(row.getName(), row.getCount())).toList();
    }

    public List<ReaderActivity> topReaders(int limit) {
        return repository.topReaders(page(limit)).stream()
                .map(row -> new ReaderActivity(row.getId(), row.getName(), row.getCount())).toList();
    }

    public List<ReaderActivity> readerActivity() {
        return repository.readerActivity().stream()
                .map(row -> new ReaderActivity(row.getId(), row.getName(), row.getCount())).toList();
    }

    public List<Reader> readersWithoutRequests() {
        return repository.readersWithoutRequests();
    }

    public Map<Integer, Long> requestsByMonth(int year) {
        if (year < 1900 || year > 2100) throw new BusinessException("Год должен быть в диапазоне 1900..2100");
        Map<Integer, Long> result = new LinkedHashMap<>();
        for (int month = 1; month <= 12; month++) result.put(month, 0L);
        for (ReportRepository.MonthCount row : repository.requestsByMonth(
                LocalDate.of(year, 1, 1).atStartOfDay(), LocalDate.of(year + 1, 1, 1).atStartOfDay())) {
            result.put(row.getMonth(), row.getCount());
        }
        return result;
    }

    public List<ReportRepository.ExportRow> requestsForExport() {
        return repository.requestsForExport();
    }

    private static PageRequest page(int limit) {
        if (limit <= 0) throw new BusinessException("Лимит должен быть положительным");
        return PageRequest.of(0, limit);
    }

    public record NameCount(String name, long count) {
    }

    public record ReaderActivity(UUID readerId, String fullName, long count) {
    }
}
