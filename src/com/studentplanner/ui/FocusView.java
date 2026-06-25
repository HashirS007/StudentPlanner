package com.studentplanner.ui;

import com.studentplanner.model.StudySession;
import com.studentplanner.model.Subject;
import com.studentplanner.model.Task;
import com.studentplanner.service.StudySessionService;
import com.studentplanner.service.SubjectService;
import com.studentplanner.service.TaskService;
import com.studentplanner.service.exception.NotFoundException;
import com.studentplanner.service.exception.ValidationException;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 The Focus (Pomodoro) screen.
 Provides a countdown timer with Start / Pause / Resume / Stop controls.
 On completion (or manual Stop with sufficient elapsed time), the session
 is logged via StudySessionService.
 Architecture:
 - Modeled as a finite state machine (IDLE / RUNNING / PAUSED / FINISHED).
   Button enabled-states are derived from the current state.
 - Timer driven by JavaFX Timeline (1s KeyFrame) — safe on the UI thread.
 - Subject and task pickers are optional; sessions can be unassociated.
 - History table at the bottom shows recent sessions.
 */
public class FocusView {

    /* Finite state machine for the timer's lifecycle. */
    private enum TimerState { IDLE, RUNNING, PAUSED, FINISHED }

    /* Sentinel for "no subject" in the ComboBox — same trick as TimetableView. */
    private static final Subject NO_SUBJECT_SENTINEL = createSubjectSentinel();
    private static final Task NO_TASK_SENTINEL = createTaskSentinel();

    private static Subject createSubjectSentinel() {
        Subject s = new Subject();
        s.setSubjectId(-1);
        s.setSubjectName("(No subject)");
        return s;
    }

    private static Task createTaskSentinel() {
        Task t = new Task();
        t.setTaskId(-1);
        t.setTitle("(No specific task)");
        return t;
    }

    // ---------- Services ----------
    private final StudySessionService sessionService;
    private final SubjectService subjectService;
    private final TaskService taskService;

    // ---------- Timer state ----------
    private TimerState state = TimerState.IDLE;
    private Timeline timeline;
    private int totalSeconds;          // configured duration
    private int secondsRemaining;      // ticks down each second
    private int secondsElapsed;        // accumulates while running
    private LocalDateTime sessionStartedAt;

    // ---------- UI references kept as fields ----------
    private Label timeLabel;
    private Label statusLabel;
    private Spinner<Integer> minutesSpinner;
    private ComboBox<Subject> subjectCombo;
    private ComboBox<Task> taskCombo;
    private Button startButton;
    private Button pauseResumeButton;
    private Button stopButton;
    private Button resetButton;
    private TableView<StudySession> historyTable;

    // ---------- Data backing the dropdowns and history ----------
    private List<Subject> allSubjects = List.of();
    private List<Task> allTasks = List.of();
    private final Map<Integer, String> subjectNameById = new HashMap<>();
    private final ObservableList<StudySession> recentSessions = FXCollections.observableArrayList();

    public FocusView(StudySessionService sessionService,
                     SubjectService subjectService,
                     TaskService taskService) {
        this.sessionService = sessionService;
        this.subjectService = subjectService;
        this.taskService = taskService;
    }


    // VIEW BUILDING
    public Parent getView() {
        Label title = new Label("Focus Mode");
        title.getStyleClass().add("h1");

        Label subtitle = new Label("Pick what you're working on, set a duration, and start the timer.");
        subtitle.getStyleClass().add("subtle");

        VBox header = new VBox(4, title, subtitle);

        Region timerCard = buildTimerCard();
        Region historyCard = buildHistoryCard();

        VBox root = new VBox(20, header, timerCard, historyCard);
        root.setPadding(new Insets(20));

        // Initial data load
        refreshSubjects();
        refreshTasks();
        refreshHistory();
        updateButtonStates();

        return root;
    }

