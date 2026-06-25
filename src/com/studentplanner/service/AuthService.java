package com.studentplanner.service;

import com.studentplanner.dao.StudentDAO;
import com.studentplanner.model.Student;
import com.studentplanner.service.exception.EmailAlreadyExistsException;
import com.studentplanner.service.exception.InvalidCredentialsException;
import com.studentplanner.service.exception.ValidationException;
import com.studentplanner.util.PasswordUtil;
import com.studentplanner.util.Session;

import java.sql.SQLException;

/*
 Service responsible for authentication: signup, login, and logout.
 Sits between the UI layer (LoginView, SignupView) and the lower layers
 (StudentDAO, PasswordUtil). The UI only ever talks to this class for
 auth it never sees passwords being hashed or SQL being executed.
 Responsibilities:
 - Validate user input (lengths, formats, password strength)
 - Coordinate hashing/verification via PasswordUtil
 - Coordinate persistence via StudentDAO
 - Manage Session state on login/logout
 Exceptions:
 - ValidationException             -> input failed a rule (UI shows message)
 - EmailAlreadyExistsException     -> signup with a duplicate email
 - InvalidCredentialsException     -> login with wrong email or password
 - SQLException                    -> database problem (UI shows generic error)
 */

public class AuthService {

    // Minimum password length. 8 is a common industry baseline.
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_EMAIL_LENGTH = 100;
    private final StudentDAO studentDAO;

    public AuthService() {
        this.studentDAO = new StudentDAO();
    }
    /*Signup:
     Creates a new account.
     Steps (in order):
     1. Trim and validate inputs.
     2. Check the email isn't already taken.
     3. Generate a fresh salt.
     4. Hash (password + salt).
     5. Build a Student object and insert it.
     6. Populate the Session so the user is "logged in" immediately.
     @return the newly created Student (with auto-generated id filled in)
     */
    public Student signup(String fullName, String email, String plainPassword)
            throws ValidationException, EmailAlreadyExistsException, SQLException {
        fullName = fullName == null ? "" : fullName.trim();
        email    = email    == null ? "" : email.trim().toLowerCase();
        // Note: we do NOT trim the password leading/trailing spaces are
        // legitimate parts of a password if the user chose them.
        validateFullName(fullName);
        validateEmail(email);
        validatePassword(plainPassword);
        if (studentDAO.existsByEmail(email)) {
            throw new EmailAlreadyExistsException(email);
        }
        // Generate per-user salt and hash the password with it
        String salt = PasswordUtil.generateSalt();
        String hash = PasswordUtil.hashPassword(plainPassword, salt);

        Student student = new Student(fullName, email, hash, salt);
        studentDAO.insert(student);   // fills in studentId via RETURN_GENERATED_KEYS

        // Auto-login after signup — common UX, saves the user a step
        Session.getInstance().setCurrentStudent(student);

        return student;
    }

    /* Login:
     Verifies credentials and, on success, populates the Session.
     @return the logged-in Student
     @throws InvalidCredentialsException if the email is unknown OR the password is wrong
     (deliberately the same exception for both, to prevent email enumeration)
     */
    public Student login(String email, String plainPassword)
            throws InvalidCredentialsException, SQLException {

        email = email == null ? "" : email.trim().toLowerCase();
        if (plainPassword == null) plainPassword = "";

        Student student = studentDAO.findByEmail(email);
        if (student == null) {
            throw new InvalidCredentialsException();
        }

        boolean ok = PasswordUtil.verifyPassword(
                plainPassword, student.getPasswordSalt(), student.getPasswordHash());

        if (!ok) {
            throw new InvalidCredentialsException();
        }

        Session.getInstance().setCurrentStudent(student);
        return student;
    }

    /* Logout. The UI is expected to navigate back to the login screen. */
    public void logout() {
        Session.getInstance().setCurrentStudent(null);
    }

    // Validation Functions
    private void validateFullName(String fullName) throws ValidationException {
        if (fullName.isEmpty()) {
            throw new ValidationException("Full name cannot be empty.");
        }
        if (fullName.length() > MAX_NAME_LENGTH) {
            throw new ValidationException(
                    "Full name must be at most " + MAX_NAME_LENGTH + " characters.");
        }
    }

    private void validateEmail(String email) throws ValidationException {
        if (email.isEmpty()) {
            throw new ValidationException("Email cannot be empty.");
        }
        if (email.length() > MAX_EMAIL_LENGTH) {
            throw new ValidationException(
                    "Email must be at most " + MAX_EMAIL_LENGTH + " characters.");
        }
        // Minimal sanity check — full RFC-compliant email validation is a
        // famous can of worms. Requiring an "@" with text on both sides
        // catches 99% of typos without false positives on edge-case real emails.
        int at = email.indexOf('@');
        if (at <= 0 || at == email.length() - 1) {
            throw new ValidationException("Please enter a valid email address.");
        }
    }

    private void validatePassword(String password) throws ValidationException {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new ValidationException(
                    "Password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
    }
}