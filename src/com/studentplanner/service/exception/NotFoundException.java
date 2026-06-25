package com.studentplanner.service.exception;

/*
 Thrown when a requested record does not exist OR does not belong to the
 current user.
 */
public class NotFoundException extends Exception {
    public NotFoundException(String message) {
        super(message);
    }
}