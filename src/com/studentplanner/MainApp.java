package com.studentplanner;

import com.studentplanner.service.*;
import com.studentplanner.ui.*;
import com.studentplanner.util.DatabaseInitializer;
import com.studentplanner.util.Session;
import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.scene.control.Button;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import com.studentplanner.ui.DashboardView;
import com.studentplanner.service.DashboardService;
import com.studentplanner.service.NotificationService;
import com.studentplanner.ui.NotificationView;
import javafx.scene.layout.StackPane;
import javafx.stage.Popup;
import javafx.geometry.Pos;
import javafx.scene.layout.Region;
import java.sql.SQLException;
import com.studentplanner.service.StudySessionService;
import com.studentplanner.ui.FocusView;

/*
 initializes the DB, then displays one of
 three screens Login, Signup, or the (placeholder) main app — by swapping
 the Scene's root node.
 Why swap the root instead of opening new windows? It keeps the user in a
 single window, which is the standard desktop-app feel. Multiple windows
 would feel jarring and complicate window management.
 */
public class MainApp extends Application {

    private Stage primaryStage;
    private Scene scene;
    private AuthService authService;
    private NotificationService notificationService;
    private FocusView currentFocusView;

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;

        // 1. Make sure the database, tables, and indexes all exist.
        DatabaseInitializer.initializeDatabase();

        // 2. Build the long-lived service objects.
        this.authService = new AuthService();
        this.notificationService = new NotificationService();
        // 3. Set up the Scene with a temporary root; we'll swap it immediately.
        this.scene = new Scene(new VBox(), 700, 500);
        this.scene = new Scene(new VBox(), 1000, 650);
        scene.getStylesheets().add(getClass().getResource("/com/studentplanner/ui/styles.css").toExternalForm());
        primaryStage.setTitle("Student Planner");
        primaryStage.setScene(scene);
        primaryStage.show();

