package ru.mirea.library.fx.controller;

import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import ru.mirea.library.fx.util.AlertHelper;
import ru.mirea.library.repository.ReportRepository;
import ru.mirea.library.service.ReportService;
import ru.mirea.library.service.StatisticService;

import java.util.List;
import java.util.Map;

public class StatisticsController {

    @FXML private Label lblTotalReaders;
    @FXML private Label lblTotalRequests;
    @FXML private Label lblActiveRequests;
    @FXML private Label lblReturned;
    @FXML private Label lblCancelledRejected;
    @FXML private Label lblLast30Days;
    @FXML private Label lblAvgPerReader;

    @FXML private Button btnRefresh;

    @FXML private TableView<StatusRow> statusTable;
    @FXML private TableColumn<StatusRow, String> colStatusName;
    @FXML private TableColumn<StatusRow, Long>   colStatusCount;
    @FXML private TableColumn<StatusRow, String> colStatusPct;

    @FXML private TableView<TopRow> topBooksTable;
    @FXML private TableColumn<TopRow, String> colTopBookTitle;
    @FXML private TableColumn<TopRow, Long>   colTopBookCount;

    @FXML private TableView<TopRow> topAuthorsTable;
    @FXML private TableColumn<TopRow, String> colTopAuthorName;
    @FXML private TableColumn<TopRow, Long>   colTopAuthorCount;

    private final ReportService reportService =
            new ReportService(new ReportRepository());
    private final StatisticService statisticService =
            new StatisticService(reportService);

    private final ObservableList<StatusRow> statusRows = FXCollections.observableArrayList();
    private final ObservableList<TopRow> topBooksRows = FXCollections.observableArrayList();
    private final ObservableList<TopRow> topAuthorsRows = FXCollections.observableArrayList();

    public static class StatusRow {
        public final String name;
        public final long count;
        public final String pct;
        public StatusRow(String name, long count, String pct) {
            this.name = name; this.count = count; this.pct = pct;
        }
    }

    public static class TopRow {
        public final String name;
        public final long count;
        public TopRow(String name, long count) {
            this.name = name; this.count = count;
        }
    }

    @FXML
    private void initialize() {
        colStatusName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().name));
        colStatusCount.setCellValueFactory(c -> new SimpleLongProperty(c.getValue().count).asObject());
        colStatusPct.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().pct));
        statusTable.setItems(statusRows);

        colTopBookTitle.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().name));
        colTopBookCount.setCellValueFactory(c -> new SimpleLongProperty(c.getValue().count).asObject());
        topBooksTable.setItems(topBooksRows);

        colTopAuthorName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().name));
        colTopAuthorCount.setCellValueFactory(c -> new SimpleLongProperty(c.getValue().count).asObject());
        topAuthorsTable.setItems(topAuthorsRows);

        btnRefresh.setOnAction(e -> refresh());

        refresh();
    }

    private void refresh() {
        try {
            // Краткие показатели
            lblTotalReaders.setText(String.valueOf(statisticService.totalReaders()));
            lblTotalRequests.setText(String.valueOf(statisticService.totalRequests()));
            lblActiveRequests.setText(String.valueOf(statisticService.activeRequests()));
            lblReturned.setText(String.valueOf(statisticService.returnedRequests()));
            lblCancelledRejected.setText(String.valueOf(statisticService.cancelledOrRejected()));
            lblLast30Days.setText(String.valueOf(statisticService.requestsLast30Days()));
            lblAvgPerReader.setText(String.format("%.2f", reportService.averageRequestsPerReader()));

            // Распределение по статусам с процентами
            Map<String, Long> statusMap = reportService.countGroupedByStatus();
            long total = statusMap.values().stream().mapToLong(Long::longValue).sum();
            statusRows.clear();
            statusMap.forEach((status, count) -> {
                double pct = total == 0 ? 0.0 : count * 100.0 / total;
                statusRows.add(new StatusRow(status, count, String.format("%.1f", pct)));
            });

            // Топ книг
            List<Map.Entry<String, Long>> topBooks = reportService.topBooks(5);
            topBooksRows.clear();
            for (Map.Entry<String, Long> e : topBooks) {
                topBooksRows.add(new TopRow(e.getKey(), e.getValue()));
            }

            // Топ авторов
            List<Map.Entry<String, Long>> topAuthors = reportService.topAuthors(5);
            topAuthorsRows.clear();
            for (Map.Entry<String, Long> e : topAuthors) {
                topAuthorsRows.add(new TopRow(e.getKey(), e.getValue()));
            }

        } catch (Exception ex) {
            AlertHelper.showError("Не удалось загрузить статистику: " + ex.getMessage());
        }
    }
}