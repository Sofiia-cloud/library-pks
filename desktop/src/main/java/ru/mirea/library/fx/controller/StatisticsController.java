package ru.mirea.library.fx.controller;

import javafx.beans.property.*;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.*;

import ru.mirea.library.client.ClientContext;
import ru.mirea.library.dto.*;
import ru.mirea.library.fx.util.FxTasks;

import java.util.*;

public class StatisticsController {
    @FXML private Parent view;
    @FXML
    private Label lblTotalReaders,
            lblTotalRequests,
            lblActiveRequests,
            lblReturned,
            lblCancelledRejected,
            lblLast30Days,
            lblAvgPerReader;
    @FXML private Button btnRefresh;
    @FXML private TableView<StatusRow> statusTable;
    @FXML private TableColumn<StatusRow, String> colStatusName, colStatusPct;
    @FXML private TableColumn<StatusRow, Long> colStatusCount;
    @FXML private TableView<NameCountDto> topBooksTable, topAuthorsTable;
    @FXML private TableColumn<NameCountDto, String> colTopBookTitle, colTopAuthorName;
    @FXML private TableColumn<NameCountDto, Long> colTopBookCount, colTopAuthorCount;

    @FXML
    private void initialize() {
        colStatusName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().name()));
        colStatusCount.setCellValueFactory(
                c -> new SimpleLongProperty(c.getValue().count()).asObject());
        colStatusPct.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().percent()));
        colTopBookTitle.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().name()));
        colTopBookCount.setCellValueFactory(
                c -> new SimpleLongProperty(c.getValue().count()).asObject());
        colTopAuthorName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().name()));
        colTopAuthorCount.setCellValueFactory(
                c -> new SimpleLongProperty(c.getValue().count()).asObject());
        btnRefresh.setOnAction(e -> refresh());
        refresh();
    }

    private void refresh() {
        FxTasks.run(
                view,
                () -> ClientContext.api().get("/api/reports/statistics", StatisticsDto.class),
                data -> {
                    lblTotalReaders.setText(Long.toString(data.totalReaders()));
                    lblTotalRequests.setText(Long.toString(data.totalRequests()));
                    lblActiveRequests.setText(Long.toString(data.activeRequests()));
                    lblReturned.setText(Long.toString(data.returnedRequests()));
                    lblCancelledRejected.setText(Long.toString(data.cancelledOrRejected()));
                    lblLast30Days.setText(Long.toString(data.requestsLast30Days()));
                    lblAvgPerReader.setText(String.format("%.2f", data.averageRequestsPerReader()));
                    long total =
                            data.statusCounts().values().stream().mapToLong(Long::longValue).sum();
                    statusTable
                            .getItems()
                            .setAll(
                                    data.statusCounts().entrySet().stream()
                                            .map(
                                                    e ->
                                                            new StatusRow(
                                                                    e.getKey(),
                                                                    e.getValue(),
                                                                    String.format(
                                                                            "%.1f",
                                                                            total == 0
                                                                                    ? 0
                                                                                    : e.getValue()
                                                                                            * 100.0
                                                                                            / total)))
                                            .toList());
                    topBooksTable.getItems().setAll(data.topBooks());
                    topAuthorsTable.getItems().setAll(data.topAuthors());
                });
    }

    private record StatusRow(String name, long count, String percent) {}
}