    /* The big timer card with selectors, countdown, and controls. */
    private Region buildTimerCard() {
        // ---------- Subject + task pickers ----------
        subjectCombo = new ComboBox<>();
        subjectCombo.setPromptText("Subject (optional)");
        subjectCombo.setPrefWidth(220);
        // When subject changes, refresh task list to only show tasks for that subject
        subjectCombo.valueProperty().addListener((obs, oldV, newV) -> rebuildTaskCombo());

        taskCombo = new ComboBox<>();
        taskCombo.setPromptText("Task (optional)");
        taskCombo.setPrefWidth(260);

        HBox pickerRow = new HBox(10,
                new Label("Subject:"), subjectCombo,
                new Label("Task:"), taskCombo);
        pickerRow.setAlignment(Pos.CENTER_LEFT);

        // ---------- Duration spinner ----------
        minutesSpinner = new Spinner<>(1, 120, 25, 5);   // min, max, initial, step
        minutesSpinner.setEditable(true);
        minutesSpinner.setPrefWidth(90);

        HBox durationRow = new HBox(10,
                new Label("Duration:"), minutesSpinner, new Label("minutes"));
        durationRow.setAlignment(Pos.CENTER_LEFT);

        // ---------- The big countdown display ----------
        timeLabel = new Label("25:00");
        timeLabel.setStyle("-fx-font-size: 64px; -fx-font-weight: bold; "
                + "-fx-font-family: 'Consolas', 'Menlo', monospace;");

        statusLabel = new Label("Ready when you are.");
        statusLabel.getStyleClass().add("subtle");

        VBox display = new VBox(4, timeLabel, statusLabel);
        display.setAlignment(Pos.CENTER);
        display.setPadding(new Insets(20, 0, 20, 0));

        // ---------- Control buttons ----------
        startButton = new Button("Start");
        startButton.getStyleClass().add("button-primary");
        startButton.setPrefWidth(100);
        startButton.setOnAction(e -> handleStart());

        pauseResumeButton = new Button("Pause");
        pauseResumeButton.setPrefWidth(100);
        pauseResumeButton.setOnAction(e -> handlePauseResume());

        stopButton = new Button("Stop");
        stopButton.getStyleClass().add("button-danger");
        stopButton.setPrefWidth(100);
        stopButton.setOnAction(e -> handleStop());

        resetButton = new Button("Reset");
        resetButton.setPrefWidth(100);
        resetButton.setOnAction(e -> handleReset());

        HBox controls = new HBox(10, startButton, pauseResumeButton, stopButton, resetButton);
        controls.setAlignment(Pos.CENTER);

        // ---------- Assemble ----------
        VBox card = new VBox(15, pickerRow, durationRow, display, controls);
        card.getStyleClass().add("card");
        card.setAlignment(Pos.CENTER_LEFT);
        return card;
    }

    /* The history card with the recent sessions table. */
    private Region buildHistoryCard() {
        Label heading = new Label("Recent Sessions");
        heading.getStyleClass().add("h3");

        historyTable = new TableView<>();
        historyTable.setItems(recentSessions);
        historyTable.setPlaceholder(new Label("No sessions yet — your first focused minutes will land here."));
        historyTable.setPrefHeight(220);

        TableColumn<StudySession, String> whenCol = new TableColumn<>("When");
        whenCol.setCellValueFactory(cd -> new SimpleStringProperty(
                cd.getValue().getStartedAt().format(
                        DateTimeFormatter.ofPattern("EEE, MMM d · HH:mm"))));
        whenCol.setPrefWidth(180);

        TableColumn<StudySession, String> subjectCol = new TableColumn<>("Subject");
        subjectCol.setCellValueFactory(cd -> {
            Integer sid = cd.getValue().getSubjectId();
            String name = (sid == null) ? "—" : subjectNameById.getOrDefault(sid, "(deleted)");
            return new SimpleStringProperty(name);
        });
        subjectCol.setPrefWidth(180);

        TableColumn<StudySession, String> durationCol = new TableColumn<>("Duration");
        durationCol.setCellValueFactory(cd ->
                new SimpleStringProperty(cd.getValue().getDurationDisplay()));
        durationCol.setPrefWidth(110);

        TableColumn<StudySession, String> resultCol = new TableColumn<>("Result");
        resultCol.setCellValueFactory(cd ->
                new SimpleStringProperty(cd.getValue().isCompleted() ? "✓ Completed" : "Stopped"));
        resultCol.setPrefWidth(120);

        historyTable.getColumns().add(whenCol);
        historyTable.getColumns().add(subjectCol);
        historyTable.getColumns().add(durationCol);
        historyTable.getColumns().add(resultCol);
        historyTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        VBox card = new VBox(10, heading, historyTable);
        card.getStyleClass().add("card");
        return card;
    }

