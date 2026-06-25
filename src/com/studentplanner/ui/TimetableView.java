package com.studentplanner.ui;

import com.studentplanner.model.ActivityType;
import com.studentplanner.model.Subject;
import com.studentplanner.model.TimetableSlot;
import com.studentplanner.service.SubjectService;
import com.studentplanner.service.TimetableService;
import com.studentplanner.service.exception.NotFoundException;
import com.studentplanner.service.exception.ValidationException;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.Scene;
import javafx.stage.StageStyle;
/*
 The Timetable screen.
 Shows the current student's recurring weekly schedule in a sortable table.
 Add/Edit/Delete via dialog. Subject column is optional ("Free Study" rows).
 Conflict detection: when adding/editing, the service checks for overlapping
 slots; if found, the user is asked to confirm before proceeding.
 */
public class TimetableView {

    private final TimetableService timetableService;
    private final SubjectService subjectService;

    private final ObservableList<TimetableSlot> slots = FXCollections.observableArrayList();
    private final Map<Integer, String> subjectNameById = new HashMap<>();
    private List<Subject> allSubjects = List.of();

    private TableView<TimetableSlot> table;

    /* Sentinel item shown in the subject ComboBox to mean "no subject". */
    private static final Subject NO_SUBJECT_SENTINEL = createSentinel();

    private static Subject createSentinel() {
        Subject s = new Subject();
        s.setSubjectId(-1);
        s.setSubjectName("(No subject — Free Study)");
        return s;
    }

    public TimetableView(TimetableService timetableService, SubjectService subjectService) {
        this.timetableService = timetableService;
        this.subjectService = subjectService;
    }

