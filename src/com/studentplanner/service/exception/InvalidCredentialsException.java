package com.studentplanner.service.exception;

/*
Thrown by AuthService.login() if the entered email is unknown OR the password is wrong.

 */

public class InvalidCredentialsException extends Exception {
    public InvalidCredentialsException() {
        super("Invalid Credentials");
    }
    public InvalidCredentialsException(String message) {
        super(message);
    }
}
