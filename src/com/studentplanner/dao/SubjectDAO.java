package com.studentplanner.dao;

import com.studentplanner.config.DatabaseConfig;
import com.studentplanner.model.Subject;

import java.sql.Connection;
import java.sql.PreparedStatement; //You write SQL with ? placeholders, then bind values to them by position. The main benefit of this is that it prevents SQL injection
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

// purpose of DAO is to translate between subject obj and sql rows
//The exceptions will be handled in services

public class SubjectDAO {
    // ---------------------------CREATE-----------------------------
    public void insertSubject(Subject subject) throws SQLException{
        String sql = "INSERT INTO subjects(student_id, subject_name, instructor_name, credit_hours) "
                + "VALUES(?, ?, ?, ?)";
        try (Connection con = DatabaseConfig.getDatabaseConnection(); PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS))
        //  Statement.RETURN_GENERATED_KEYS  tells JDBC after insert hand me back the auto generated id
        {
            // Bind values to the four ? placeholders, in order
            ps.setInt(1, subject.getStudentId());
            ps.setString(2, subject.getSubjectName());
            ps.setString(3, subject.getInstructorName());
            ps.setInt(4, subject.getCreditHours());
            //executeUpdate runs INSERT/UPDATE/DELETE and returns row-count affected
            ps.executeUpdate();
            // Retrieve the auto-generated subject_id MySQL just created, getGeneratedKeys() Returns a ResultSet containing the auto-generated id(s)
            try (ResultSet keys = ps.getGeneratedKeys())
            {
                if (keys.next()) {
                    subject.setSubjectId(keys.getInt(1));
                }
            }
        }
    }
    // ---------------------------READ-----------------------------
    //Returns all subjects belonging to one student, ordered alphabetically.
    //findAllByStudentId returns an empty list, never null. Callers can iterate without null-checks. (findById returns null because "one or none" is a different contract.)
    public List<Subject> findAllByStudentId(int studentId) throws SQLException{
        String sql = "SELECT subject_id, student_id, subject_name, instructor_name, credit_hours "
                + "FROM subjects WHERE student_id = ? ORDER BY subject_name";
        List<Subject> subjects = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getDatabaseConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, studentId);

            // executeQuery runs SELECT and returns a ResultSet (cursor over rows)
            try (ResultSet rs = ps.executeQuery())
            // exceuteQuery() Runs SELECT, returns a ResultSet
            {
                while (rs.next()) { //while there exists a next row
                    subjects.add(mapRowToSubject(rs));
                }
            }
        }
        return subjects;
    }
    // Finds a single subject by id. Returns null if not found.
    public Subject findById(int subjectId) throws SQLException {
        String sql = "SELECT subject_id, student_id, subject_name, instructor_name, credit_hours "
                + "FROM subjects WHERE subject_id = ?";

        try (Connection conn = DatabaseConfig.getDatabaseConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, subjectId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRowToSubject(rs);
                }
            }
        }
        return null;
    }
    // ---------------------------UPDATE-----------------------------
    // Updates an existing subject identified by its subjectId.
    public boolean update(Subject subject) throws SQLException {
        String sql = "UPDATE subjects SET subject_name = ?, instructor_name = ?, credit_hours = ? "
                + "WHERE subject_id = ?";

        try (Connection conn = DatabaseConfig.getDatabaseConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, subject.getSubjectName());
            ps.setString(2, subject.getInstructorName());
            ps.setInt(3, subject.getCreditHours());
            ps.setInt(4, subject.getSubjectId());

            // executeUpdate returns the number of rows affected
            return ps.executeUpdate() > 0;
        }
    }
    // ---------------------------DELETE-----------------------------
    // Deletes a subject by id. Because the schema declares ON DELETE CASCADE for tasks(subject_id), all related tasks are deleted automatically by MySQL. Timetable slots have ON DELETE SET NULL, so their subject_id becomes NULL but the slots remain
    public boolean delete(int subjectId) throws SQLException {
        String sql = "DELETE FROM subjects WHERE subject_id = ?";

        try (Connection conn = DatabaseConfig.getDatabaseConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, subjectId);
            return ps.executeUpdate() > 0;
        }
    }
    // --------------------------- PRIVATE HELPER — row -> object mapping-----------------------------
    // converts the current row of a resultset to a subject obj
    private Subject mapRowToSubject(ResultSet rs) throws SQLException {
        return new Subject(
                rs.getInt("subject_id"),
                rs.getInt("student_id"),
                rs.getString("subject_name"),
                rs.getString("instructor_name"),
                rs.getInt("credit_hours")
        );
    }
}
