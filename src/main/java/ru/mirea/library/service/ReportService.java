package ru.mirea.library.service;

import ru.mirea.library.exception.BusinessException;
import ru.mirea.library.repository.ReportRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Бизнес-логика отчётов: валидация параметров + обёртки над репозиторием.
 */
public class ReportService {

    private final ReportRepository repo;

    public ReportService(ReportRepository repo) {
        this.repo = repo;
    }

    public Map<String, Long> summary() {
        Map<String, Long> m = new LinkedHashMap<>();
        m.put("Всего заявок", repo.totalRequests());
        m.put("Активных", repo.activeRequests());
        m.put("Просроченных", repo.overdueRequests());
        m.put("Всего читателей", repo.countAllReaders());
        return m;
    }

    public double averageRequestsPerReader() {
        return repo.averageRequestsPerReader();
    }

    public Map<String, Long> countGroupedByStatus() {
        return repo.countGroupedByStatus();
    }

    public List<Map.Entry<String, Long>> topBooks(int limit) {
        validateLimit(limit);
        return repo.topBooks(limit);
    }

    public List<Map.Entry<String, Long>> topAuthors(int limit) {
        validateLimit(limit);
        return repo.topAuthors(limit);
    }

    public List<Map.Entry<String, Long>> topReaders(int limit) {
        validateLimit(limit);
        return repo.topReaders(limit);
    }

    public Map<String, Long> readerActivity() {
        return repo.readerActivity();
    }

    public List<String> readersWithoutRequests() {
        return repo.readersWithoutRequests();
    }

    public Map<Integer, Long> requestsByMonth(int year) {
        validateYear(year);
        return repo.requestsByMonth(year);
    }

    public long returnedRequests()    { return repo.returnedRequests(); }
    public long cancelledOrRejected() { return repo.cancelledOrRejected(); }
    public long requestsLast30Days()  { return repo.requestsLast30Days(); }
    public long totalRequests()       { return repo.totalRequests(); }
    public long totalReaders()        { return repo.countAllReaders(); }

    private void validateLimit(int limit) {
        if (limit <= 0) {
            throw new BusinessException("Лимит должен быть положительным (получено " + limit + ")");
        }
    }

    private void validateYear(int year) {
        if (year < 1900 || year > 2100) {
            throw new BusinessException("Год должен быть в диапазоне 1900..2100 (получено " + year + ")");
        }
    }
}