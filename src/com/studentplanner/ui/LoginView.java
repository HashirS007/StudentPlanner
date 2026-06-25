package com.studentplanner.ui;

import com.studentplanner.service.AuthService;
import com.studentplanner.service.exception.InvalidCredentialsException;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

/*
 The login screen.
 Responsibilities:
 - Render email + password inputs and a Login button.
 - Provide a "Sign up" hyperlink to switch to the signup screen.
 - Call AuthService.login() on submit and show errors on failure.
 - On success, hand control back to MainApp via a callback.
 Design note: this class does NOT decide what happens after login it just
 fires a callback. That keeps the View ignorant of the rest of the app, which
 means it could be reused in a different program with a different "next screen".
 */
public class LoginView {
    // A small functional interface for "what to do after successful login".
    @FunctionalInterface
    public interface OnLoginSuccess {
        void run();
    }

    // A small functional interface for "user clicked the sign-up link".
    @FunctionalInterface
    public interface OnSwitchToSignup {
        void run();
    }

    private final AuthService authService;
    private final OnLoginSuccess onLoginSuccess;
    private final OnSwitchToSignup onSwitchToSignup;

    // The form inputs
    private TextField emailField;
    private PasswordField passwordField;
    private Label errorLabel;

    public LoginView(AuthService authService,
                     OnLoginSuccess onLoginSuccess,
                     OnSwitchToSignup onSwitchToSignup) {
        this.authService = authService;
        this.onLoginSuccess = onLoginSuccess;
        this.onSwitchToSignup = onSwitchToSignup;
    }

    /*
     Builds the scene-graph subtree for this view.
     Called by MainApp when it wants to display the login screen.
     */
    public Parent getView() {
        // ---------- Title ----------
        Label title = new Label("Student Planner");
        title.getStyleClass().add("h1");

        Label subtitle = new Label("Log in to continue");
        subtitle.getStyleClass().add("subtle");

        // ---------- Form ----------
        // GridPane lays out labels in column 0 and inputs in column 1.
        GridPane form = new GridPane();
        form.setHgap(10);   // horizontal gap between cells
        form.setVgap(10);   // vertical gap between rows
        form.setAlignment(Pos.CENTER);

        emailField = new TextField();
        emailField.setPromptText("you@example.com");   // greyed-out placeholder text
        emailField.setPrefWidth(250);

        passwordField = new PasswordField();
        passwordField.setPromptText("Your password");
        passwordField.setPrefWidth(250);

        form.add(new Label("Email:"),    0, 0);   // column 0, row 0
        form.add(emailField,             1, 0);   // column 1, row 0
        form.add(new Label("Password:"), 0, 1);
        form.add(passwordField,          1, 1);

        // ---------- Error label (hidden until something goes wrong) ----------
        errorLabel = new Label();
        errorLabel.getStyleClass().add("error-text");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);   // also don't reserve space when hidden

        // ---------- Buttons / links ----------
        Button loginButton = new Button("Log In");
        loginButton.setDefaultButton(true);// pressing Enter triggers this button
        loginButton.getStyleClass().add("button-primary");
        loginButton.setPrefWidth(120);
        loginButton.setOnAction(event -> handleLogin());

        Hyperlink signupLink = new Hyperlink("Don't have an account? Sign up");
        signupLink.setOnAction(event -> onSwitchToSignup.run());

        // ---------- Assemble everything ----------
        VBox card = new VBox(15);
        card.setAlignment(Pos.CENTER);
        card.setMaxWidth(380);
        card.getStyleClass().add("auth-card");
        card.getChildren().addAll(
                title, subtitle, form, errorLabel, loginButton, signupLink);

        VBox wrapper = new VBox(card);
        wrapper.setAlignment(Pos.CENTER);
        wrapper.setPadding(new Insets(40));
        wrapper.getStyleClass().add("auth-root");
        return wrapper;
    }

    /*
     Called when the user clicks "Log In" or presses Enter.
     Reads the inputs, calls AuthService, handles success and failure.
     */
    private void handleLogin() {
        // Hide any previous error first
        hideError();

        String email = emailField.getText();
        String password = passwordField.getText();

        try {
            authService.login(email, password);
            // Success: clear fields (in case user logs out and back in) and notify caller
            emailField.clear();
            passwordField.clear();
            onLoginSuccess.run();
        } catch (InvalidCredentialsException e) {
            // Wrong email or wrong password — show the same message either way
            showError(e.getMessage());
        } catch (Exception e) {
            // Catch-all: SQLException or anything unexpected. Show a generic message
            // and log the technical details for the developer.
            e.printStackTrace();
            showAlert("A problem occurred. Please try again.");
        }
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void hideError() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }

    // Pops up a modal dialog for serious / unexpected errors.
    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);   // remove the big bold header for a cleaner look
        alert.setContentText(message);
        alert.showAndWait();
    }
}