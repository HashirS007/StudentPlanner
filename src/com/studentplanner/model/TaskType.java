package com.studentplanner.model;

/*
 Category of a task.
 Stored in the DB as the enum NAME.
 */
public enum TaskType {
    ASSIGNMENT("Assignment"),
    QUIZ("Quiz"),
    EXAM("Exam"),
    STUDY("Study Session"),
    PROJECT("Project"),
    OTHER("Other");

    private final String displayName;

    TaskType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}