package com.studentplanner.service;

import com.studentplanner.model.DashboardStats;
import com.studentplanner.model.Subject;
import com.studentplanner.model.Task;
import com.studentplanner.model.TaskStatus;
import com.studentplanner.model.TimetableSlot;

import java.sql.SQLException;
import java.util.List;
import com.studentplanner.service.StudySessionService;

/*
 Aggregates data from TaskService, SubjectService, and TimetableService into
 one DashboardStats snapshot for the Dashboard screen.
 This is a Facade: the Dashboard view only needs to call ONE method
 (getStats) and receives everything it needs to render. The orchestration
 complexity is hidden here, not pushed up to the UI.
 Note: this service composes other services rather than DAOs. That's
 deliberate — if a service like TaskService grows new validation or
 authorization logic, the Dashboard automatically benefits.
 */
public class DashboardService {

    /* Tasks within the next N days are considered "upcoming" for the dashboard. */
    private static final int UPCOMING_WINDOW_DAYS = 7;

    private final TaskService taskService;
    private final SubjectService subjectService;
    private final TimetableService timetableService;
    private final StudySessionService studySessionService;

    public DashboardService() {
        this.taskService = new TaskService();
        this.subjectService = new SubjectService();
        this.timetableService = new TimetableService();
        this.studySessionService = new StudySessionService();
    }

    /*
     Computes a fresh snapshot of dashboard statistics for the current student.
     Called every time the user opens or refreshes the dashboard.
     */
    public DashboardStats getStats() throws SQLException {
        // Pull all tasks once, then compute counts in-memory. One query is
        // cheaper than five — and the count-by-status logic stays here, in
        // the service that needs it, instead of polluting the DAO with five
        // very-similar SELECT queries.
        List<Task> allTasks = taskService.getAllTasksForCurrentStudent();

        int pending     = 0;
        int inProgress  = 0;
        int completed   = 0;
        int overdue     = 0;

        for (Task t : allTasks) {
            // isOverdue() lives on the model — single source of truth.
            // Note: an overdue task is also pending or in-progress; we count
            // both. Overdue is a separate dimension, not a status.
            if (t.isOverdue()) overdue++;

            switch (t.getStatus()) {
                case PENDING     -> pending++;
                case IN_PROGRESS -> inProgress++;
                case COMPLETED   -> completed++;
            }
        }

        // Subjects — a simple count.
        List<Subject> subjects = subjectService.getAllSubjectsForCurrentStudent();

        // Timetable — count and total minutes.
        List<TimetableSlot> slots = timetableService.getAllSlotsForCurrentStudent();
        int totalMinutes = 0;
        for (TimetableSlot s : slots) {
            totalMinutes += s.getDurationMinutes();
        }

        // "Coming up next" — service-layer method already filters & sorts.
        List<Task> upcoming = taskService.getTasksDueWithin(UPCOMING_WINDOW_DAYS);
        int focusThisWeek = studySessionService.getSecondsThisWeek();
        int focusToday    = studySessionService.getSecondsToday();

        return new DashboardStats(
                allTasks.size(),
                pending, inProgress, completed, overdue,
                subjects.size(),
                slots.size(), totalMinutes,
                upcoming,
                focusThisWeek, focusToday);   // <-- new args
    }
}