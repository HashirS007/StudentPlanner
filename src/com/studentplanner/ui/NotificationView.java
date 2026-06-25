package com.studentplanner.ui;

import com.studentplanner.model.Notification;
import com.studentplanner.service.NotificationService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.sql.SQLException;
import java.util.List;

/*
 The popup content shown when the user clicks the bell icon.
 Builds a card with a header and a scrollable list of notifications.
 Each item is clickable (via the onItemClick callback), letting MainApp
 navigate to the relevant screen.
 Pure builder does not own its own popup window. The caller (MainApp)
 embeds this view inside a JavaFX Popup and handles show/hide.
 */
public class NotificationView {

    @FunctionalInterface
    public interface OnNotificationClick {
        void run();
    }

    private final NotificationService notificationService;
    private final OnNotificationClick onItemClick;

    public NotificationView(NotificationService notificationService,
                            OnNotificationClick onItemClick) {
        this.notificationService = notificationService;
        this.onItemClick = onItemClick;
    }

    public Parent getView() {
        // ---------- Header ----------
        Label heading = new Label("Notifications");
        heading.getStyleClass().add("h3");

        VBox header = new VBox(heading);
        header.setPadding(new Insets(16, 18, 12, 18));
        header.setStyle("-fx-border-color: transparent transparent #e5e7eb transparent;"
                + "-fx-border-width: 0 0 1 0;");

        // ---------- Body (scrollable list) ----------
        VBox list = new VBox(0);
        list.setPadding(new Insets(8, 0, 8, 0));

        try {
            List<Notification> notifications = notificationService.getNotifications();
            if (notifications.isEmpty()) {
                Label empty = new Label("You're all caught up. ✨");
                empty.getStyleClass().add("muted");
                empty.setPadding(new Insets(20));
                list.getChildren().add(empty);
            } else {
                for (Notification n : notifications) {
                    list.getChildren().add(buildItem(n));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            Label err = new Label("Could not load notifications.");
            err.getStyleClass().add("error-text");
            err.setPadding(new Insets(20));
            list.getChildren().add(err);
        }

        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: white; -fx-background-color: white;");
        scroll.setPrefViewportHeight(280);

        // ---------- Assemble ----------
        VBox root = new VBox(header, scroll);
        root.setPrefWidth(340);
        root.getStyleClass().add("notification-popup");

        return root;
    }

    /* One notification row: severity dot + title + detail. */
    private Region buildItem(Notification n) {
        // Coloured dot indicating severity
        Label dot = new Label("●");
        dot.setStyle("-fx-font-size: 14px;");
        switch (n.getSeverity()) {
            case HIGH   -> dot.setStyle(dot.getStyle() + "-fx-text-fill: #ef4444;");
            case MEDIUM -> dot.setStyle(dot.getStyle() + "-fx-text-fill: #f59e0b;");
            case LOW    -> dot.setStyle(dot.getStyle() + "-fx-text-fill: #6b7280;");
        }

        Label title = new Label(n.getTitle());
        title.setStyle("-fx-font-weight: bold;");

        Label detail = new Label(n.getDetail());
        detail.getStyleClass().add("subtle");

        VBox texts = new VBox(2, title, detail);

        javafx.scene.layout.HBox row = new javafx.scene.layout.HBox(10, dot, texts);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(10, 18, 10, 18));
        row.getStyleClass().add("notification-item");
        row.setOnMouseClicked(e -> onItemClick.run());

        return row;
    }
}