package com.studentplanner.model;

// Type of activity for a timetable slot. Stored in the DB as the enum NAME.
public enum ActivityType {
    LECTURE("Lecture"),
    LAB("Lab"),
    TUTORIAL("Tutorial"),
    SELF_STUDY("Self-Study"),
    GROUP_STUDY("Group Study"),
    OTHER("Other");

    private final String displayName;

    ActivityType(String displayName) {
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