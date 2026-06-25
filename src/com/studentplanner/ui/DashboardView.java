package com.studentplanner.ui;

import com.studentplanner.model.DashboardStats;
import com.studentplanner.model.Task;
import com.studentplanner.service.DashboardService;
import com.studentplanner.util.Session;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;

/*
 The Dashboard screen.
 Read-only home screen displaying summary statistics for the current student:
 - Greeting and current date
 - Stat cards: tasks total / pending / in-progress / completed / overdue
 - Completion progress bar
 - Subject and timetable summaries
 - Upcoming tasks (next 7 days)
 Pulls all data via DashboardService.getStats(), which composes the lower
 services (TaskService, SubjectService, TimetableService) for us.
 */
public class DashboardView {
    private final DashboardService dashboardService;
    private VBox root;

    public DashboardView(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    public Parent getView() {
        root = new VBox(20);
        root.setPadding(new Insets(20));

        rebuild();   // initial render

        // Wrap in a ScrollPane so the dashboard works on smaller screens
        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        return scroll;
    }

    /*
     Re-fetches stats and rebuilds the entire dashboard content.
     Called on initial render and when the user clicks Refresh.
     */
    private void rebuild() {
        root.getChildren().clear();

        DashboardStats stats;
        try {
            stats = dashboardService.getStats();
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Could not load dashboard:\n" + e.getMessage());
            return;
        }

        root.getChildren().addAll(
                buildHeader(),
                buildStatCardsRow(stats),
                buildProgressSection(stats),
                buildSummarySection(stats),
                buildUpcomingSection(stats));
    }


    // SECTIONS
    /*Greeting + current date + refresh button on the right. */
    private Region buildHeader() {
        String name = Session.getInstance().getCurrentStudent().getFullName();
        Label greeting = new Label("Hello, " + name + " 👋");
        greeting.getStyleClass().add("h1");

        String today = LocalDate.now().format(
                DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL));
        Label dateLabel = new Label(today);
        dateLabel.getStyleClass().add("subtle");

        VBox left = new VBox(2, greeting, dateLabel);

        Button refresh = new Button("↻ Refresh");
        refresh.setOnAction(e -> rebuild());

        // A spacer that grows to push the refresh button to the right
        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        HBox header = new HBox(left, spacer, refresh);
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    /* Five stat cards: Total / Pending / In Progress / Completed / Overdue. */
    private Region buildStatCardsRow(DashboardStats s) {
        HBox row = new HBox(15);
        row.getChildren().addAll(
                buildCard("Total Tasks", String.valueOf(s.getTotalTasks()), null),
                buildCard("Pending",     String.valueOf(s.getPendingTasks()),    "accent-pending"),
                buildCard("In Progress", String.valueOf(s.getInProgressTasks()), "accent-progress"),
                buildCard("Completed",   String.valueOf(s.getCompletedTasks()),  "accent-completed"),
                buildCard("Overdue",     String.valueOf(s.getOverdueTasks()),    "accent-overdue")
        );
        // Make all five cards share width equally
        for (var node : row.getChildren()) {
            HBox.setHgrow(node, Priority.ALWAYS);
        }
        return row;
    }

    /*
     One stat card. The big number is colored if a color is provided;
     otherwise it uses the default text color.
     */
    private Region buildCard(String label, String value, String accentStyleClass) {
        Label number = new Label(value);
        number.getStyleClass().add("card-number");
        if (accentStyleClass != null) number.getStyleClass().add(accentStyleClass);

        Label name = new Label(label);
        name.getStyleClass().add("card-label");

        VBox card = new VBox(4, number, name);
        card.getStyleClass().add("card");
        card.setAlignment(Pos.CENTER_LEFT);
        return card;
    }

    /* Completion progress bar — visualizes "X% done overall". */
    private Region buildProgressSection(DashboardStats s) {
        Label heading = new Label("Overall Completion");
        heading.getStyleClass().add("h3");

        int pct = s.getCompletionPercentage();
        ProgressBar bar = new ProgressBar(pct / 100.0);
        bar.setPrefWidth(Double.MAX_VALUE);   // fill the row width
        bar.setPrefHeight(20);

        Label pctLabel = new Label(pct + "%  (" + s.getCompletedTasks() +
                " of " + s.getTotalTasks() + " tasks completed)");
        pctLabel.getStyleClass().add("subtle");

        VBox box = new VBox(8, heading, bar, pctLabel);
        box.getStyleClass().add("card");
        return box;
    }

    /* Subjects + timetable summaries side-by-side. */
    private Region buildSummarySection(DashboardStats s) {
        Region subjectsCard = buildSimpleSummary(
                "📚 Subjects",
                String.valueOf(s.getTotalSubjects()),
                "subjects in your planner");

        Region timetableCard = buildSimpleSummary(
                "🗓 Weekly Schedule",
                s.getTotalTimetableSlots() + " slots, " +
                        s.getTotalScheduledHoursDisplay() + " hrs",
                "of weekly recurring sessions");

        Region focusCard = buildSimpleSummary(
                "🎯 Focus This Week",
                s.getFocusThisWeekDisplay(),
                "(" + s.getFocusTodayDisplay() + " today)");

        HBox row = new HBox(15, subjectsCard, timetableCard, focusCard);
        HBox.setHgrow(subjectsCard, javafx.scene.layout.Priority.ALWAYS);
        HBox.setHgrow(timetableCard, javafx.scene.layout.Priority.ALWAYS);
        HBox.setHgrow(focusCard, javafx.scene.layout.Priority.ALWAYS);
        return row;
    }

    private Region buildSimpleSummary(String heading, String value, String caption) {
        Label h = new Label(heading);
        h.getStyleClass().add("h3");

        Label v = new Label(value);
        v.getStyleClass().add("h2");

        Label c = new Label(caption);
        c.getStyleClass().add("subtle");

        VBox box = new VBox(4, h, v, c);
        box.getStyleClass().add("card");
        return box;
    }

    /* "Coming up next" — list of upcoming tasks (next 7 days). */
    private Region buildUpcomingSection(DashboardStats s) {
        Label heading = new Label("⏰ Coming Up (next 7 days)");
        heading.getStyleClass().add("h3");

        VBox list = new VBox(6);
        if (s.getUpcomingTasks().isEmpty()) {
            Label empty = new Label("No tasks due in the next 7 days.");
            empty.getStyleClass().add("muted");
            list.getChildren().add(empty);
        } else {
            for (Task t : s.getUpcomingTasks()) {
                list.getChildren().add(buildUpcomingRow(t));
            }
        }

        VBox box = new VBox(10, heading, list);
        box.getStyleClass().add("card");
        return box;
    }

    private Region buildUpcomingRow(Task t) {
        Label title = new Label(t.getTitle());
        title.setStyle("-fx-font-weight: bold;");

        long daysUntil = java.time.temporal.ChronoUnit.DAYS.between(
                LocalDate.now(), t.getDueDate());
        String dayPhrase;
        if (daysUntil == 0)      dayPhrase = "today";
        else if (daysUntil == 1) dayPhrase = "tomorrow";
        else                     dayPhrase = "in " + daysUntil + " days";

        Label due = new Label(t.getDueDate() + " (" + dayPhrase + ") · " +
                t.getPriority().getDisplayName() + " priority");
        due.getStyleClass().add("subtle");

        VBox v = new VBox(2, title, due);
        v.setPadding(new Insets(8, 12, 8, 12));
        v.getStyleClass().add("upcoming-row");
        return v;
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