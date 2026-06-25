package com.studentplanner.service.exception;

//Thrown by AuthService.signup() when someone tries to register with an email that already exists in the database.

public class EmailAlreadyExistsException extends Exception {
    public EmailAlreadyExistsException(String email) {
        super("An account with email '" + email + "' already exists.");
    }
}