    // DATA LOADING
    private void refreshSubjects() {
        try {
            allSubjects = subjectService.getAllSubjectsForCurrentStudent();
            subjectNameById.clear();
            for (Subject s : allSubjects) subjectNameById.put(s.getSubjectId(), s.getSubjectName());

            subjectCombo.getItems().clear();
            subjectCombo.getItems().add(NO_SUBJECT_SENTINEL);
            subjectCombo.getItems().addAll(allSubjects);
            subjectCombo.setValue(NO_SUBJECT_SENTINEL);
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Could not load subjects:\n" + e.getMessage());
        }
    }

    private void refreshTasks() {
        try {
            allTasks = taskService.getAllTasksForCurrentStudent();
            rebuildTaskCombo();
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Could not load tasks:\n" + e.getMessage());
        }
    }

    /*
     Rebuilds the task ComboBox to only show tasks belonging to the
     currently-selected subject. If no subject is selected (or it's the
     sentinel), shows all tasks.
     */
    private void rebuildTaskCombo() {
        taskCombo.getItems().clear();
        taskCombo.getItems().add(NO_TASK_SENTINEL);

        Subject selected = subjectCombo.getValue();
        if (selected == null || selected == NO_SUBJECT_SENTINEL) {
            taskCombo.getItems().addAll(allTasks);
        } else {
            for (Task t : allTasks) {
                if (t.getSubjectId() == selected.getSubjectId()) {
                    taskCombo.getItems().add(t);
                }
            }
        }
        taskCombo.setValue(NO_TASK_SENTINEL);
    }

    private void refreshHistory() {
        try {
            recentSessions.setAll(sessionService.getRecentSessions(50));
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Could not load session history:\n" + e.getMessage());
        }
    }

    // STATE MACHINE TRANSITIONS
    private void handleStart() {
        if (state != TimerState.IDLE && state != TimerState.FINISHED) return;

        totalSeconds = minutesSpinner.getValue() * 60;
        secondsRemaining = totalSeconds;
        secondsElapsed = 0;
        sessionStartedAt = LocalDateTime.now();

        startTimeline();

        state = TimerState.RUNNING;
        statusLabel.setText("Focusing… stay with it.");
        updateDisplay();
        updateButtonStates();
    }

    private void handlePauseResume() {
        if (state == TimerState.RUNNING) {
            timeline.pause();
            state = TimerState.PAUSED;
            statusLabel.setText("Paused. Take a breath.");
        } else if (state == TimerState.PAUSED) {
            timeline.play();
            state = TimerState.RUNNING;
            statusLabel.setText("Back at it.");
        }
        updateButtonStates();
    }

    private void handleStop() {
        if (state != TimerState.RUNNING && state != TimerState.PAUSED) return;
        if (timeline != null) timeline.stop();

        // Log the session if it ran long enough
        finalizeSession(false);

        state = TimerState.FINISHED;
        statusLabel.setText("Session ended.");
        updateButtonStates();
    }

    private void handleReset() {
        if (timeline != null) timeline.stop();
        secondsRemaining = minutesSpinner.getValue() * 60;
        totalSeconds = secondsRemaining;
        secondsElapsed = 0;
        state = TimerState.IDLE;
        statusLabel.setText("Ready when you are.");
        updateDisplay();
        updateButtonStates();
    }

    /* Called when the timer reaches 00:00. */
    private void onTimerCompleted() {
        if (timeline != null) timeline.stop();
        finalizeSession(true);
        state = TimerState.FINISHED;
        statusLabel.setText("✓ Session complete. Nice work.");

        // Friendly toast
        Alert done = new Alert(Alert.AlertType.INFORMATION,
                "Focus session completed!\n\n" + (totalSeconds / 60) + " minutes logged.");
        done.setTitle("Done");
        done.setHeaderText(null);
        done.show();   // non-blocking unlike showAndWait

        updateButtonStates();
    }

