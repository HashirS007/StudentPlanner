package com.studentplanner.dao;

import com.studentplanner.config.DatabaseConfig;
import com.studentplanner.model.ActivityType;
import com.studentplanner.model.TimetableSlot;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/*
 Data Access Object for the 'timetable_slots' table.
 Translates between TimetableSlot objects and SQL rows. Unlike tasks,
 timetable_slots has student_id directly on the table, so no JOIN is
 needed for ownership filtering.
 */
public class TimetableDAO {

    // CREATE
    public void insert(TimetableSlot slot) throws SQLException {
        String sql = "INSERT INTO timetable_slots(student_id, subject_id, day_of_week, "
                + "start_time, end_time, activity_type) "
                + "VALUES(?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConfig.getDatabaseConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setInt(1, slot.getStudentId());

            // Nullable subject_id: setNull with the right SQL type
            if (slot.getSubjectId() == null) {
                ps.setNull(2, Types.INTEGER);
            } else {
                ps.setInt(2, slot.getSubjectId());
            }

            ps.setString(3, slot.getDayOfWeek().name());     // "MONDAY", etc.
            ps.setObject(4, slot.getStartTime());            // LocalTime -> SQL TIME
            ps.setObject(5, slot.getEndTime());
            ps.setString(6, slot.getActivityType().name());

            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    slot.setSlotId(keys.getInt(1));
                }
            }
        }
    }

    // READ
    /*
     Returns all slots for the given student, sorted by day of week (Mon to Sun)
     then by start time (earliest first).
     Custom day ordering uses MySQL's FIELD() function
     */
    public List<TimetableSlot> findAllByStudentId(int studentId) throws SQLException {
        String sql = "SELECT slot_id, student_id, subject_id, day_of_week, "
                + "start_time, end_time, activity_type "
                + "FROM timetable_slots WHERE student_id = ? "
                + "ORDER BY FIELD(day_of_week, 'MONDAY','TUESDAY','WEDNESDAY',"
                + "'THURSDAY','FRIDAY','SATURDAY','SUNDAY'), start_time";

        List<TimetableSlot> slots = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getDatabaseConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, studentId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    slots.add(mapRowToSlot(rs));
                }
            }
        }
        return slots;
    }

    public TimetableSlot findById(int slotId) throws SQLException {
        String sql = "SELECT slot_id, student_id, subject_id, day_of_week, "
                + "start_time, end_time, activity_type "
                + "FROM timetable_slots WHERE slot_id = ?";

        try (Connection conn = DatabaseConfig.getDatabaseConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, slotId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRowToSlot(rs);
                }
            }
        }
        return null;
    }

    // UPDATE
    public boolean update(TimetableSlot slot) throws SQLException {
        String sql = "UPDATE timetable_slots SET subject_id = ?, day_of_week = ?, "
                + "start_time = ?, end_time = ?, activity_type = ? WHERE slot_id = ?";

        try (Connection conn = DatabaseConfig.getDatabaseConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            if (slot.getSubjectId() == null) {
                ps.setNull(1, Types.INTEGER);
            } else {
                ps.setInt(1, slot.getSubjectId());
            }
            ps.setString(2, slot.getDayOfWeek().name());
            ps.setObject(3, slot.getStartTime());
            ps.setObject(4, slot.getEndTime());
            ps.setString(5, slot.getActivityType().name());
            ps.setInt(6, slot.getSlotId());

            return ps.executeUpdate() > 0;
        }
    }

    // DELETE
    public boolean delete(int slotId) throws SQLException {
        String sql = "DELETE FROM timetable_slots WHERE slot_id = ?";

        try (Connection conn = DatabaseConfig.getDatabaseConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, slotId);
            return ps.executeUpdate() > 0;
        }
    }

    // PRIVATE HELPER
    private TimetableSlot mapRowToSlot(ResultSet rs) throws SQLException {
        // Nullable subject_id: getInt returns 0 for SQL NULL — use getObject
        // to faithfully receive null instead.
        Integer subjectId = (Integer) rs.getObject("subject_id");

        return new TimetableSlot(
                rs.getInt("slot_id"),
                rs.getInt("student_id"),
                subjectId,
                DayOfWeek.valueOf(rs.getString("day_of_week")),
                rs.getObject("start_time", LocalTime.class),
                rs.getObject("end_time", LocalTime.class),
                ActivityType.valueOf(rs.getString("activity_type"))
        );
    }
}