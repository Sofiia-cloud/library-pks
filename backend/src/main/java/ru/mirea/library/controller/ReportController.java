package ru.mirea.library.controller;

import org.springframework.web.bind.annotation.*;

import ru.mirea.library.dto.*;
import ru.mirea.library.service.ReportService;

import java.util.*;

@RestController
@RequestMapping("/api/reports")
public class ReportController {
    private final ReportService reports;

    public ReportController(ReportService reports) {
        this.reports = reports;
    }

    @GetMapping("/summary")
    public Map<String, Long> summary() {
        return reports.summary();
    }

    @GetMapping("/statistics")
    public StatisticsDto statistics() {
        return new StatisticsDto(
                reports.totalReaders(),
                reports.totalRequests(),
                reports.activeRequests(),
                reports.returnedRequests(),
                reports.cancelledOrRejected(),
                reports.requestsLast30Days(),
                reports.averageRequestsPerReader(),
                reports.countGroupedByStatus(),
                topBooks(5),
                topAuthors(5));
    }

    @GetMapping("/top-books")
    public List<NameCountDto> topBooks(
            @RequestParam(value = "limit", defaultValue = "5") int limit) {
        return reports.topBooks(limit).stream()
                .map(row -> new NameCountDto(row.name(), row.count()))
                .toList();
    }

    @GetMapping("/top-authors")
    public List<NameCountDto> topAuthors(
            @RequestParam(value = "limit", defaultValue = "5") int limit) {
        return reports.topAuthors(limit).stream()
                .map(row -> new NameCountDto(row.name(), row.count()))
                .toList();
    }

    @GetMapping("/top-readers")
    public List<ReaderActivityDto> topReaders(
            @RequestParam(value = "limit", defaultValue = "5") int limit) {
        return reports.topReaders(limit).stream()
                .map(row -> new ReaderActivityDto(row.readerId(), row.fullName(), row.count()))
                .toList();
    }

    @GetMapping("/reader-activity")
    public List<ReaderActivityDto> activity() {
        return reports.readerActivity().stream()
                .map(row -> new ReaderActivityDto(row.readerId(), row.fullName(), row.count()))
                .toList();
    }

    @GetMapping("/readers-without-requests")
    public List<ReaderDto> withoutRequests() {
        return reports.readersWithoutRequests().stream().map(DtoMapper::reader).toList();
    }

    @GetMapping("/by-month")
    public Map<Integer, Long> months(@RequestParam("year") int year) {
        return reports.requestsByMonth(year);
    }

    @GetMapping("/export-data")
    public List<ExportRowDto> exportData() {
        return reports.requestsForExport().stream()
                .map(
                        row ->
                                new ExportRowDto(
                                        row.getId(),
                                        row.getRequestNumber(),
                                        row.getReaderId(),
                                        row.getReaderName(),
                                        row.getBookTitle(),
                                        row.getBookAuthor(),
                                        row.getIsbn(),
                                        row.getStatus(),
                                        row.getCreatedAt(),
                                        row.getDesiredReturnDate(),
                                        row.getComment()))
                .toList();
    }
}
