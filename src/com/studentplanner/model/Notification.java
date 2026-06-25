package com.studentplanner.model;

/*
 A single user-facing notification (e.g., "Quiz overdue!", "Project due tomorrow").
 Immutable DTO produced fresh by NotificationService each time the bell
 is opened. Not persisted anywhere.
 Severity drives the UI styling: HIGH = red dot (overdue), MEDIUM = amber
 (due today/tomorrow). Designed to extend easily — INFO/LOW could be added
 later without breaking existing code.
 */
public class Notification {

    public enum Severity { HIGH, MEDIUM, LOW }

    private final String title;
    private final String detail;
    private final Severity severity;

    public Notification(String title, String detail, Severity severity) {
        this.title = title;
        this.detail = detail;
        this.severity = severity;
    }

    public String getTitle()       { return title; }
    public String getDetail()      { return detail; }
    public Severity getSeverity()  { return severity; }
}