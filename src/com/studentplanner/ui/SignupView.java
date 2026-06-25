package com.studentplanner.ui;

import com.studentplanner.service.AuthService;
import com.studentplanner.service.exception.EmailAlreadyExistsException;
import com.studentplanner.service.exception.ValidationException;
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
 The signup screen.
 Mirrors LoginView's structure: builds its own UI subtree, holds a reference
 to AuthService, and fires callbacks on success or "switch to login" link click.
 */
public class SignupView {

    @FunctionalInterface
    public interface OnSignupSuccess {
        void run();
    }

    @FunctionalInterface
    public interface OnSwitchToLogin {
        void run();
    }

    private final AuthService authService;
    private final OnSignupSuccess onSignupSuccess;
    private final OnSwitchToLogin onSwitchToLogin;

    private TextField nameField;
    private TextField emailField;
    private PasswordField passwordField;
    private PasswordField confirmField;
    private Label errorLabel;

    public SignupView(AuthService authService,
                      OnSignupSuccess onSignupSuccess,
                      OnSwitchToLogin onSwitchToLogin) {
        this.authService = authService;
        this.onSignupSuccess = onSignupSuccess;
        this.onSwitchToLogin = onSwitchToLogin;
    }

    public Parent getView() {
        Label title = new Label("Create Your Account");
        title.getStyleClass().add("h1");

        Label subtitle = new Label("Start planning smarter");
        subtitle.getStyleClass().add("subtle");

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        form.setAlignment(Pos.CENTER);

        nameField = new TextField();
        nameField.setPromptText("Your full name");
        nameField.setPrefWidth(250);

        emailField = new TextField();
        emailField.setPromptText("you@example.com");
        emailField.setPrefWidth(250);

        passwordField = new PasswordField();
        passwordField.setPromptText("At least 8 characters");
        passwordField.setPrefWidth(250);

        confirmField = new PasswordField();
        confirmField.setPromptText("Re-enter your password");
        confirmField.setPrefWidth(250);

        form.add(new Label("Full name:"),       0, 0);
        form.add(nameField,                     1, 0);
        form.add(new Label("Email:"),           0, 1);
        form.add(emailField,                    1, 1);
        form.add(new Label("Password:"),        0, 2);
        form.add(passwordField,                 1, 2);
        form.add(new Label("Confirm password:"),0, 3);
        form.add(confirmField,                  1, 3);

        errorLabel = new Label();
        errorLabel.getStyleClass().add("error-text");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        Button signupButton = new Button("Create Account");
        signupButton.setDefaultButton(true);
        signupButton.getStyleClass().add("button-primary");
        signupButton.setPrefWidth(140);
        signupButton.setOnAction(event -> handleSignup());

        Hyperlink loginLink = new Hyperlink("Already have an account? Log in");
        loginLink.setOnAction(event -> onSwitchToLogin.run());

        VBox card = new VBox(15);
        card.setAlignment(Pos.CENTER);
        card.setMaxWidth(380);
        card.getStyleClass().add("auth-card");
        card.getChildren().addAll(
                title, subtitle, form, errorLabel, signupButton, loginLink);

        VBox wrapper = new VBox(card);
        wrapper.setAlignment(Pos.CENTER);
        wrapper.setPadding(new Insets(40));
        wrapper.getStyleClass().add("auth-root");
        return wrapper;
    }

    private void handleSignup() {
        hideError();

        String name     = nameField.getText();
        String email    = emailField.getText();
        String password = passwordField.getText();
        String confirm  = confirmField.getText();

        // UI-level validation: passwords match.
        // We do this here rather than in AuthService because "confirm password"
        // is a UI concept — the service only cares about ONE password.
        if (!password.equals(confirm)) {
            showError("Passwords do not match.");
            return;
        }

        try {
            authService.signup(name, email, password);
            // Success: clear the form so it's empty if the user logs out later
            nameField.clear();
            emailField.clear();
            passwordField.clear();
            confirmField.clear();
            onSignupSuccess.run();
        } catch (ValidationException e) {
            showError(e.getMessage());
        } catch (EmailAlreadyExistsException e) {
            showError(e.getMessage());
        } catch (Exception e) {
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

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}