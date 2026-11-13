package ru.mirea.library.fx.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.util.StringConverter;

import ru.mirea.library.client.*;
import ru.mirea.library.dto.*;
import ru.mirea.library.fx.util.*;
import ru.mirea.library.model.RequestStatus;

import java.time.LocalTime;
import java.util.*;

public class FilterController extends RequestTableController {
    @FXML private ComboBox<RequestStatus> cmbStatus;
    @FXML private ComboBox<ReaderDto> cmbReader;
    @FXML private DatePicker dateFrom, dateTo;
    @FXML private Button btnApplyStatus, btnApplyReader, btnApplyDateRange, btnReset;

    @FXML
    private void initialize() {
        configureTable();
        cmbStatus.getItems().setAll(RequestStatus.values());
        cmbStatus.setConverter(
                new StringConverter<>() {
                    public String toString(RequestStatus s) {
                        return s == null ? "" : s.getRussianName();
                    }

                    public RequestStatus fromString(String s) {
                        return null;
                    }
                });
        cmbReader.setConverter(
                new StringConverter<>() {
                    public String toString(ReaderDto r) {
                        return r == null ? "" : r.fullName() + " (" + r.libraryCardNumber() + ")";
                    }

                    public ReaderDto fromString(String s) {
                        return null;
                    }
                });
        btnApplyStatus.setOnAction(
                e -> {
                    if (cmbStatus.getValue() == null) AlertHelper.showError("Выберите статус");
                    else load(Map.of("status", cmbStatus.getValue()));
                });
        btnApplyReader.setOnAction(
                e -> {
                    if (cmbReader.getValue() == null) AlertHelper.showError("Выберите читателя");
                    else load(Map.of("readerId", cmbReader.getValue().id()));
                });
        btnApplyDateRange.setOnAction(
                e -> {
                    if (dateFrom.getValue() == null || dateTo.getValue() == null)
                        AlertHelper.showError("Укажите обе даты");
                    else
                        load(
                                Map.of(
                                        "from",
                                        dateFrom.getValue().atStartOfDay(),
                                        "to",
                                        dateTo.getValue().atTime(LocalTime.MAX)));
                });
        btnReset.setOnAction(
                e -> {
                    cmbStatus.setValue(null);
                    cmbReader.setValue(null);
                    dateFrom.setValue(null);
                    dateTo.setValue(null);
                    load(Map.of());
                });
        FxTasks.run(
                view,
                () -> new Loaded(new ReaderApiClient(ClientContext.api()).list(), List.of()),
                data -> {
                    cmbReader.getItems().setAll(data.readers());
                    load(Map.of());
                });
    }

    private record Loaded(List<ReaderDto> readers, List<BookRequestDto> requests) {}
}
