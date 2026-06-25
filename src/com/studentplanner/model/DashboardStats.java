package com.studentplanner.model;

import java.util.List;

/*
 Data Transfer Object holding all the values shown on the Dashboard.
 This is NOT a persisted entity — there's no `dashboard_stats` table.
 It's a snapshot of derived values, computed by DashboardService at the
 moment the user opens (or refreshes) the screen.
 Why a DTO instead of returning a Map<String, Object> or a tuple?
 - Type safety: the View knows the exact shape of what it gets.
 - Discoverability: IDE autocompletes every available stat.
 - Self-documenting: the field names ARE the documentation.
 The fields are 'final' because a stats snapshot should never change after
 creation — if the user wants newer numbers, they get a new DashboardStats.
 Immutability also makes this object safe to share across threads (not
 relevant here, but good habit).
 */
public class DashboardStats {

    // ---------- Task counts ----------
    private final int totalTasks;
    private final int pendingTasks;
    private final int inProgressTasks;
    private final int completedTasks;
    private final int overdueTasks;
    private final int focusSecondsThisWeek;
    private final int focusSecondsToday;

    // ---------- Subjects ----------
    private final int totalSubjects;

    // ---------- Timetable ----------
    private final int totalTimetableSlots;
    private final int totalScheduledMinutes;   // sum across all slots

    // ---------- Lookahead ----------
    private final List<Task> upcomingTasks;     // due in the next N days, ordered

    public DashboardStats(int totalTasks, int pendingTasks, int inProgressTasks,
                          int completedTasks, int overdueTasks,
                          int totalSubjects,
                          int totalTimetableSlots, int totalScheduledMinutes,
                          List<Task> upcomingTasks, int focusSecondsThisWeek, int focusSecondsToday) {
        this.totalTasks = totalTasks;
        this.pendingTasks = pendingTasks;
        this.inProgressTasks = inProgressTasks;
        this.completedTasks = completedTasks;
        this.overdueTasks = overdueTasks;
        this.totalSubjects = totalSubjects;
        this.totalTimetableSlots = totalTimetableSlots;
        this.totalScheduledMinutes = totalScheduledMinutes;
        this.focusSecondsThisWeek = focusSecondsThisWeek;
        this.focusSecondsToday = focusSecondsToday;
        // Wrap in a defensive immutable copy so callers can't mutate our list
        this.upcomingTasks = List.copyOf(upcomingTasks);
    }

    // ---------- Getters only (no setters — immutable) ----------
    public int getTotalTasks()            { return totalTasks; }
    public int getPendingTasks()          { return pendingTasks; }
    public int getInProgressTasks()       { return inProgressTasks; }
    public int getCompletedTasks()        { return completedTasks; }
    public int getOverdueTasks()          { return overdueTasks; }
    public int getTotalSubjects()         { return totalSubjects; }
    public int getTotalTimetableSlots()   { return totalTimetableSlots; }
    public int getTotalScheduledMinutes() { return totalScheduledMinutes; }
    public List<Task> getUpcomingTasks()  { return upcomingTasks; }
    public int getFocusSecondsThisWeek() { return focusSecondsThisWeek; }
    public int getFocusSecondsToday()    { return focusSecondsToday; }

    public String getFocusThisWeekDisplay() {
        return formatHoursMinutes(focusSecondsThisWeek);
    }

    public String getFocusTodayDisplay() {
        return formatHoursMinutes(focusSecondsToday);
    }

    private static String formatHoursMinutes(int totalSeconds) {
        int totalMinutes = totalSeconds / 60;
        int hours = totalMinutes / 60;
        int minutes = totalMinutes % 60;
        if (hours > 0) return hours + "h " + minutes + "m";
        return minutes + "m";
    }

    // Convenience: hours rounded to one decimal, e.g. "12.5". *
    public String getTotalScheduledHoursDisplay() {
        double hours = totalScheduledMinutes / 60.0;
        return String.format("%.1f", hours);
    }

    // Returns the percentage of tasks that are completed (0–100, integer).
    public int getCompletionPercentage() {
        if (totalTasks == 0) return 0;
        return (int) Math.round((completedTasks * 100.0) / totalTasks);
    }
}