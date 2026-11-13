package ru.mirea.library.fx.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import ru.mirea.library.exception.BusinessException;
import ru.mirea.library.fx.util.AlertHelper;
import ru.mirea.library.model.BookRequest;
import ru.mirea.library.model.Reader;
import ru.mirea.library.model.RequestStatus;
import ru.mirea.library.repository.BookRequestRepository;
import ru.mirea.library.repository.ReaderRepository;
import ru.mirea.library.service.BookRequestService;
import ru.mirea.library.service.ReaderService;

import java.time.LocalDate;
import java.util.List;

public class RequestFormController {

    @FXML private ComboBox<Reader>        cbReader;
    @FXML private TextField               fieldBookTitle;
    @FXML private TextField               fieldAuthor;
    @FXML private TextField               fieldIsbn;
    @FXML private ComboBox<RequestStatus> cbStatus;
    @FXML private DatePicker              dpReturnDate;
    @FXML private TextArea                taComment;
    @FXML private Label                   lblError;
    @FXML private Button                  btnSave;
    @FXML private Button                  btnCancel;

    private final ReaderService readerService =
            new ReaderService(new ReaderRepository());
    private final BookRequestService requestService =
            new BookRequestService(new BookRequestRepository(), readerService);

    private BookRequest request;      // null → добавление
    private boolean saved = false;

    public boolean isSaved() {
        return saved;
    }

    @FXML
    private void initialize() {
        // Конвертер для отображения Reader в ComboBox
        cbReader.setConverter(new StringConverter<>() {
            @Override
            public String toString(Reader r) {
                return r == null ? "" : r.getFullName() + " (" + r.getLibraryCardNumber() + ")";
            }
            @Override
            public Reader fromString(String s) {
                return null;
            }
        });

        // Конвертер для статусов (русские названия)
        cbStatus.getItems().setAll(RequestStatus.values());
        cbStatus.setConverter(new StringConverter<>() {
            @Override
            public String toString(RequestStatus s) {
                return s == null ? "" : s.getRussianName();
            }
            @Override
            public RequestStatus fromString(String s) {
                return null;
            }
        });

        btnSave.setOnAction(e -> onSave());
        btnCancel.setOnAction(e -> close());
    }

    /** Заполняет ComboBox читателей (вызывается из RequestController). */
    public void setReaders(List<Reader> readers) {
        cbReader.getItems().setAll(readers);
    }

    /** Устанавливает редактируемую заявку (null → режим добавления). */
    public void setRequest(BookRequest request) {
        this.request = request;
        if (request == null) {
            cbStatus.setValue(RequestStatus.CREATED);
            return;
        }

        // Находим читателя по readerId и делаем его единственным в списке
        Reader r = cbReader.getItems().stream()
                .filter(reader -> reader.getId() == request.getReaderId())
                .findFirst()
                .orElse(null);

        if (r != null) {
            // Оставляем в ComboBox только этого читателя — выбрать другого нельзя
            cbReader.getItems().setAll(r);
            cbReader.setValue(r);
        }

        // Блокируем взаимодействие
        cbReader.setMouseTransparent(true);
        cbReader.setFocusTraversable(false);

        fieldBookTitle.setText(request.getBookTitle());
        fieldAuthor.setText(request.getBookAuthor());
        fieldIsbn.setText(request.getIsbn() == null ? "" : request.getIsbn());
        cbStatus.setValue(request.getStatus());
        dpReturnDate.setValue(request.getDesiredReturnDate());
        taComment.setText(request.getComment() == null ? "" : request.getComment());
    }

    private void onSave() {
        lblError.setText("");
        String error = validateInput();
        if (error != null) {
            lblError.setText(error);
            return;
        }

        try {
            if (request == null) {
                // Создание
                Reader reader = cbReader.getValue();
                BookRequest created = requestService.create(
                        reader.getId(),
                        fieldBookTitle.getText(),
                        fieldAuthor.getText(),
                        fieldIsbn.getText(),
                        dpReturnDate.getValue(),
                        taComment.getText()
                );
                RequestStatus chosen = cbStatus.getValue();
                if (chosen != null && chosen != created.getStatus()) {
                    requestService.changeStatus(created.getId(), chosen);
                }
            } else {
                // Обновление текстовых полей
                requestService.update(
                        request.getId(),
                        fieldBookTitle.getText(),
                        fieldAuthor.getText(),
                        fieldIsbn.getText(),
                        dpReturnDate.getValue(),
                        taComment.getText()
                );
                // Отдельно — смена статуса (с проверкой canTransitionTo)
                RequestStatus chosen = cbStatus.getValue();
                if (chosen != null && chosen != request.getStatus()) {
                    requestService.changeStatus(request.getId(), chosen);
                }
            }
            saved = true;
            close();
        } catch (BusinessException ex) {
            lblError.setText(ex.getMessage());
        } catch (Exception ex) {
            AlertHelper.showError("Ошибка сохранения: " + ex.getMessage());
        }
    }

    /** UI-валидация. Бизнес-валидация — в Service. */
    private String validateInput() {
        if (cbReader.getValue() == null) {
            return "Выберите читателя";
        }
        if (fieldBookTitle.getText() == null || fieldBookTitle.getText().isBlank()) {
            return "Введите название книги";
        }
        if (fieldBookTitle.getText().length() > 200) {
            return "Название книги не должно превышать 200 символов";
        }
        if (fieldAuthor.getText() == null || fieldAuthor.getText().isBlank()) {
            return "Введите автора книги";
        }
        if (fieldAuthor.getText().length() > 150) {
            return "Имя автора не должно превышать 150 символов";
        }
        if (fieldIsbn.getText() != null && fieldIsbn.getText().length() > 20) {
            return "ISBN не должен превышать 20 символов";
        }
        if (dpReturnDate.getValue() != null
                && dpReturnDate.getValue().isBefore(LocalDate.now())) {
            return "Дата возврата не может быть в прошлом";
        }
        if (cbStatus.getValue() == null) {
            return "Выберите статус";
        }
        return null;
    }

    private void close() {
        ((Stage) btnSave.getScene().getWindow()).close();
    }
}