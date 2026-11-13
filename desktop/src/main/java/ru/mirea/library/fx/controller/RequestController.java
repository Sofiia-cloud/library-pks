package ru.mirea.library.fx.controller;

import javafx.fxml.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;

import ru.mirea.library.client.*;
import ru.mirea.library.dto.*;
import ru.mirea.library.fx.util.*;
import ru.mirea.library.model.*;

import java.io.*;
import java.util.*;

public class RequestController extends RequestTableController {
    @FXML private Button btnAdd, btnEdit, btnDelete, btnRefresh;
    private final ApiClient api = ClientContext.api();

    @FXML
    private void initialize() {
        configureTable();
        resultTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        btnAdd.setDisable(!api.canEdit());
        btnEdit.setDisable(!api.canEdit());
        btnDelete.setDisable(!api.canEdit());
        btnDelete.setText("В архив");
        btnAdd.setOnAction(e -> openForm(null));
        btnEdit.setOnAction(
                e -> {
                    var r = selected();
                    if (r != null) openForm(r);
                });
        btnDelete.setOnAction(e -> archive());
        btnRefresh.setOnAction(e -> reload());
        addColumn("Копий", r -> String.valueOf(r.requestedCopies()));
        addColumn("Срочная", r -> r.urgent() ? "Да" : "Нет");
        addColumn("Теги", r -> String.join(", ", new TreeSet<>(r.tags())));
        addColumn("Состояние", r -> Labels.text(r.returnState()));
        addColumn("Архив", r -> r.archived() ? "Да" : "Нет");
        var restore = new Button("Восстановить");
        restore.setDisable(!api.canEdit());
        restore.setOnAction(
                e -> {
                    var r = selected();
                    if (r != null)
                        FxTasks.run(
                                view,
                                () ->
                                        api.post(
                                                "/api/book-requests/" + r.id() + "/restore",
                                                new VersionRequest(r.version()),
                                                BookRequestDto.class),
                                v -> reload());
                });
        var permanent = new Button("Удалить навсегда");
        permanent.setVisible(api.isAdmin());
        permanent.setManaged(api.isAdmin());
        permanent.setOnAction(
                e -> {
                    var r = selected();
                    if (r != null
                            && AlertHelper.confirm(
                                    "Окончательно удалить заявку №"
                                            + r.requestNumber()
                                            + " и её заметки?"))
                        FxTasks.run(
                                view,
                                () -> {
                                    api.delete(
                                            "/api/book-requests/"
                                                    + r.id()
                                                    + "/permanent?version="
                                                    + r.version());
                                    return null;
                                },
                                v -> reload());
                });
        var notes = new Button("Заметки");
        notes.setOnAction(
                e -> {
                    var r = selected();
                    if (r != null) new NotesWindow().show(view.getScene().getWindow(), r);
                });
        var csv = new Button("Импорт CSV");
        csv.setDisable(!api.canEdit());
        csv.setOnAction(e -> importCsv());
        ((VBox) view).getChildren().add(2, new FlowPane(8, 8, restore, permanent, notes, csv));
        filters();
        bulkControls();
        load(Map.of());
    }

    private void addColumn(String name, java.util.function.Function<BookRequestDto, String> value) {
        var c = new TableColumn<BookRequestDto, String>(name);
        c.setCellValueFactory(
                row -> new javafx.beans.property.SimpleStringProperty(value.apply(row.getValue())));
        c.setSortable(false);
        resultTable.getColumns().add(c);
    }

    private BookRequestDto selected() {
        var r = resultTable.getSelectionModel().getSelectedItem();
        if (r == null) AlertHelper.showError("Выберите заявку");
        return r;
    }

