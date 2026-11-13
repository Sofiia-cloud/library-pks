package ru.mirea.library.fx.controller;

import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import ru.mirea.library.client.*;
import ru.mirea.library.dto.*;
import ru.mirea.library.fx.util.FxTasks;
import ru.mirea.library.model.RequestStatus;

import java.util.*;

public class RequestFormController {
    @FXML private Parent form;
    @FXML private ComboBox<ReaderDto> cbReader;
    @FXML private ComboBox<RequestStatus> cbStatus;
    @FXML private TextField fieldBookTitle, fieldAuthor, fieldIsbn;
    @FXML private DatePicker dpReturnDate;
    @FXML private TextArea taComment;
    @FXML private Label lblError;
    @FXML private Button btnSave, btnCancel;
    private final BookRequestApiClient requests = new BookRequestApiClient(ClientContext.api());
    private BookRequestDto request;
    private boolean saved;

    public boolean isSaved() {
        return saved;
    }

    @FXML
    private void initialize() {
        cbReader.setConverter(
                new StringConverter<>() {
                    public String toString(ReaderDto r) {
                        return r == null ? "" : r.fullName() + " (" + r.libraryCardNumber() + ")";
                    }

                    public ReaderDto fromString(String s) {
                        return null;
                    }
                });
        cbStatus.setConverter(
                new StringConverter<>() {
                    public String toString(RequestStatus s) {
                        return s == null ? "" : s.getRussianName();
                    }

                    public RequestStatus fromString(String s) {
                        return null;
                    }
                });
        btnSave.setOnAction(e -> save());
        btnCancel.setOnAction(e -> close());
        FxTasks.run(
                form,
                () -> new ReaderApiClient(ClientContext.api()).list(),
                readers -> {
                    cbReader.getItems()
                            .setAll(
                                    readers.stream()
                                            .filter(
                                                    r ->
                                                            request == null
                                                                    ? r.active()
                                                                    : r.id().equals(
                                                                                    request
                                                                                            .readerId()))
                                            .toList());
                    if (request != null) cbReader.getSelectionModel().selectFirst();
                });
    }

    public void setRequest(BookRequestDto value) {
        request = value;
        RequestStatus current = value == null ? RequestStatus.CREATED : value.status();
        cbStatus.getItems()
                .setAll(
                        Arrays.stream(RequestStatus.values())
                                .filter(s -> s == current || current.canTransitionTo(s))
                                .toList());
        cbStatus.setValue(current);
        cbReader.setDisable(value != null);
        if (value != null) {
            fieldBookTitle.setText(value.bookTitle());
            fieldAuthor.setText(value.bookAuthor());
            fieldIsbn.setText(value.isbn());
            dpReturnDate.setValue(value.desiredReturnDate());
            taComment.setText(value.comment());
        }
    }

    private void save() {
        ReaderDto reader = cbReader.getValue();
        if (reader == null) {
            lblError.setText("Выберите читателя");
            return;
        }
        String title = fieldBookTitle.getText(),
                author = fieldAuthor.getText(),
                isbn = fieldIsbn.getText(),
                comment = taComment.getText();
        var due = dpReturnDate.getValue();
        var status = cbStatus.getValue();
        lblError.setText("");
        BookRequestDto existing = request;
        FxTasks.run(
                form,
                () ->
                        existing == null
                                ? requests.create(
                                        new BookRequestCreate(
                                                reader.id(),
                                                title,
                                                author,
                                                isbn,
                                                due,
                                                comment,
                                                status))
                                : requests.update(
                                        existing.id(),
                                        new BookRequestUpdate(
                                                existing.version(),
                                                title,
                                                author,
                                                isbn,
                                                due,
                                                comment,
                                                status)),
                result -> {
                    saved = true;
                    close();
                },
                error -> {
                    if (error instanceof ApiException api && api.status() == 409)
                        lblError.setText(
                                "Запись изменена. Закройте форму, обновите список и повторите"
                                    + " редактирование");
                    else if (error instanceof ApiException api && api.status() == 401)
                        FxTasks.showError(error);
                    else lblError.setText(error.getMessage());
                });
    }

    private void close() {
        ((Stage) btnSave.getScene().getWindow()).close();
    }
}
