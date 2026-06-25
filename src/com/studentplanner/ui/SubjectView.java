package com.studentplanner.ui;

import com.studentplanner.model.Subject;
import com.studentplanner.service.SubjectService;
import com.studentplanner.service.exception.NotFoundException;
import com.studentplanner.service.exception.ValidationException;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.StageStyle;
import java.sql.SQLException;
import java.util.Optional;

import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.Scene;

/*
 The Subjects screen.
 Shows the current student's subjects in a table and provides Add / Edit /
 Delete actions via dialogs.
 CurrentUser data is only handled by the service layer
 The TableView's data source is an ObservableList, which automatically
 re-renders the table whenever items are added/removed/changed
 */
public class SubjectView {

    private final SubjectService subjectService;

    // The list backing the TableView. Adding/removing items here updates the UI.
    private final ObservableList<Subject> subjects = FXCollections.observableArrayList();

    private TableView<Subject> table;

    public SubjectView(SubjectService subjectService) {
        this.subjectService = subjectService;
    }

    // Builds and returns the root node for this screen.
    public Parent getView() {
        Label title = new Label("My Subjects");
        title.getStyleClass().add("h1");

        // ---------- Action buttons ----------
        Button addButton    = new Button("Add Subject");
        addButton.getStyleClass().add("button-primary");
        Button editButton   = new Button("Edit");
        Button deleteButton = new Button("Delete");
        deleteButton.getStyleClass().add("button-danger");

        addButton.setOnAction(e -> handleAdd());
        editButton.setOnAction(e -> handleEdit());
        deleteButton.setOnAction(e -> handleDelete());

        // Disable Edit/Delete unless a row is selected.
        // Bindings.isNull(...) returns a boolean property that is true when the
        // selected item is null. disableProperty().bind(...) ties the button's
        // enabled state to that boolean — automatic, no manual updates needed.
        ReadOnlyObjectProperty<Subject> selectedItemProperty;
        // (We need the table created before we can wire the bindings, so we
        //  build the table first, then come back and bind.)

        HBox buttonBar = new HBox(10, addButton, editButton, deleteButton);
        buttonBar.setAlignment(Pos.CENTER_LEFT);

        // ---------- The table ----------
        table = buildTable();
        table.setItems(subjects);
        table.setPlaceholder(new Label("No subjects yet. Click 'Add Subject' to create your first one."));

        // Now bind the buttons to selection
        selectedItemProperty = table.getSelectionModel().selectedItemProperty(); //eturns the currently-selected row (or null if nothing is selected). Edit/Delete buttons need to check this.
        editButton.disableProperty().bind(Bindings.isNull(selectedItemProperty));
        deleteButton.disableProperty().bind(Bindings.isNull(selectedItemProperty));

        // ---------- Assemble ----------
        VBox topBar = new VBox(10, title, buttonBar);
        topBar.setPadding(new Insets(15));

        BorderPane root = new BorderPane();
        root.setTop(topBar);
        root.setCenter(table);
        BorderPane.setMargin(table, new Insets(0, 15, 15, 15));

        // Load the data into the list. Errors here are catastrophic — show alert.
        refreshSubjects();
        // Register shortcuts after the view is attached to a scene
        root.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) attachShortcuts(newScene);
            if (oldScene != null) detachShortcuts(oldScene);
        });
        return root;
    }

    //Constructs the TableView with its three columns.
    private TableView<Subject> buildTable() {
        TableView<Subject> t = new TableView<>();

        TableColumn<Subject, String> nameCol = new TableColumn<>("Subject Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("subjectName"));
        nameCol.setPrefWidth(220);

        TableColumn<Subject, String> instructorCol = new TableColumn<>("Instructor");
        instructorCol.setCellValueFactory(new PropertyValueFactory<>("instructorName"));
        instructorCol.setPrefWidth(180);

        TableColumn<Subject, Integer> creditCol = new TableColumn<>("Credit Hours");
        creditCol.setCellValueFactory(new PropertyValueFactory<>("creditHours"));
        creditCol.setPrefWidth(100);

        t.getColumns().add(nameCol);
        t.getColumns().add(instructorCol);
        t.getColumns().add(creditCol);

        // Make columns share the available width sensibly
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        return t;
    }

    // Reloads the subjects list from the database.
    private void refreshSubjects() {
        try {
            subjects.setAll(subjectService.getAllSubjectsForCurrentStudent());
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Could not load subjects:\n" + e.getMessage());
        }
    }

    private void handleAdd() {
        Optional<SubjectFormResult> result = showSubjectDialog(
                "Add Subject", "", "", 3);
        if (result.isEmpty()) return;   // user cancelled

        try {
            Subject created = subjectService.addSubject(
                    result.get().name, result.get().instructor, result.get().creditHours);
            subjects.add(created);
            // Select the newly added row so the user sees what they created
            table.getSelectionModel().select(created);
        } catch (ValidationException e) {
            showAlert(e.getMessage());
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Could not add subject:\n" + e.getMessage());
        }
    }

    private void handleEdit() {
        Subject selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) return;   // shouldn't happen because button is disabled, but defensive

        Optional<SubjectFormResult> result = showSubjectDialog(
                "Edit Subject",
                selected.getSubjectName(),
                selected.getInstructorName() == null ? "" : selected.getInstructorName(),
                selected.getCreditHours());
        if (result.isEmpty()) return;

        try {
            Subject updated = subjectService.updateSubject(
                    selected.getSubjectId(),
                    result.get().name, result.get().instructor, result.get().creditHours);
            // Replace the row in the list so the table re-renders.
            // ObservableList.set() fires a "replaced" event the table reacts to.
            int index = subjects.indexOf(selected);
            if (index >= 0) {
                subjects.set(index, updated);
                table.getSelectionModel().select(updated);
            }
        } catch (ValidationException | NotFoundException e) {
            showAlert(e.getMessage());
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Could not update subject:\n" + e.getMessage());
        }
    }

    private void handleDelete() {
        Subject selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        // Confirmation prompt
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete subject \"" + selected.getSubjectName() + "\"?\n\n" +
                        "All tasks linked to this subject will also be deleted.",
                ButtonType.OK, ButtonType.CANCEL);
        confirm.setHeaderText(null);
        confirm.setTitle("Confirm Delete");

        Optional<ButtonType> choice = confirm.showAndWait();
        if (choice.isEmpty() || choice.get() != ButtonType.OK) return;

        try {
            subjectService.deleteSubject(selected.getSubjectId());
            subjects.remove(selected);
        } catch (NotFoundException e) {
            // The row vanished from the DB between rendering and clicking — refresh.
            showAlert(e.getMessage());
            refreshSubjects();
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Could not delete subject:\n" + e.getMessage());
        }
    }

    /*
      Shows the add/edit form, pre-filled with the given values.
      Returns Optional.of(result) if the user clicked OK, or Optional.empty()
      if they cancelled.
     */
    private Optional<SubjectFormResult> showSubjectDialog(
            String title, String prefillName, String prefillInstructor, int prefillCredits) {

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(null);
        dialog.initStyle(javafx.stage.StageStyle.UTILITY);   // cleaner, smaller title bar

        // Apply our stylesheet to the dialog
        dialog.getDialogPane().getStylesheets().add(
                getClass().getResource("/com/studentplanner/ui/styles.css").toExternalForm());
        dialog.getDialogPane().getStyleClass().add("dialog-card");

        TextField nameField = new TextField(prefillName);
        nameField.setPromptText("e.g. Object Oriented Programming");

        TextField instructorField = new TextField(prefillInstructor);
        instructorField.setPromptText("Optional");

        // Spinner is a numeric input with up/down arrows — perfect for
        // bounded integer values like credit hours. Range matches the
        // SubjectService validation rules so the UI guides the user.
        Spinner<Integer> creditsSpinner = new Spinner<>(1, 6, prefillCredits);
        creditsSpinner.setEditable(true);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        grid.add(new Label("Subject name:"),  0, 0);
        grid.add(nameField,                   1, 0);
        grid.add(new Label("Instructor:"),    0, 1);
        grid.add(instructorField,             1, 1);
        grid.add(new Label("Credit hours:"),  0, 2);
        grid.add(creditsSpinner,              1, 2);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Optional<ButtonType> clicked = dialog.showAndWait();
        if (clicked.isEmpty() || clicked.get() != ButtonType.OK) {
            return Optional.empty();
        }

        return Optional.of(new SubjectFormResult(
                nameField.getText(),
                instructorField.getText(),
                creditsSpinner.getValue()));
    }

    /*
      Tiny private record-like class to hold the three form values together,
      so the dialog method can return all of them as one object.
      Could also be a Java 'record' if your project allows it; this plain
      class works on any Java version your course supports.
     */
    private static class SubjectFormResult {
        final String name;
        final String instructor;
        final int creditHours;

        SubjectFormResult(String name, String instructor, int creditHours) {
            this.name = name;
            this.instructor = instructor;
            this.creditHours = creditHours;
        }
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Notice");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    /*
     Wires Ctrl/Cmd+N and Delete to the Add and Delete actions.
     Called when this view is attached to a scene.
     */
    private void attachShortcuts(Scene scene) {
        scene.getAccelerators().put(
                KeyCombination.keyCombination("Shortcut+N"),
                this::handleAdd);
        // Delete key is registered via key-pressed handler so it only fires
        // when the user is NOT in a text field (otherwise typing 'Delete'
        // in a textbox would fire it).
        scene.addEventFilter(KeyEvent.KEY_PRESSED, this::handleSceneKey);
    }

    private void detachShortcuts(Scene scene) {
        scene.getAccelerators().remove(KeyCombination.keyCombination("Shortcut+N"));
        scene.removeEventFilter(KeyEvent.KEY_PRESSED, this::handleSceneKey);
    }

    private void handleSceneKey(KeyEvent ev) {
        // Ignore Delete when focus is inside a text input (don't break typing!)
        if (ev.getTarget() instanceof javafx.scene.control.TextInputControl) return;
        if (ev.getCode() == KeyCode.DELETE) {
            Subject selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                handleDelete();
                ev.consume();
            }
        }
    }
}