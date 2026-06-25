package com.studentplanner.service;

import com.studentplanner.dao.StudySessionDAO;
import com.studentplanner.dao.SubjectDAO;
import com.studentplanner.dao.TaskDAO;
import com.studentplanner.model.StudySession;
import com.studentplanner.model.Subject;
import com.studentplanner.model.Task;
import com.studentplanner.service.exception.NotFoundException;
import com.studentplanner.service.exception.ValidationException;
import com.studentplanner.util.Session;

import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/*
 Service for recording and querying study sessions.
 Authorization model:
 - Sessions are filtered by current student.
 - If a session references a subject or task, those must belong to the
   current student (transitive ownership via SubjectDAO / TaskDAO).
 Validation rules:
 - duration_seconds must be at least MIN_DURATION_SECONDS to prevent
   accidental "I clicked Start and Stop" 1-second sessions polluting
   the record. Sessions shorter than this are silently discarded.
 */
public class StudySessionService {

    /* Sessions shorter than this aren't logged — protects against accidental clicks. */
    public static final int MIN_DURATION_SECONDS = 60;

    private final StudySessionDAO sessionDAO;
    private final SubjectDAO subjectDAO;
    private final TaskDAO taskDAO;

    public StudySessionService() {
        this.sessionDAO = new StudySessionDAO();
        this.subjectDAO = new SubjectDAO();
        this.taskDAO = new TaskDAO();
    }

    // CREATE
    /*
     Records a completed study session. Returns null (not throwing) if the
     duration is below the minimum — this is an expected-but-discarded case,
     not an error.
     */
    public StudySession recordSession(Integer subjectId, Integer taskId,
                                      LocalDateTime startedAt, LocalDateTime endedAt,
                                      int durationSeconds, boolean completed)
            throws ValidationException, NotFoundException, SQLException {

        if (startedAt == null || endedAt == null) {
            throw new ValidationException("Session timestamps are required.");
        }
        if (durationSeconds < MIN_DURATION_SECONDS) {
            return null;   // silently skip — too short to log
        }

        // Authorization: validate the references if present
        if (subjectId != null) requireOwnedSubject(subjectId);
        if (taskId    != null) requireOwnedTask(taskId);

        int currentStudentId = Session.getInstance().getCurrentStudentId();
        StudySession session = new StudySession(
                currentStudentId, subjectId, taskId,
                startedAt, endedAt, durationSeconds, completed);
        sessionDAO.insert(session);
        return session;
    }


    // READ
    public List<StudySession> getRecentSessions(int limit) throws SQLException {
        int currentStudentId = Session.getInstance().getCurrentStudentId();
        return sessionDAO.findRecentByStudentId(currentStudentId, limit);
    }

    /*
     Total seconds focused this week (Monday 00:00 onwards).
     Used by the Dashboard's "Focus this week" card.
     */
    public int getSecondsThisWeek() throws SQLException {
        int currentStudentId = Session.getInstance().getCurrentStudentId();
        LocalDateTime mondayStart = startOfThisWeek();
        List<StudySession> sessions = sessionDAO.findSinceByStudentId(currentStudentId, mondayStart);

        int total = 0;
        for (StudySession s : sessions) total += s.getDurationSeconds();
        return total;
    }

    /* Total seconds focused today (00:00 onwards). */
    public int getSecondsToday() throws SQLException {
        int currentStudentId = Session.getInstance().getCurrentStudentId();
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        List<StudySession> sessions = sessionDAO.findSinceByStudentId(currentStudentId, todayStart);

        int total = 0;
        for (StudySession s : sessions) total += s.getDurationSeconds();
        return total;
    }

    // DELETE
    public void deleteSession(int sessionId) throws NotFoundException, SQLException {
        // Ownership check: only let users delete their own sessions
        StudySession session = sessionDAO.findRecentByStudentId(
                        Session.getInstance().getCurrentStudentId(), Integer.MAX_VALUE)
                .stream()
                .filter(s -> s.getSessionId() == sessionId)
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Session not found."));

        boolean deleted = sessionDAO.delete(sessionId);
        if (!deleted) throw new NotFoundException("Session not found.");
    }

    // PRIVATE HELPERS
    private void requireOwnedSubject(int subjectId) throws NotFoundException, SQLException {
        Subject subject = subjectDAO.findById(subjectId);
        int currentStudentId = Session.getInstance().getCurrentStudentId();
        if (subject == null || subject.getStudentId() != currentStudentId) {
            throw new NotFoundException("Subject not found.");
        }
    }

    private void requireOwnedTask(int taskId) throws NotFoundException, SQLException {
        Task task = taskDAO.findById(taskId);
        if (task == null) throw new NotFoundException("Task not found.");
        // Tasks are owned through their subject — reuse that check
        requireOwnedSubject(task.getSubjectId());
    }

    /* Returns Monday 00:00 of the current week as a LocalDateTime. */
    private LocalDateTime startOfThisWeek() {
        LocalDate today = LocalDate.now();
        // dayOfWeek.getValue() is 1 (MON) to 7 (SUN); subtract 1 to get days since Monday
        int daysSinceMonday = today.getDayOfWeek().getValue() - 1;
        return today.minusDays(daysSinceMonday).atStartOfDay();
    }
}