    // VIEW BUILDING
    public Parent getView() {
        Label title = new Label("My Weekly Timetable");
        title.getStyleClass().add("h1");

        Button addButton    = new Button("Add Slot");
        addButton.getStyleClass().add("button-primary");
        Button editButton   = new Button("Edit");
        Button deleteButton = new Button("Delete");
        deleteButton.getStyleClass().add("button-danger");
        addButton.setOnAction(e -> handleAdd());
        editButton.setOnAction(e -> handleEdit());
        deleteButton.setOnAction(e -> handleDelete());

        HBox actionBar = new HBox(10, addButton, editButton, deleteButton);
        actionBar.setAlignment(Pos.CENTER_LEFT);

        table = buildTable();
        table.setItems(slots);
        table.setPlaceholder(new Label(
                "No timetable slots yet. Click 'Add Slot' to create one."));

        editButton.disableProperty().bind(
                Bindings.isNull(table.getSelectionModel().selectedItemProperty()));
        deleteButton.disableProperty().bind(
                Bindings.isNull(table.getSelectionModel().selectedItemProperty()));

        VBox topBar = new VBox(10, title, actionBar);
        topBar.setPadding(new Insets(15));

        BorderPane root = new BorderPane();
        root.setTop(topBar);
        root.setCenter(table);
        BorderPane.setMargin(table, new Insets(0, 15, 15, 15));

        refreshSubjects();
        refreshSlots();
        root.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) attachShortcuts(newScene);
            if (oldScene != null) detachShortcuts(oldScene);
        });
        return root;
    }

    private TableView<TimetableSlot> buildTable() {
        TableView<TimetableSlot> t = new TableView<>();

        TableColumn<TimetableSlot, String> dayCol = new TableColumn<>("Day");
        dayCol.setCellValueFactory(cd ->
                new SimpleStringProperty(formatDay(cd.getValue().getDayOfWeek())));
        dayCol.setPrefWidth(110);

        TableColumn<TimetableSlot, String> startCol = new TableColumn<>("Start");
        startCol.setCellValueFactory(cd ->
                new SimpleStringProperty(cd.getValue().getStartTime().toString()));
        startCol.setPrefWidth(80);

        TableColumn<TimetableSlot, String> endCol = new TableColumn<>("End");
        endCol.setCellValueFactory(cd ->
                new SimpleStringProperty(cd.getValue().getEndTime().toString()));
        endCol.setPrefWidth(80);

        TableColumn<TimetableSlot, String> subjectCol = new TableColumn<>("Subject");
        subjectCol.setCellValueFactory(cd -> {
            Integer sid = cd.getValue().getSubjectId();
            String name = (sid == null) ? "—" : subjectNameById.getOrDefault(sid, "(unknown)");
            return new SimpleStringProperty(name);
        });
        subjectCol.setPrefWidth(180);

        TableColumn<TimetableSlot, String> activityCol = new TableColumn<>("Activity");
        activityCol.setCellValueFactory(cd ->
                new SimpleStringProperty(cd.getValue().getActivityType().getDisplayName()));
        activityCol.setPrefWidth(120);

        TableColumn<TimetableSlot, Integer> durationCol = new TableColumn<>("Duration (min)");
        durationCol.setCellValueFactory(cd ->
                new SimpleObjectProperty<>(cd.getValue().getDurationMinutes()));
        durationCol.setPrefWidth(110);

        t.getColumns().add(dayCol);
        t.getColumns().add(startCol);
        t.getColumns().add(endCol);
        t.getColumns().add(subjectCol);
        t.getColumns().add(activityCol);
        t.getColumns().add(durationCol);

        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        return t;
    }

    /* "Mon", "Tue", etc. instead of Java's default uppercase enum names. */
    private String formatDay(DayOfWeek d) {
        String name = d.toString();   // "MONDAY"
        return name.charAt(0) + name.substring(1, 3).toLowerCase();   // "Mon"
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

    private void refreshSlots() {
        try {
            slots.setAll(timetableService.getAllSlotsForCurrentStudent());
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Could not load timetable:\n" + e.getMessage());
        }
    }


    // ACTION HANDLERS
    private void handleAdd() {
        Optional<SlotFormResult> result = showSlotDialog("Add Slot", null);
        if (result.isEmpty()) return;

        SlotFormResult r = result.get();
        if (!confirmIfConflict(r.dayOfWeek, r.startTime, r.endTime, null)) return;

        try {
            TimetableSlot created = timetableService.addSlot(
                    r.subjectId, r.dayOfWeek, r.startTime, r.endTime, r.activityType);
            slots.add(created);
            refreshSlots();   // re-sort so new row lands in proper position
            table.getSelectionModel().select(created);
        } catch (ValidationException | NotFoundException e) {
            showAlert(e.getMessage());
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Could not add slot:\n" + e.getMessage());
        }
    }

    private void handleEdit() {
        TimetableSlot selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        Optional<SlotFormResult> result = showSlotDialog("Edit Slot", selected);
        if (result.isEmpty()) return;

        SlotFormResult r = result.get();
        if (!confirmIfConflict(r.dayOfWeek, r.startTime, r.endTime, selected.getSlotId())) return;

        try {
            TimetableSlot updated = timetableService.updateSlot(
                    selected.getSlotId(), r.subjectId, r.dayOfWeek,
                    r.startTime, r.endTime, r.activityType);
            int index = slots.indexOf(selected);
            if (index >= 0) {
                slots.set(index, updated);
            }
            refreshSlots();
        } catch (ValidationException | NotFoundException e) {
            showAlert(e.getMessage());
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Could not update slot:\n" + e.getMessage());
        }
    }

    private void handleDelete() {
        TimetableSlot selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete this slot?\n\n" + selected.toString(),
                ButtonType.OK, ButtonType.CANCEL);
        confirm.setHeaderText(null);
        confirm.setTitle("Confirm Delete");

        Optional<ButtonType> choice = confirm.showAndWait();
        if (choice.isEmpty() || choice.get() != ButtonType.OK) return;

        try {
            timetableService.deleteSlot(selected.getSlotId());
            slots.remove(selected);
        } catch (NotFoundException e) {
            showAlert(e.getMessage());
            refreshSlots();
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Could not delete slot:\n" + e.getMessage());
        }
    }

    /*
     Checks for time conflicts and asks the user to confirm if any.
     Returns true if the operation should proceed, false to abort.
     */
    private boolean confirmIfConflict(DayOfWeek day, LocalTime start, LocalTime end,
                                      Integer ignoreSlotId) {
        try {
            Optional<TimetableSlot> conflict =
                    timetableService.detectConflict(day, start, end, ignoreSlotId);
            if (conflict.isEmpty()) return true;

            TimetableSlot c = conflict.get();
            Alert warn = new Alert(Alert.AlertType.CONFIRMATION,
                    "This slot overlaps with an existing one:\n\n" +
                            formatDay(c.getDayOfWeek()) + " " + c.getStartTime() +
                            "–" + c.getEndTime() + " " + c.getActivityType() +
                            "\n\nAdd anyway?",
                    ButtonType.OK, ButtonType.CANCEL);
            warn.setHeaderText(null);
            warn.setTitle("Schedule Conflict");
            Optional<ButtonType> choice = warn.showAndWait();
            return choice.isPresent() && choice.get() == ButtonType.OK;
        } catch (SQLException e) {
            e.printStackTrace();
            // If we can't even check, let the operation proceed and let the
            // service-layer call surface any database error.
            return true;
        }
    }

    // SHARED FORM DIALOG
    private Optional<SlotFormResult> showSlotDialog(String title, TimetableSlot existing) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(null);
        dialog.initStyle(javafx.stage.StageStyle.UTILITY);   // cleaner, smaller title bar

        // Apply our stylesheet to the dialog
        dialog.getDialogPane().getStylesheets().add(
                getClass().getResource("/com/studentplanner/ui/styles.css").toExternalForm());
        dialog.getDialogPane().getStyleClass().add("dialog-card");
        // ---------- Subject (with sentinel for "no subject") ----------
        ComboBox<Subject> subjectCombo = new ComboBox<>();
        subjectCombo.getItems().add(NO_SUBJECT_SENTINEL);
        subjectCombo.getItems().addAll(allSubjects);
        subjectCombo.setValue(NO_SUBJECT_SENTINEL);

        // ---------- Day ----------
        ChoiceBox<DayOfWeek> dayChoice = new ChoiceBox<>();
        dayChoice.getItems().addAll(DayOfWeek.values());

        // ---------- Time pickers (hour + minute spinners) ----------
        Spinner<Integer> startHour = makeHourSpinner();
        Spinner<Integer> startMin  = makeMinuteSpinner();
        Spinner<Integer> endHour   = makeHourSpinner();
        Spinner<Integer> endMin    = makeMinuteSpinner();

        HBox startBox = new HBox(5, startHour, new Label(":"), startMin);
        startBox.setAlignment(Pos.CENTER_LEFT);
        HBox endBox = new HBox(5, endHour, new Label(":"), endMin);
        endBox.setAlignment(Pos.CENTER_LEFT);

        // ---------- Activity ----------
        ChoiceBox<ActivityType> activityChoice = new ChoiceBox<>();
        activityChoice.getItems().addAll(ActivityType.values());

        // ---------- Pre-fill ----------
        if (existing != null) {
            // subject: find by id, fall back to sentinel
            Subject sub = NO_SUBJECT_SENTINEL;
            if (existing.getSubjectId() != null) {
                for (Subject s : allSubjects) {
                    if (s.getSubjectId() == existing.getSubjectId()) { sub = s; break; }
                }
            }
            subjectCombo.setValue(sub);
            dayChoice.setValue(existing.getDayOfWeek());
            startHour.getValueFactory().setValue(existing.getStartTime().getHour());
            startMin.getValueFactory().setValue(roundTo15(existing.getStartTime().getMinute()));
            endHour.getValueFactory().setValue(existing.getEndTime().getHour());
            endMin.getValueFactory().setValue(roundTo15(existing.getEndTime().getMinute()));
            activityChoice.setValue(existing.getActivityType());
        } else {
            dayChoice.setValue(DayOfWeek.MONDAY);
            startHour.getValueFactory().setValue(9);
            startMin.getValueFactory().setValue(0);
            endHour.getValueFactory().setValue(10);
            endMin.getValueFactory().setValue(30);
            activityChoice.setValue(ActivityType.LECTURE);
        }

        // ---------- Layout ----------
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        int row = 0;
        grid.add(new Label("Subject:"),  0, row); grid.add(subjectCombo,   1, row++);
        grid.add(new Label("Day:"),      0, row); grid.add(dayChoice,      1, row++);
        grid.add(new Label("Start:"),    0, row); grid.add(startBox,       1, row++);
        grid.add(new Label("End:"),      0, row); grid.add(endBox,         1, row++);
        grid.add(new Label("Activity:"), 0, row); grid.add(activityChoice, 1, row++);

        subjectCombo.setPrefWidth(260);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Optional<ButtonType> clicked = dialog.showAndWait();
        if (clicked.isEmpty() || clicked.get() != ButtonType.OK) {
            return Optional.empty();
        }

        // ---------- Read values ----------
        Subject pickedSubject = subjectCombo.getValue();
        Integer subjectId = (pickedSubject == NO_SUBJECT_SENTINEL || pickedSubject == null)
                ? null : pickedSubject.getSubjectId();

        return Optional.of(new SlotFormResult(
                subjectId,
                dayChoice.getValue(),
                LocalTime.of(startHour.getValue(), startMin.getValue()),
                LocalTime.of(endHour.getValue(), endMin.getValue()),
                activityChoice.getValue()));
    }

    private Spinner<Integer> makeHourSpinner() {
        Spinner<Integer> s = new Spinner<>();
        s.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 23, 9));
        s.setEditable(true);
        s.setPrefWidth(80);
        return s;
    }

    private Spinner<Integer> makeMinuteSpinner() {
        // Cycle through 0/15/30/45 in 15-minute steps
        Spinner<Integer> s = new Spinner<>();
        SpinnerValueFactory.IntegerSpinnerValueFactory f =
                new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 45, 0, 15);
        s.setValueFactory(f);
        s.setEditable(true);
        s.setPrefWidth(80);
        return s;
    }

    /* Snap a minute value to the nearest valid spinner step (0/15/30/45). */
    private int roundTo15(int minute) {
        return Math.min(45, (minute / 15) * 15);
    }

    private static class SlotFormResult {
        final Integer subjectId;        // null = no subject
        final DayOfWeek dayOfWeek;
        final LocalTime startTime;
        final LocalTime endTime;
        final ActivityType activityType;

        SlotFormResult(Integer subjectId, DayOfWeek dayOfWeek, LocalTime startTime,
                       LocalTime endTime, ActivityType activityType) {
            this.subjectId = subjectId;
            this.dayOfWeek = dayOfWeek;
            this.startTime = startTime;
            this.endTime = endTime;
            this.activityType = activityType;
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
        scene.addEventFilter(KeyEvent.KEY_PRESSED, this::handleSceneKey);
    }

    private void detachShortcuts(Scene scene) {
        scene.getAccelerators().remove(KeyCombination.keyCombination("Shortcut+N"));
        scene.removeEventFilter(KeyEvent.KEY_PRESSED, this::handleSceneKey);
    }

    private void handleSceneKey(KeyEvent ev) {
        if (ev.getTarget() instanceof javafx.scene.control.TextInputControl) return;
        if (ev.getCode() == KeyCode.DELETE) {
            TimetableSlot selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                handleDelete();
                ev.consume();
            }
        }
    }
}