        // 4. Start the user on the login screen.
        showLogin();
    }

    // Switches the current scene's root to a fresh login screen.
    private void showLogin() {
        LoginView loginView = new LoginView(
                authService,
                this::showMainApp,        // on successful login basically a function call to this.showMainApp()
                this::showSignup          // on "sign up" link click
        );
        scene.setRoot(loginView.getView());
        primaryStage.setTitle("Student Planner — Log In");
    }

    // Switches to a fresh signup screen.
    private void showSignup() {
        SignupView signupView = new SignupView(
                authService,
                this::showMainApp,        // on successful signup (auto-login)
                this::showLogin           // on "log in" link click
        );
        scene.setRoot(signupView.getView());
        primaryStage.setTitle("Student Planner — Sign Up");
    }

    private void showMainApp() {
        DashboardView dashboardView = new DashboardView(new DashboardService());
        Parent dashRoot = dashboardView.getView();

        // ---------- Navigation buttons ----------
        Button subjectsButton  = new Button("Subjects");
        Button tasksButton     = new Button("Tasks");
        Button timetableButton = new Button("Timetable");
        Button focusButton     = new Button("Focus");
        Button logoutButton    = new Button("Log Out");

        subjectsButton.setOnAction(e -> showSubjects());
        tasksButton.setOnAction(e -> showTasks());
        timetableButton.setOnAction(e -> showTimetable());
        focusButton.setOnAction(e -> showFocus());
        logoutButton.setOnAction(e -> {
            authService.logout();
            showLogin();
        });

        // ---------- Notification bell with badge ----------
        Region bell = buildNotificationBell();

        // ---------- Spacer pushes the bell + logout to the right ----------
        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        HBox navBar = new HBox(10,
                subjectsButton, tasksButton, timetableButton, focusButton,
                spacer, bell, logoutButton);
        navBar.setAlignment(Pos.CENTER_LEFT);
        navBar.getStyleClass().add("nav-bar");

        BorderPane wrapper = new BorderPane();
        wrapper.setTop(navBar);
        wrapper.setCenter(dashRoot);

        scene.setRoot(wrapper);
        primaryStage.setTitle("Student Planner — Dashboard");
    }

    /*
     Builds the bell button with a live count badge in the corner.
     Clicking the bell opens a popup of notifications.
     */
    private Region buildNotificationBell() {
        // The bell glyph itself (Unicode bell character — no image asset needed)
        Button bellButton = new Button("\uD83D\uDD14");
        bellButton.getStyleClass().add("notification-bell");

        // The little red badge that overlays the bell
        Label badge = new Label();
        badge.getStyleClass().add("notification-badge");
        badge.setVisible(false);
        badge.setManaged(false);

        // StackPane lets us layer the badge on top of the bell.
        // We pin the badge to the top-right corner via StackPane alignment.
        StackPane bellStack = new StackPane(bellButton, badge);
        StackPane.setAlignment(badge, Pos.TOP_RIGHT);
        badge.setTranslateX(-4);   // nudge the badge slightly inward
        badge.setTranslateY(2);

        // Refresh the badge count
        Runnable refreshBadge = () -> {
            try {
                int count = notificationService.getUnreadCount();
                if (count <= 0) {
                    badge.setVisible(false);
                    badge.setManaged(false);
                } else {
                    badge.setText(count > 9 ? "9+" : String.valueOf(count));
                    badge.setVisible(true);
                    badge.setManaged(true);
                }
            } catch (SQLException e) {
                e.printStackTrace();
                badge.setVisible(false);
                badge.setManaged(false);
            }
        };
        refreshBadge.run();   // initial value

        // Open the popup on click
        Popup popup = new Popup();
        popup.setAutoHide(true);   // closes when user clicks outside

        bellButton.setOnAction(e -> {
            if (popup.isShowing()) {
                popup.hide();
                return;
            }
            // Build a fresh NotificationView each time so the list is current
            NotificationView notifView = new NotificationView(
                    notificationService,
                    () -> {
                        popup.hide();
                        showTasks();   // clicking any item jumps to Tasks
                    });
            popup.getContent().clear();
            popup.getContent().add(notifView.getView());

            // Anchor the popup just below the bell
            var bounds = bellButton.localToScreen(bellButton.getBoundsInLocal());
            popup.show(primaryStage, bounds.getMinX() - 280, bounds.getMaxY() + 4);

            // Once the user opens the panel, refresh the badge so it reflects
            // any changes since the last navigation
            refreshBadge.run();
        });

        return bellStack;
    }


    private void showTasks() {
        TaskView taskView = new TaskView(new TaskService(), new SubjectService());
        Parent tasksRoot = taskView.getView();

        Button back = new Button("← Back to Home");
        back.setOnAction(e -> showMainApp());

        HBox topBar = new HBox(back);
        topBar.setPadding(new Insets(10));

        BorderPane wrapper = new BorderPane();
        wrapper.setTop(topBar);
        wrapper.setCenter(tasksRoot);

        scene.setRoot(wrapper);
        primaryStage.setTitle("Student Planner — Tasks");
    }

    /* Navigates to the Subjects screen, with a Back button to return home. */
    private void showSubjects() {
        SubjectView subjectView = new SubjectView(new SubjectService());
        Parent subjectsRoot = subjectView.getView();

        Button back = new Button("← Back to Home");
        back.setOnAction(e -> showMainApp());

        BorderPane wrapper = new BorderPane();
        HBox topBar = new HBox(back);
        topBar.setPadding(new Insets(10));
        wrapper.setTop(topBar);
        wrapper.setCenter(subjectsRoot);

        scene.setRoot(wrapper);
        primaryStage.setTitle("Student Planner — Subjects");
    }

    private void showTimetable() {
        TimetableView timetableView = new TimetableView(new TimetableService(), new SubjectService());
        Parent timetableRoot = timetableView.getView();

        Button back = new Button("← Back to Home");
        back.setOnAction(e -> showMainApp());

        HBox topBar = new HBox(back);
        topBar.setPadding(new Insets(10));

        BorderPane wrapper = new BorderPane();
        wrapper.setTop(topBar);
        wrapper.setCenter(timetableRoot);

        scene.setRoot(wrapper);
        primaryStage.setTitle("Student Planner — Timetable");
    }

    /* Navigates to the Focus screen. */
    private void showFocus() {
        currentFocusView = new FocusView(
                new StudySessionService(),
                new SubjectService(),
                new TaskService());
        Parent focusRoot = currentFocusView.getView();

        Button back = new Button("← Back to Home");
        back.setOnAction(e -> {
            // CRITICAL: clean up the timer before navigating away
            if (currentFocusView != null) {
                currentFocusView.cleanup();
                currentFocusView = null;
            }
            showMainApp();
        });

        HBox topBar = new HBox(back);
        topBar.setPadding(new Insets(10));

        BorderPane wrapper = new BorderPane();
        wrapper.setTop(topBar);
        wrapper.setCenter(focusRoot);

        scene.setRoot(wrapper);
        primaryStage.setTitle("Student Planner — Focus");
    }

    public static void main(String[] args) {
        launch(args);
    }
}

