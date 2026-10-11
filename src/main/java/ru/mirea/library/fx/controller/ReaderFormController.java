package ru.mirea.library.fx.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import ru.mirea.library.exception.BusinessException;
import ru.mirea.library.fx.util.AlertHelper;
import ru.mirea.library.model.Reader;
import ru.mirea.library.repository.ReaderRepository;
import ru.mirea.library.service.ReaderService;

public class ReaderFormController {

    @FXML private TextField fieldFullName;
    @FXML private TextField fieldEmail;
    @FXML private TextField fieldPhone;
    @FXML private TextField fieldCard;

    @FXML private Button btnSave;
    @FXML private Button btnCancel;

    private final ReaderService readerService =
            new ReaderService(new ReaderRepository());

    private Reader reader;
    private boolean saved = false;

    public boolean isSaved() {
        return saved;
    }

    public void setReader(Reader reader) {
        this.reader = reader;
        if (reader != null) {
            fieldFullName.setText(reader.getFullName());
            fieldEmail.setText(reader.getEmail());
            fieldPhone.setText(reader.getPhone() == null ? "" : reader.getPhone());
            fieldCard.setText(reader.getLibraryCardNumber());
        }
    }

    @FXML
    private void initialize() {
        btnSave.setOnAction(e -> onSave());
        btnCancel.setOnAction(e -> close());
    }

    private void onSave() {
        String fullName = fieldFullName.getText();
        String email = fieldEmail.getText();
        String phone = fieldPhone.getText();
        String card = fieldCard.getText();

        try {
            if (reader == null) {
                readerService.create(fullName, email, phone, card);
            } else {
                readerService.update(reader.getId(), fullName, email, phone, card);
            }
            saved = true;
            close();
        } catch (BusinessException ex) {
            AlertHelper.showError(ex.getMessage());
        } catch (Exception ex) {
            AlertHelper.showError("Ошибка сохранения: " + ex.getMessage());
        }
    }

    private void close() {
        ((Stage) btnSave.getScene().getWindow()).close();
    }
}