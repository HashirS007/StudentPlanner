package com.studentplanner.model;

/*
 Priority level of a task. Stored in the DB as the enum's NAME (e.g. "HIGH").
 Each constant has a human-friendly display name for the UI, so the user
 sees "Low" instead of "LOW".
 */
public enum Priority {
    LOW("Low"),
    MEDIUM("Medium"),
    HIGH("High");

    private final String displayName;

    // Enum constructors are private
    Priority(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /*
     Override toString so JavaFX controls (ChoiceBox, etc.) automatically
     render the friendly name without any custom converter.
     */
    @Override
    public String toString() {
        return displayName;
    }
}