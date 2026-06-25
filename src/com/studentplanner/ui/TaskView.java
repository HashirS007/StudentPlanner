package com.studentplanner.ui;

import com.studentplanner.model.Priority;
import com.studentplanner.model.Subject;
import com.studentplanner.model.Task;
import com.studentplanner.model.TaskStatus;
import com.studentplanner.model.TaskType;
import com.studentplanner.service.SubjectService;
import com.studentplanner.service.TaskService;
import com.studentplanner.service.exception.NotFoundException;
import com.studentplanner.service.exception.ValidationException;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.StageStyle;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.Scene;

/*
 The Tasks screen.
 Features:
 - Sortable table of all tasks owned by the current student.
 - Subject column shows the SUBJECT NAME (looked up via a cache map).
 - Overdue tasks are highlighted in red; completed tasks are dimmed.
 - Filter dropdown lets the user view: All / Pending / In Progress / Completed / Overdue.
 - Add / Edit / Delete via dialog (8-field form).
 Architecture:
 - All business logic delegated to TaskService.
 - Subject lookups (for the dropdown and the table column) go through SubjectService.
 - Coloring uses TableRow.setRowFactory; filtering uses FilteredList.
 */
public class TaskView {

    private final TaskService taskService;
    private final SubjectService subjectService;

    /* Source list; never replaced, only mutated. */
    private final ObservableList<Task> tasks = FXCollections.observableArrayList();

    /* A live-filtered view of `tasks`. Changing its predicate re-renders the table. */
    private FilteredList<Task> filteredTasks;

    /* Cache: subject_id -> subject_name, for fast lookup in the table column. */
    private final Map<Integer, String> subjectNameById = new HashMap<>();

    /* All subjects for the current student backs the ComboBox in the dialog. */
    private List<Subject> allSubjects = List.of();

    private TableView<Task> table;
    private ChoiceBox<String> filterChoice;
    private TextField searchField;

    public TaskView(TaskService taskService, SubjectService subjectService) {
        this.taskService = taskService;
        this.subjectService = subjectService;
    }

    // VIEW BUILDING