    // TIMELINE
    private void startTimeline() {
        if (timeline != null) timeline.stop();   // clean any prior instance

        timeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            // This runs on the JavaFX Application Thread once per second
            secondsRemaining--;
            secondsElapsed++;
            updateDisplay();
            if (secondsRemaining <= 0) {
                onTimerCompleted();
            }
        }));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
    }

    // SESSION LOGGING
    /*
     Records the session via the service. If the elapsed time is below
     the service's minimum, the service returns null and we skip the toast.
     */
    private void finalizeSession(boolean completed) {
        if (secondsElapsed <= 0) return;

        Subject sub = subjectCombo.getValue();
        Task tsk = taskCombo.getValue();

        Integer subjectId = (sub == null || sub == NO_SUBJECT_SENTINEL) ? null : sub.getSubjectId();
        Integer taskId    = (tsk == null || tsk == NO_TASK_SENTINEL)    ? null : tsk.getTaskId();

        try {
            StudySession recorded = sessionService.recordSession(
                    subjectId, taskId,
                    sessionStartedAt, LocalDateTime.now(),
                    secondsElapsed, completed);

            if (recorded == null) {
                // Below MIN_DURATION_SECONDS — service silently discarded it
                statusLabel.setText("Session was too short to log. Try at least 1 minute.");
            } else {
                refreshHistory();
            }
        } catch (ValidationException | NotFoundException e) {
            showAlert(e.getMessage());
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Could not save the session:\n" + e.getMessage());
        }
    }

    // UI UPDATES
    /* Updates the big "MM:SS" label from secondsRemaining. */
    private void updateDisplay() {
        int minutes = secondsRemaining / 60;
        int seconds = secondsRemaining % 60;
        timeLabel.setText(String.format("%02d:%02d", minutes, seconds));

        // Color the display red when in the final 60 seconds — visual urgency
        if (state == TimerState.RUNNING && secondsRemaining <= 60 && secondsRemaining > 0) {
            timeLabel.setStyle(timeLabel.getStyle() + "-fx-text-fill: #ef4444;");
        } else if (state == TimerState.FINISHED) {
            timeLabel.setStyle(timeLabel.getStyle() + "-fx-text-fill: #10b981;");
        } else {
            // Reset to default color
            String base = timeLabel.getStyle().replaceAll("-fx-text-fill: [^;]+;", "");
            timeLabel.setStyle(base);
        }
    }

    /* Single source of truth for which buttons are enabled in each state. */
    private void updateButtonStates() {
        switch (state) {
            case IDLE -> {
                startButton.setDisable(false);
                pauseResumeButton.setDisable(true);
                pauseResumeButton.setText("Pause");
                stopButton.setDisable(true);
                resetButton.setDisable(true);
                minutesSpinner.setDisable(false);
                subjectCombo.setDisable(false);
                taskCombo.setDisable(false);
            }
            case RUNNING -> {
                startButton.setDisable(true);
                pauseResumeButton.setDisable(false);
                pauseResumeButton.setText("Pause");
                stopButton.setDisable(false);
                resetButton.setDisable(true);
                minutesSpinner.setDisable(true);
                subjectCombo.setDisable(true);
                taskCombo.setDisable(true);
            }
            case PAUSED -> {
                startButton.setDisable(true);
                pauseResumeButton.setDisable(false);
                pauseResumeButton.setText("Resume");
                stopButton.setDisable(false);
                resetButton.setDisable(true);
            }
            case FINISHED -> {
                startButton.setDisable(false);
                pauseResumeButton.setDisable(true);
                pauseResumeButton.setText("Pause");
                stopButton.setDisable(true);
                resetButton.setDisable(false);
                minutesSpinner.setDisable(false);
                subjectCombo.setDisable(false);
                taskCombo.setDisable(false);
            }
        }
    }

    // CLEANUP
    /*
     Called by MainApp when the user navigates away from this screen.
     Stops the timeline so it doesn't keep ticking in the background.
     Without this, leaving the screen mid-session would orphan the timer.
     */
    public void cleanup() {
        if (timeline != null) {
            timeline.stop();
        }
        // If a session was running, log what we have so the user doesn't lose it
        if (state == TimerState.RUNNING || state == TimerState.PAUSED) {
            finalizeSession(false);
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
}