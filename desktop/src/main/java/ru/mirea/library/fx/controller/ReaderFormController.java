package ru.mirea.library.fx.controller;

import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.stage.Stage;

import ru.mirea.library.client.*;
import ru.mirea.library.dto.*;
import ru.mirea.library.fx.util.FxTasks;

public class ReaderFormController {
    @FXML private Parent form;
    @FXML private TextField fieldFullName, fieldEmail, fieldPhone, fieldCard;
    @FXML private Button btnSave, btnCancel;
    private final ReaderApiClient readers = new ReaderApiClient(ClientContext.api());
    private ReaderDto reader;
    private boolean saved;

    public boolean isSaved() {
        return saved;
    }

    public void setReader(ReaderDto value) {
        reader = value;
        if (value != null) {
            fieldFullName.setText(value.fullName());
            fieldEmail.setText(value.email());
            fieldPhone.setText(value.phone());
            fieldCard.setText(value.libraryCardNumber());
        }
    }

    @FXML
    private void initialize() {
        btnSave.setOnAction(e -> save());
        btnCancel.setOnAction(e -> close());
    }

    private void save() {
        String name = fieldFullName.getText().trim(),
                email = fieldEmail.getText().trim(),
                phone = fieldPhone.getText().trim(),
                card = fieldCard.getText().trim();
        ReaderDto current = reader;
        FxTasks.run(
                form,
                () ->
                        current == null
                                ? readers.create(new ReaderCreate(name, email, phone, card))
                                : readers.update(
                                        current.id(),
                                        new ReaderUpdate(
                                                current.version(), name, email, phone, card)),
                result -> {
                    saved = true;
                    close();
                });
    }

    private void close() {
        ((Stage) btnSave.getScene().getWindow()).close();
    }
}
