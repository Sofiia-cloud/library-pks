package ru.mirea.library.service;

/**
 * Показатели статистики (делегирует в ReportService, чтобы не дублировать SQL).
 */
public class StatisticService {

    private final ReportService reportService;

    public StatisticService(ReportService reportService) {
        this.reportService = reportService;
    }

    public long totalReaders()       { return reportService.totalReaders(); }
    public long totalRequests()      { return reportService.totalRequests(); }
    public long activeRequests()     { return reportService.summary().get("Активных"); }
    public long returnedRequests()   { return reportService.returnedRequests(); }
    public long cancelledOrRejected(){ return reportService.cancelledOrRejected(); }
    public long requestsLast30Days() { return reportService.requestsLast30Days(); }
}