package com.studentplanner.model;

import java.time.LocalDateTime;

/*
 Model class representing one row in the 'study_sessions' table.
 Each row records ONE focus session  a contiguous block of study time
 the user committed to via the Focus screen.
 Fields:
 - subjectId / taskId are nullable: a session may be unassociated,
   subject-only, or task-specific.
 - durationSeconds is the actual FOCUSED time (excluding pauses),
   not the wall-clock difference between started_at and ended_at.
 - completed = true if the timer ran to 00:00; false if user stopped early.
 Stored times use LocalDateTime (date + time, no time zone). For a single-
 user-per-machine app this is correct — sessions are local to the student's
 computer.
 */
public class StudySession {

    private int sessionId;
    private int studentId;
    private Integer subjectId;       // nullable
    private Integer taskId;          // nullable
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private int durationSeconds;     // focused time, excluding pauses
    private boolean completed;       // ran to 00:00 vs. stopped manually

    public StudySession() {
    }

    /* Constructor for inserting a NEW session — sessionId auto-generated. */
    public StudySession(int studentId, Integer subjectId, Integer taskId,
                        LocalDateTime startedAt, LocalDateTime endedAt,
                        int durationSeconds, boolean completed) {
        this.studentId = studentId;
        this.subjectId = subjectId;
        this.taskId = taskId;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.durationSeconds = durationSeconds;
        this.completed = completed;
    }

    /* Full constructor — for loading from the DB. */
    public StudySession(int sessionId, int studentId, Integer subjectId, Integer taskId,
                        LocalDateTime startedAt, LocalDateTime endedAt,
                        int durationSeconds, boolean completed) {
        this.sessionId = sessionId;
        this.studentId = studentId;
        this.subjectId = subjectId;
        this.taskId = taskId;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.durationSeconds = durationSeconds;
        this.completed = completed;
    }

    // ---------- Getters & Setters ----------
    public int getSessionId() { return sessionId; }
    public void setSessionId(int sessionId) { this.sessionId = sessionId; }

    public int getStudentId() { return studentId; }
    public void setStudentId(int studentId) { this.studentId = studentId; }

    public Integer getSubjectId() { return subjectId; }
    public void setSubjectId(Integer subjectId) { this.subjectId = subjectId; }

    public Integer getTaskId() { return taskId; }
    public void setTaskId(Integer taskId) { this.taskId = taskId; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getEndedAt() { return endedAt; }
    public void setEndedAt(LocalDateTime endedAt) { this.endedAt = endedAt; }

    public int getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(int durationSeconds) { this.durationSeconds = durationSeconds; }

    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }

    // ---------- Behavior ----------

    /* Convenience: duration in minutes, rounded down. */
    public int getDurationMinutes() {
        return durationSeconds / 60;
    }

    /* Display-friendly duration: "25m", "1h 15m". */
    public String getDurationDisplay() {
        int totalMinutes = durationSeconds / 60;
        int hours = totalMinutes / 60;
        int minutes = totalMinutes % 60;
        if (hours > 0) return hours + "h " + minutes + "m";
        return minutes + "m";
    }

    @Override
    public String toString() {
        return "Session(" + getDurationDisplay() + (completed ? ", completed" : ", stopped") + ")";
    }
}