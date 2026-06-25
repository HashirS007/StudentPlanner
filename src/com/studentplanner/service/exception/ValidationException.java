package com.studentplanner.service.exception;
/*
Thrown by Service layer methods when user input fails validation rules
(e.g., empty subject name, password too short, credit hours out of range).
 */
public class ValidationException  extends Exception {
    public ValidationException(String message) {
        super(message);
    }
}