    public Parent getView() {
        Label title = new Label("My Tasks");
        title.getStyleClass().add("h1");

        // ---------- Filter dropdown ----------
        filterChoice = new ChoiceBox<>();
        filterChoice.getItems().addAll("All", "Pending", "In Progress", "Completed", "Overdue");
        filterChoice.setValue("All");
        filterChoice.setOnAction(e -> applyFilter());

        searchField = new TextField();
        searchField.setPromptText("Search title, description, or subject…");
        searchField.setPrefWidth(300);
        // textProperty().addListener fires on every keystroke — instant filtering
        searchField.textProperty().addListener((obs, oldVal, newVal) -> applyFilter());

        Label filterLabel = new Label("Show:");
        HBox filterBar = new HBox(10, filterLabel, filterChoice, searchField);
        filterBar.setAlignment(Pos.CENTER_LEFT);
        // ---------- Action buttons ----------
        Button addButton    = new Button("Add Task");
        addButton.getStyleClass().add("button-primary");
        Button editButton   = new Button("Edit");
        Button deleteButton = new Button("Delete");
        deleteButton.getStyleClass().add("button-danger");
        addButton.setOnAction(e -> handleAdd());
        editButton.setOnAction(e -> handleEdit());
        deleteButton.setOnAction(e -> handleDelete());

        HBox actionBar = new HBox(10, addButton, editButton, deleteButton);
        actionBar.setAlignment(Pos.CENTER_LEFT);

        // ---------- Table ----------
        table = buildTable();
        filteredTasks = new FilteredList<>(tasks, t -> true);
        table.setItems(filteredTasks);
        table.setPlaceholder(new Label("No tasks to show. Click 'Add Task' to create one."));

        // Disable Edit/Delete unless a row is selected
        editButton.disableProperty().bind(
                Bindings.isNull(table.getSelectionModel().selectedItemProperty()));
        deleteButton.disableProperty().bind(
                Bindings.isNull(table.getSelectionModel().selectedItemProperty()));

        // ---------- Top bar ----------
        VBox topBar = new VBox(10, title, filterBar, actionBar);
        topBar.setPadding(new Insets(15));

        BorderPane root = new BorderPane();
        root.setTop(topBar);
        root.setCenter(table);
        BorderPane.setMargin(table, new Insets(0, 15, 15, 15));

        // Initial data load (subjects FIRST so the table column can resolve names)
        refreshSubjects();
        refreshTasks();
        root.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) attachShortcuts(newScene);
            if (oldScene != null) detachShortcuts(oldScene);
        });
        return root;
    }

    private TableView<Task> buildTable() {
        TableView<Task> t = new TableView<>();

        TableColumn<Task, String> titleCol = new TableColumn<>("Title");
        titleCol.setCellValueFactory(cd ->
                new javafx.beans.property.SimpleStringProperty(cd.getValue().getTitle()));
        /*cd is a CellDataFeatures<Task, String> — JavaFX hands us each row's value, we return what to show. */
        titleCol.setPrefWidth(180);

        TableColumn<Task, String> subjectCol = new TableColumn<>("Subject");
        subjectCol.setCellValueFactory(cd -> {
            String name = subjectNameById.getOrDefault(cd.getValue().getSubjectId(), "(unknown)");
            return new javafx.beans.property.SimpleStringProperty(name);
        });
        subjectCol.setPrefWidth(150);

        TableColumn<Task, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(cd ->
                new javafx.beans.property.SimpleStringProperty(cd.getValue().getTaskType().getDisplayName()));
        typeCol.setPrefWidth(110);

        TableColumn<Task, LocalDate> dueCol = new TableColumn<>("Due Date");
        dueCol.setCellValueFactory(cd ->
                new javafx.beans.property.SimpleObjectProperty<>(cd.getValue().getDueDate()));
        dueCol.setPrefWidth(110);

        TableColumn<Task, String> priorityCol = new TableColumn<>("Priority");
        priorityCol.setCellValueFactory(cd ->
                new javafx.beans.property.SimpleStringProperty(cd.getValue().getPriority().getDisplayName()));
        priorityCol.setPrefWidth(90);

        TableColumn<Task, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(cd ->
                new javafx.beans.property.SimpleStringProperty(cd.getValue().getStatus().getDisplayName()));
        statusCol.setPrefWidth(110);

        t.getColumns().add(titleCol);
        t.getColumns().add(subjectCol);
        t.getColumns().add(typeCol);
        t.getColumns().add(dueCol);
        t.getColumns().add(priorityCol);
        t.getColumns().add(statusCol);

        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        // ---------- Row factory: color by status / overdue ----------
        // The row factory creates a custom TableRow that re-styles itself
        // whenever its bound item changes. This is how we get red overdue
        // rows and dimmed completed rows.
        t.setRowFactory(tv -> new TableRow<Task>() {
            @Override
            protected void updateItem(Task task, boolean empty) {
                super.updateItem(task, empty);
                if (empty || task == null) {
                    setStyle("");
                } else if (task.isOverdue()) {
                    // Soft red background for overdue
                    setStyle("-fx-background-color: #fef2f2;");
                } else if (task.getStatus() == TaskStatus.COMPLETED) {
                    // Faint text for completed (visually 'crossed off')
                    setStyle("-fx-text-fill: #6b7280; -fx-background-color: #f3f4f6;");
                } else {
                    setStyle("");
                }
            }
        });

        return t;
    }

    // DATA LOADING

    private void refreshSubjects() {
        try {
            allSubjects = subjectService.getAllSubjectsForCurrentStudent();
            subjectNameById.clear();
            for (Subject s : allSubjects) {
                subjectNameById.put(s.getSubjectId(), s.getSubjectName());
            }
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Could not load subjects:\n" + e.getMessage());
        }
    }

    private void refreshTasks() {
        try {
            tasks.setAll(taskService.getAllTasksForCurrentStudent());
            // Force the table to re-render rows so the row factory recomputes styling
            table.refresh();
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Could not load tasks:\n" + e.getMessage());
        }
    }

    // FILTERING

    private void applyFilter() {
        // 1. Status predicate from the dropdown
        String choice = filterChoice.getValue();
        Predicate<Task> statusPredicate = switch (choice) {
            case "Pending"     -> t -> t.getStatus() == TaskStatus.PENDING;
            case "In Progress" -> t -> t.getStatus() == TaskStatus.IN_PROGRESS;
            case "Completed"   -> t -> t.getStatus() == TaskStatus.COMPLETED;
            case "Overdue"     -> Task::isOverdue;
            default            -> t -> true;
        };

        // 2. Search predicate from the text field — case-insensitive across
        //    title, description, and subject name. Empty search matches everything.
        String query = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase();
        Predicate<Task> searchPredicate;
        if (query.isEmpty()) {
            searchPredicate = t -> true;
        } else {
            searchPredicate = t -> {
                // Title — always present
                if (t.getTitle() != null && t.getTitle().toLowerCase().contains(query)) return true;
                // Description — may be null
                if (t.getDescription() != null && t.getDescription().toLowerCase().contains(query)) return true;
                // Subject name — looked up via the same cache the table uses
                String subjectName = subjectNameById.get(t.getSubjectId());
                if (subjectName != null && subjectName.toLowerCase().contains(query)) return true;
                return false;
            };
        }

        // 3. Combine — both must match
        filteredTasks.setPredicate(statusPredicate.and(searchPredicate));
    }
    // ACTION HANDLERS

    private void handleAdd() {
        if (allSubjects.isEmpty()) {
            showAlert("You need to add a subject first before creating tasks.");
            return;
        }

        Optional<TaskFormResult> result = showTaskDialog("Add Task", null);
        if (result.isEmpty()) return;

        TaskFormResult r = result.get();
        try {
            Task created = taskService.addTask(
                    r.subjectId, r.title, r.description, r.taskType,
                    r.dueDate, r.priority, r.status, r.weightage);
            tasks.add(created);
            table.getSelectionModel().select(created);
        } catch (ValidationException | NotFoundException e) {
            showAlert(e.getMessage());
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Could not add task:\n" + e.getMessage());
        }
    }

    private void handleEdit() {
        Task selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        Optional<TaskFormResult> result = showTaskDialog("Edit Task", selected);
        if (result.isEmpty()) return;

        TaskFormResult r = result.get();
        try {
            Task updated = taskService.updateTask(
                    selected.getTaskId(), r.subjectId, r.title, r.description,
                    r.taskType, r.dueDate, r.priority, r.status, r.weightage);
            int index = tasks.indexOf(selected);
            if (index >= 0) {
                tasks.set(index, updated);
                table.getSelectionModel().select(updated);
            }
        } catch (ValidationException | NotFoundException e) {
            showAlert(e.getMessage());
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Could not update task:\n" + e.getMessage());
        }
    }

    private void handleDelete() {
        Task selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete task \"" + selected.getTitle() + "\"?",
                ButtonType.OK, ButtonType.CANCEL);
        confirm.setHeaderText(null);
        confirm.setTitle("Confirm Delete");

        Optional<ButtonType> choice = confirm.showAndWait();
        if (choice.isEmpty() || choice.get() != ButtonType.OK) return;

        try {
            taskService.deleteTask(selected.getTaskId());
            tasks.remove(selected);
        } catch (NotFoundException e) {
            showAlert(e.getMessage());
            refreshTasks();
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Could not delete task:\n" + e.getMessage());
        }
    }

    // SHARED FORM DIALOG (used by Add and Edit)

    private Optional<TaskFormResult> showTaskDialog(String title, Task existing) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(null);
        dialog.initStyle(javafx.stage.StageStyle.UTILITY);   // cleaner, smaller title bar

        // Apply our stylesheet to the dialog
        dialog.getDialogPane().getStylesheets().add(
                getClass().getResource("/com/studentplanner/ui/styles.css").toExternalForm());
        dialog.getDialogPane().getStyleClass().add("dialog-card");

        // ---------- Subject dropdown ----------
        ComboBox<Subject> subjectCombo = new ComboBox<>();
        subjectCombo.getItems().addAll(allSubjects);
        subjectCombo.setPromptText("Select a subject");
        // Subject's toString() returns "Name (X cr)".

        // ---------- Other fields ----------
        TextField titleField = new TextField();
        titleField.setPromptText("e.g. Calculus midterm prep");

        TextArea descField = new TextArea();
        descField.setPromptText("Optional details");
        descField.setPrefRowCount(3);
        descField.setWrapText(true);

        ChoiceBox<TaskType> typeChoice = new ChoiceBox<>();
        typeChoice.getItems().addAll(TaskType.values());

        DatePicker datePicker = new DatePicker();
        datePicker.setPromptText("Pick a date");

        ChoiceBox<Priority> priorityChoice = new ChoiceBox<>();
        priorityChoice.getItems().addAll(Priority.values());

        ChoiceBox<TaskStatus> statusChoice = new ChoiceBox<>();
        statusChoice.getItems().addAll(TaskStatus.values());

        TextField weightageField = new TextField();
        weightageField.setPromptText("Optional, 0–100");

        // ---------- Pre-fill values ----------
        if (existing != null) {
            // Find and select the existing subject in the combo
            for (Subject s : allSubjects) {
                if (s.getSubjectId() == existing.getSubjectId()) {
                    subjectCombo.setValue(s);
                    break;
                }
            }
            titleField.setText(existing.getTitle());
            descField.setText(existing.getDescription() == null ? "" : existing.getDescription());
            typeChoice.setValue(existing.getTaskType());
            datePicker.setValue(existing.getDueDate());
            priorityChoice.setValue(existing.getPriority());
            statusChoice.setValue(existing.getStatus());
            weightageField.setText(existing.getMarksWeightage() == null
                    ? "" : existing.getMarksWeightage().toPlainString());
        } else {
            // Sensible defaults for "Add"
            typeChoice.setValue(TaskType.ASSIGNMENT);
            datePicker.setValue(LocalDate.now().plusDays(7));
            priorityChoice.setValue(Priority.MEDIUM);
            statusChoice.setValue(TaskStatus.PENDING);
        }

        // ---------- Layout ----------
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        int row = 0;
        grid.add(new Label("Subject:"),     0, row); grid.add(subjectCombo,    1, row++);
        grid.add(new Label("Title:"),       0, row); grid.add(titleField,      1, row++);
        grid.add(new Label("Description:"), 0, row); grid.add(descField,       1, row++);
        grid.add(new Label("Type:"),        0, row); grid.add(typeChoice,      1, row++);
        grid.add(new Label("Due date:"),    0, row); grid.add(datePicker,      1, row++);
        grid.add(new Label("Priority:"),    0, row); grid.add(priorityChoice,  1, row++);
        grid.add(new Label("Status:"),      0, row); grid.add(statusChoice,    1, row++);
        grid.add(new Label("Weightage %:"), 0, row); grid.add(weightageField,  1, row++);

        // Make the input column expand to a usable width
        titleField.setPrefWidth(280);
        descField.setPrefWidth(280);
        subjectCombo.setPrefWidth(280);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Optional<ButtonType> clicked = dialog.showAndWait();
        if (clicked.isEmpty() || clicked.get() != ButtonType.OK) {
            return Optional.empty();
        }

        // ---------- Read values ----------
        Subject pickedSubject = subjectCombo.getValue();
        if (pickedSubject == null) {
            showAlert("Please choose a subject.");
            return Optional.empty();
        }

        // Parse weightage. Empty -> null. Non-numeric -> friendly error.
        BigDecimal weightage = null;
        String weightageText = weightageField.getText() == null ? "" : weightageField.getText().trim();
        if (!weightageText.isEmpty()) {
            try {
                weightage = new BigDecimal(weightageText);
            } catch (NumberFormatException ex) {
                showAlert("Weightage must be a number.");
                return Optional.empty();
            }
        }

        return Optional.of(new TaskFormResult(
                pickedSubject.getSubjectId(),
                titleField.getText(),
                descField.getText(),
                typeChoice.getValue(),
                datePicker.getValue(),
                priorityChoice.getValue(),
                statusChoice.getValue(),
                weightage));
    }

    /* Bundle of values returned by the form dialog. */
    private static class TaskFormResult {
        final int subjectId;
        final String title;
        final String description;
        final TaskType taskType;
        final LocalDate dueDate;
        final Priority priority;
        final TaskStatus status;
        final BigDecimal weightage;

        TaskFormResult(int subjectId, String title, String description, TaskType taskType,
                       LocalDate dueDate, Priority priority, TaskStatus status,
                       BigDecimal weightage) {
            this.subjectId = subjectId;
            this.title = title;
            this.description = description;
            this.taskType = taskType;
            this.dueDate = dueDate;
            this.priority = priority;
            this.status = status;
            this.weightage = weightage;
        }
    }

    // ALERT HELPER

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Notice");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    private void attachShortcuts(Scene scene) {
        scene.getAccelerators().put(
                KeyCombination.keyCombination("Shortcut+N"),
                this::handleAdd);
        scene.getAccelerators().put(
                KeyCombination.keyCombination("Shortcut+F"),
                () -> searchField.requestFocus());
        scene.addEventFilter(KeyEvent.KEY_PRESSED, this::handleSceneKey);
    }

    private void detachShortcuts(Scene scene) {
        scene.getAccelerators().remove(KeyCombination.keyCombination("Shortcut+N"));
        scene.getAccelerators().remove(KeyCombination.keyCombination("Shortcut+F"));
        scene.removeEventFilter(KeyEvent.KEY_PRESSED, this::handleSceneKey);
    }

    private void handleSceneKey(KeyEvent ev) {
        if (ev.getTarget() instanceof javafx.scene.control.TextInputControl) return;
        if (ev.getCode() == KeyCode.DELETE) {
            Task selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                handleDelete();
                ev.consume();
            }
        }
    }
}