package com.studentplanner.dao;

import com.studentplanner.config.DatabaseConfig;
import com.studentplanner.model.StudySession;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/*
 Data Access Object for the 'study_sessions' table.
 No UPDATE method — sessions are immutable once logged. You can either
 record a new session or delete a wrong one; you cannot retroactively
 edit duration or assignments. This is deliberate: sessions are facts
 that occurred, not editable plans.
 */
public class StudySessionDAO {

    public void insert(StudySession session) throws SQLException {
        String sql = "INSERT INTO study_sessions"
                + "(student_id, subject_id, task_id, started_at, ended_at, "
                + "duration_seconds, completed) "
                + "VALUES(?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConfig.getDatabaseConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setInt(1, session.getStudentId());

            if (session.getSubjectId() == null) ps.setNull(2, Types.INTEGER);
            else ps.setInt(2, session.getSubjectId());

            if (session.getTaskId() == null) ps.setNull(3, Types.INTEGER);
            else ps.setInt(3, session.getTaskId());

            ps.setObject(4, session.getStartedAt());
            ps.setObject(5, session.getEndedAt());
            ps.setInt(6, session.getDurationSeconds());
            ps.setBoolean(7, session.isCompleted());

            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    session.setSessionId(keys.getInt(1));
                }
            }
        }
    }

    /*
     Returns the N most recent sessions for the student, newest first.
     The 'limit' is applied via SQL LIMIT — the DB does the slicing,
     not Java. With a properly indexed table this is far cheaper than
     loading everything and slicing in memory.
     */
    public List<StudySession> findRecentByStudentId(int studentId, int limit) throws SQLException {
        String sql = "SELECT session_id, student_id, subject_id, task_id, "
                + "started_at, ended_at, duration_seconds, completed "
                + "FROM study_sessions WHERE student_id = ? "
                + "ORDER BY started_at DESC LIMIT ?";

        List<StudySession> list = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getDatabaseConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, studentId);
            ps.setInt(2, limit);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        }
        return list;
    }

    /*
     Returns sessions on or after the given start time, oldest first.
     Used for "this week" / "today" aggregations.
     */
    public List<StudySession> findSinceByStudentId(int studentId, LocalDateTime since) throws SQLException {
        String sql = "SELECT session_id, student_id, subject_id, task_id, "
                + "started_at, ended_at, duration_seconds, completed "
                + "FROM study_sessions WHERE student_id = ? AND started_at >= ? "
                + "ORDER BY started_at ASC";

        List<StudySession> list = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getDatabaseConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, studentId);
            ps.setObject(2, since);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        }
        return list;
    }

    public boolean delete(int sessionId) throws SQLException {
        String sql = "DELETE FROM study_sessions WHERE session_id = ?";
        try (Connection conn = DatabaseConfig.getDatabaseConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, sessionId);
            return ps.executeUpdate() > 0;
        }
    }

    private StudySession mapRow(ResultSet rs) throws SQLException {
        Integer subjectId = (Integer) rs.getObject("subject_id");
        Integer taskId    = (Integer) rs.getObject("task_id");

        return new StudySession(
                rs.getInt("session_id"),
                rs.getInt("student_id"),
                subjectId,
                taskId,
                rs.getObject("started_at", LocalDateTime.class),
                rs.getObject("ended_at",   LocalDateTime.class),
                rs.getInt("duration_seconds"),
                rs.getBoolean("completed")
        );
    }
}