    private void filters() {
        var text = new TextField();
        text.setPromptText("Книга, автор, читатель, билет");
        text.setPrefWidth(225);
        var tag = new TextField();
        tag.setPromptText("Тег");
        tag.setPrefWidth(110);
        var scope = new ComboBox<ArchiveScope>();
        scope.getItems().setAll(ArchiveScope.values());
        scope.setValue(ArchiveScope.ACTIVE);
        Labels.configure(scope);
        var state = new ComboBox<ReturnState>();
        state.getItems().add(null);
        state.getItems().addAll(ReturnState.values());
        state.setPromptText("Состояние");
        Labels.configure(state);
        var urgent = new ComboBox<String>();
        urgent.getItems().setAll("Любая срочность", "Срочные", "Обычные");
        urgent.setValue("Любая срочность");
        var status = new ComboBox<RequestStatus>();
        status.getItems().add(null);
        status.getItems().addAll(RequestStatus.values());
        status.setPromptText("Статус");
        Labels.configure(status);
        var reader = new ComboBox<ReaderDto>();
        reader.getItems().add(null);
        reader.setPromptText("Читатель");
        reader.setPrefWidth(210);
        reader.setConverter(
                new javafx.util.StringConverter<ReaderDto>() {
                    public String toString(ReaderDto r) {
                        return r == null
                                ? "Все читатели"
                                : r.fullName() + " · " + r.libraryCardNumber();
                    }

                    public ReaderDto fromString(String s) {
                        return null;
                    }
                });
        FxTasks.run(
                null,
                () -> new ReaderApiClient(api).list(),
                rows -> reader.getItems().addAll(rows));
        var op = new ComboBox<CopiesComparison>();
        op.getItems().add(null);
        op.getItems().addAll(CopiesComparison.values());
        Labels.configure(op);
        op.setPromptText("Сравнение");
        var copies = new TextField();
        copies.setPromptText("Копий");
        copies.setPrefWidth(65);
        var sort = new ComboBox<RequestSort>();
        sort.getItems().setAll(RequestSort.values());
        sort.setValue(RequestSort.NUMBER);
        Labels.configure(sort);
        var descending = new CheckBox("По убыванию");
        var apply = new Button("Применить");
        var reset = new Button("Сбросить");
        apply.setOnAction(
                e -> {
                    var f = new LinkedHashMap<String, Object>();
                    f.put("q", text.getText());
                    f.put("tag", tag.getText());
                    f.put("scope", scope.getValue());
                    f.put("returnState", state.getValue());
                    f.put("status", status.getValue());
                    f.put("readerId", reader.getValue() == null ? null : reader.getValue().id());
                    f.put(
                            "urgent",
                            urgent.getSelectionModel().getSelectedIndex() == 0
                                    ? null
                                    : urgent.getSelectionModel().getSelectedIndex() == 1);
                    f.put("copiesOp", op.getValue());
                    if (!copies.getText().isBlank()) {
                        try {
                            f.put("copiesValue", Integer.valueOf(copies.getText().trim()));
                        } catch (NumberFormatException ex) {
                            AlertHelper.showError("Количество должно быть целым числом");
                            return;
                        }
                    }
                    f.put("sort", sort.getValue());
                    f.put("ascending", !descending.isSelected());
                    load(f);
                });
        reset.setOnAction(
                e -> {
                    text.clear();
                    tag.clear();
                    copies.clear();
                    op.setValue(null);
                    state.setValue(null);
                    status.setValue(null);
                    reader.setValue(null);
                    urgent.getSelectionModel().selectFirst();
                    scope.setValue(ArchiveScope.ACTIVE);
                    sort.setValue(RequestSort.NUMBER);
                    descending.setSelected(false);
                    load(Map.of());
                });
        ((VBox) view)
                .getChildren()
                .add(
                        3,
                        new FlowPane(
                                8,
                                8,
                                text,
                                reader,
                                status,
                                scope,
                                urgent,
                                state,
                                tag,
                                new Label("Количество:"),
                                op,
                                copies,
                                sort,
                                descending,
                                apply,
                                reset));
    }

    private void bulkControls() {
        var action = new ComboBox<BulkAction>();
        action.getItems().setAll(BulkAction.values());
        action.setValue(BulkAction.ARCHIVE);
        Labels.configure(action);
        var status = new ComboBox<RequestStatus>();
        status.getItems().setAll(RequestStatus.values());
        Labels.configure(status);
        status.setPromptText("Новый статус");
        var tag = new TextField();
        tag.setPromptText("Тег для операции");
        var apply = new Button("Выполнить для выбранных");
        apply.setDisable(!api.canEdit());
        apply.setOnAction(
                e -> {
                    var rows = List.copyOf(resultTable.getSelectionModel().getSelectedItems());
                    if (rows.isEmpty()) {
                        AlertHelper.showError("Выберите строки (Ctrl / Shift)");
                        return;
                    }
                    var body =
                            new BulkRequest(
                                    rows.stream()
                                            .map(r -> new VersionedId(r.id(), r.version()))
                                            .toList(),
                                    action.getValue(),
                                    status.getValue(),
                                    tag.getText());
                    if (!AlertHelper.confirm(
                            "Выполнить «"
                                    + Labels.text(action.getValue())
                                    + "» для "
                                    + rows.size()
                                    + " заявок?")) return;
                    FxTasks.run(
                            view,
                            () -> api.post("/api/book-requests/bulk", body, BulkResult.class),
                            r -> {
                                AlertHelper.showInfo(
                                        "Успешно: "
                                                + r.successful()
                                                + ", ошибок: "
                                                + r.failed()
                                                + "\n"
                                                + r.results().stream()
                                                        .filter(x -> !x.success())
                                                        .map(x -> x.id() + ": " + x.message())
                                                        .collect(
                                                                java.util.stream.Collectors.joining(
                                                                        "\n")));
                                reload();
                            });
                });
        ((VBox) view)
                .getChildren()
                .add(
                        4,
                        new FlowPane(
                                8, 8, new Label("Массовая операция:"), action, status, tag, apply));
    }

