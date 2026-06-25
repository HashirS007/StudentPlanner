package com.studentplanner.dao;

import com.studentplanner.config.DatabaseConfig;
import com.studentplanner.model.Priority;
import com.studentplanner.model.Task;
import com.studentplanner.model.TaskStatus;
import com.studentplanner.model.TaskType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/*
 Data Access Object for the 'tasks' table.
 Translates between Task objects and SQL rows. Uses JOINs to filter tasks
 by the owning student via the subjects table the tasks table itself
 doesn't have a student_id, so we join through subjects
 */
public class TaskDAO {

    // ------------- CREATE ------------
    public void insert(Task task) throws SQLException {
        String sql = "INSERT INTO tasks(subject_id, title, description, task_type, "
                + "due_date, priority, status, marks_weightage) "
                + "VALUES(?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConfig.getDatabaseConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setInt(1, task.getSubjectId());
            ps.setString(2, task.getTitle());
            // Use setString with null support; setNull would also work.
            ps.setString(3, task.getDescription());
            // Enums stored as their NAME (e.g., "ASSIGNMENT"). Reverse lookup with valueOf().
            ps.setString(4, task.getTaskType().name());
            // setObject auto-converts LocalDate -> SQL DATE
            ps.setObject(5, task.getDueDate());
            ps.setString(6, task.getPriority().name());
            ps.setString(7, task.getStatus().name());

            // Nullable BigDecimal: setBigDecimal handles null safely as SQL NULL,
            // but using setObject with explicit type is the cleanest pattern.
            if (task.getMarksWeightage() == null) {
                ps.setNull(8, Types.DECIMAL);
            } else {
                ps.setBigDecimal(8, task.getMarksWeightage());
            }

            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    task.setTaskId(keys.getInt(1));
                }
            }
        }
    }

    //----------------- READ -----------------------

    /*
      Returns all tasks belonging to the given student, sorted by due date
      (earliest first), then by priority within the same date.
     */
    public List<Task> findAllByStudentId(int studentId) throws SQLException {
        String sql = "SELECT t.task_id, t.subject_id, t.title, t.description, "
                + "t.task_type, t.due_date, t.priority, t.status, t.marks_weightage "
                + "FROM tasks t "
                + "JOIN subjects s ON s.subject_id = t.subject_id "
                + "WHERE s.student_id = ? "
                + "ORDER BY t.due_date ASC, "
                + "  FIELD(t.priority, 'HIGH', 'MEDIUM', 'LOW')";

        List<Task> tasks = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getDatabaseConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, studentId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    tasks.add(mapRowToTask(rs));
                }
            }
        }
        return tasks;
    }

    /*
      Looks up a single task by id. Returns null if not found.
     */
    public Task findById(int taskId) throws SQLException {
        String sql = "SELECT task_id, subject_id, title, description, task_type, "
                + "due_date, priority, status, marks_weightage "
                + "FROM tasks WHERE task_id = ?";

        try (Connection conn = DatabaseConfig.getDatabaseConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, taskId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRowToTask(rs);
                }
            }
        }
        return null;
    }

    // -------------------- UPDATE ---------------------

    public boolean update(Task task) throws SQLException {
        String sql = "UPDATE tasks SET subject_id = ?, title = ?, description = ?, "
                + "task_type = ?, due_date = ?, priority = ?, status = ?, "
                + "marks_weightage = ? WHERE task_id = ?";

        try (Connection conn = DatabaseConfig.getDatabaseConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, task.getSubjectId());
            ps.setString(2, task.getTitle());
            ps.setString(3, task.getDescription());
            ps.setString(4, task.getTaskType().name());
            ps.setObject(5, task.getDueDate());
            ps.setString(6, task.getPriority().name());
            ps.setString(7, task.getStatus().name());
            if (task.getMarksWeightage() == null) {
                ps.setNull(8, Types.DECIMAL);
            } else {
                ps.setBigDecimal(8, task.getMarksWeightage());
            }
            ps.setInt(9, task.getTaskId());

            return ps.executeUpdate() > 0;
        }
    }

    // ----------------------- DELETE ----------------------

    public boolean delete(int taskId) throws SQLException {
        String sql = "DELETE FROM tasks WHERE task_id = ?";

        try (Connection conn = DatabaseConfig.getDatabaseConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, taskId);
            return ps.executeUpdate() > 0;
        }
    }

    // PRIVATE HELPER
    /*
     Converts the CURRENT row of a ResultSet into a Task object.
     Handles the enum parsing (string -> Priority/TaskStatus/TaskType) and
     the LocalDate / BigDecimal column conversions.
     */
    private Task mapRowToTask(ResultSet rs) throws SQLException {
        return new Task(
                rs.getInt("task_id"),
                rs.getInt("subject_id"),
                rs.getString("title"),
                rs.getString("description"),
                TaskType.valueOf(rs.getString("task_type")),
                rs.getObject("due_date", LocalDate.class),
                Priority.valueOf(rs.getString("priority")),
                TaskStatus.valueOf(rs.getString("status")),
                rs.getBigDecimal("marks_weightage")   // returns null if column is NULL
        );
    }
}