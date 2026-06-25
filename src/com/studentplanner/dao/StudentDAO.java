package com.studentplanner.dao;

import com.studentplanner.config.DatabaseConfig;
import com.studentplanner.model.Student;

import java.sql.Connection;
import java.sql.PreparedStatement; //You write SQL with ? placeholders, then bind values to them by position. The main benefit of this is that it prevents SQL injection
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
// purpose of DAO is to translate between subject obj and sql rows
//The exceptions will be handled in services
/*
 Data Access Object for the 'students' table.
 Responsibilities (single!):
- Insert a new student record (signup)
- Look up a student by email (login)
Does NOT validate, hash passwords, or decide what to do on errors —
those are the AuthService's responsibilities.
 */
public class StudentDAO {
    /*
     Inserts a new student. Expects passwordHash and passwordSalt to already
     be set on the Student object (AuthService computes them).
     On success, fills in the auto-generated studentId.
     */
    public void insert(Student student) throws SQLException {
        String sql = "INSERT INTO students(full_name, email, password_hash, password_salt) "
                + "VALUES(?, ?, ?, ?)";

        try (Connection conn = DatabaseConfig.getDatabaseConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            //  Statement.RETURN_GENERATED_KEYS  tells JDBC after insert hand me back the auto generated id
            ps.setString(1, student.getFullName());
            ps.setString(2, student.getEmail());
            ps.setString(3, student.getPasswordHash());
            ps.setString(4, student.getPasswordSalt());

            ps.executeUpdate();
            // Retrieve the auto-generated student_id MySQL just created, getGeneratedKeys() Returns a ResultSet containing the auto-generated id(s)
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    student.setStudentId(keys.getInt(1));
                }
            }
        }
    }

    /*
     Looks up a student by email. Loads ALL fields including hash & salt
     because AuthService.login() needs them to verify the password.
     Returns null if no student with that email exists.
     */
    public Student findByEmail(String email) throws SQLException {
        String sql = "SELECT student_id, full_name, email, password_hash, password_salt "
                + "FROM students WHERE email = ?";

        try (Connection conn = DatabaseConfig.getDatabaseConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, email);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Student(
                            rs.getInt("student_id"),
                            rs.getString("full_name"),
                            rs.getString("email"),
                            rs.getString("password_hash"),
                            rs.getString("password_salt")
                    );
                }
            }
        }
        return null;
    }

    /*
     Quick existence check used by signup to give a fast "email taken" error
     without loading the full record. Returns true if email exists in DB.
     */
    public boolean existsByEmail(String email) throws SQLException {
        String sql = "SELECT 1 FROM students WHERE email = ? LIMIT 1";

        try (Connection conn = DatabaseConfig.getDatabaseConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, email);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}