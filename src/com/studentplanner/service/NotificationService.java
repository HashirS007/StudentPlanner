package com.studentplanner.service;

import com.studentplanner.model.Notification;
import com.studentplanner.model.Notification.Severity;
import com.studentplanner.model.Task;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/*
 Aggregates "things the student should pay attention to" from across the app.
 Currently produces:
 - HIGH severity for every overdue task
 - MEDIUM severity for tasks due today, tomorrow, or in 2 days
 Composes TaskService rather than touching DAOs directly when TaskService
 adds new validation/auth rules in the future, NotificationService benefits
 automatically.
 Design decision: tasks due more than 2 days out are NOT shown here, even
 though the Dashboard's "Coming Up" section uses a 7-day window. Two days
 is the right window for an attention-grabbing badge anything longer
 creates notification fatigue. Different windows for different contexts;
 each lives where it's used (no duplication).
 */
public class NotificationService {

    /* Tasks within this many days are considered "due soon" for notifications. */
    private static final int DUE_SOON_WINDOW_DAYS = 2;

    private final TaskService taskService;

    public NotificationService() {
        this.taskService = new TaskService();
    }

    /*
     Returns all current notifications, ordered HIGH severity first
     (overdue tasks), then MEDIUM (due soon). Within each severity,
     tasks sort by due date ascending so the most urgent items
     appear at the top of the list.
     */
    public List<Notification> getNotifications() throws SQLException {
        List<Notification> result = new ArrayList<>();

        // HIGH severity: overdue tasks
        for (Task t : taskService.getOverdueTasks()) {
            result.add(new Notification(
                    "Overdue: " + t.getTitle(),
                    formatOverdueDetail(t),
                    Severity.HIGH));
        }

        // MEDIUM severity: due in the next 2 days (today, tomorrow, +2)
        for (Task t : taskService.getTasksDueWithin(DUE_SOON_WINDOW_DAYS)) {
            result.add(new Notification(
                    "Due soon: " + t.getTitle(),
                    formatDueSoonDetail(t),
                    Severity.MEDIUM));
        }

        return result;
    }

    /*
     Convenience for the badge: how many items would the user see if they
     opened the panel right now? Just the count, no UI strings built.
     Simpler than calling getNotifications().size() because it skips the
     formatting work small but principled separation between "give me
     the count" and "render the list".
     */
    public int getUnreadCount() throws SQLException {
        int count = 0;
        count += taskService.getOverdueTasks().size();
        count += taskService.getTasksDueWithin(DUE_SOON_WINDOW_DAYS).size();
        return count;
    }

    // PRIVATE FORMATTING HELPERS
    private String formatOverdueDetail(Task t) {
        long days = ChronoUnit.DAYS.between(t.getDueDate(), LocalDate.now());
        String when = (days == 1) ? "1 day overdue" : days + " days overdue";
        return when + " · " + t.getPriority().getDisplayName() + " priority";
    }

    private String formatDueSoonDetail(Task t) {
        long days = ChronoUnit.DAYS.between(LocalDate.now(), t.getDueDate());
        String when;
        if (days == 0)      when = "Due today";
        else if (days == 1) when = "Due tomorrow";
        else                when = "Due in " + days + " days";
        return when + " · " + t.getPriority().getDisplayName() + " priority";
    }
}