    private void openForm(BookRequestDto request) {
        if (request != null && request.archived()) {
            AlertHelper.showError("Сначала восстановите заявку");
            return;
        }
        try (InputStream stream = getClass().getResourceAsStream("/fxml/request-form.fxml")) {
            FXMLLoader loader = new FXMLLoader();
            Parent root = loader.load(stream);
            Stage d = new Stage();
            d.initOwner(view.getScene().getWindow());
            d.initModality(Modality.WINDOW_MODAL);
            d.setTitle(request == null ? "Новая заявка" : "Заявка №" + request.requestNumber());
            d.setScene(new Scene(root, 560, 650));
            RequestFormController c = loader.getController();
            c.setRequest(request);
            d.showAndWait();
            if (c.isSaved()) reload();
        } catch (IOException ex) {
            AlertHelper.showError("Не удалось открыть форму: " + ex.getMessage());
        }
    }

    private void archive() {
        var r = selected();
        if (r == null
                || !AlertHelper.confirm("Переместить заявку №" + r.requestNumber() + " в архив?"))
            return;
        FxTasks.run(
                view,
                () -> {
                    requests.delete(r);
                    return null;
                },
                v -> reload());
    }

    private void importCsv() {
        var chooser = new FileChooser();
        chooser.setTitle("Выберите CSV (UTF-8)");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        var file = chooser.showOpenDialog(view.getScene().getWindow());
        if (file == null) return;
        var dialog = new ChoiceDialog<>(ImportMode.NEW_ONLY, ImportMode.values());
        dialog.setTitle("Импорт CSV");
        dialog.setHeaderText(
                "Режим импорта: NEW_ONLY — только новые; UPSERT — обновление по внешнему коду");
        dialog.initOwner(view.getScene().getWindow());
        dialog.showAndWait()
                .ifPresent(
                        mode ->
                                FxTasks.run(
                                        view,
                                        () -> {
                                            if (java.nio.file.Files.size(file.toPath()) > 5000000)
                                                throw new IllegalArgumentException(
                                                        "Файл больше 5 МБ");
                                            return api.post(
                                                    "/api/book-requests/import/preview",
                                                    new CsvPreviewRequest(
                                                            java.nio.file.Files.readString(
                                                                    file.toPath()),
                                                            mode),
                                                    CsvPreview.class);
                                        },
                                        preview -> {
                                            var message =
                                                    "Корректных строк: "
                                                            + preview.validRows()
                                                            + ", ошибочных: "
                                                            + preview.invalidRows()
                                                            + "\n"
                                                            + preview.errors().stream()
                                                                    .map(
                                                                            x ->
                                                                                    "Строка "
                                                                                            + x
                                                                                                    .row()
                                                                                            + ": "
                                                                                            + x
                                                                                                    .message())
                                                                    .collect(
                                                                            java.util.stream
                                                                                    .Collectors
                                                                                    .joining("\n"));
                                            if (preview.invalidRows() > 0) {
                                                AlertHelper.showError(message);
                                                return;
                                            }
                                            if (AlertHelper.confirm(
                                                    message + "\nПрименить импорт?"))
                                                FxTasks.run(
                                                        view,
                                                        () ->
                                                                api.post(
                                                                        "/api/book-requests/import/apply",
                                                                        new CsvApply(preview.id()),
                                                                        CsvResult.class),
                                                        r -> {
                                                            AlertHelper.showInfo(
                                                                    "Создано: "
                                                                            + r.created()
                                                                            + ", обновлено: "
                                                                            + r.updated());
                                                            reload();
                                                        });
                                        }));
    }
}
