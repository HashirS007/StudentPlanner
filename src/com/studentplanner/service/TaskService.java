package com.studentplanner.service;

import com.studentplanner.dao.SubjectDAO;
import com.studentplanner.dao.TaskDAO;
import com.studentplanner.model.Priority;
import com.studentplanner.model.Subject;
import com.studentplanner.model.Task;
import com.studentplanner.model.TaskStatus;
import com.studentplanner.model.TaskType;
import com.studentplanner.service.exception.NotFoundException;
import com.studentplanner.service.exception.ValidationException;
import com.studentplanner.util.Session;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/*
  Service for managing tasks belonging to the current student.
  Authorization model: a task is "owned" by the student that owns its parent
  subject.
  Composition: TaskService HAS-A TaskDAO and HAS-A SubjectDAO
 */
public class TaskService {

    private static final int MAX_TITLE_LENGTH = 150;
    private static final int MAX_DESCRIPTION_LENGTH = 255;
    private static final BigDecimal MIN_WEIGHTAGE = new BigDecimal("0.00");
    private static final BigDecimal MAX_WEIGHTAGE = new BigDecimal("100.00");

    private final TaskDAO taskDAO;
    private final SubjectDAO subjectDAO;

    public TaskService() {
        this.taskDAO = new TaskDAO();
        this.subjectDAO = new SubjectDAO();
    }


    // ---------------- CREATE --------------------

    public Task addTask(int subjectId, String title, String description,
                        TaskType taskType, LocalDate dueDate,
                        Priority priority, TaskStatus status,
                        BigDecimal marksWeightage)
            throws ValidationException, NotFoundException, SQLException {

        title       = title       == null ? "" : title.trim();
        description = description == null ? "" : description.trim();

        validateTitle(title);
        validateDescription(description);
        validateTaskType(taskType);
        validateDueDate(dueDate);
        validatePriority(priority);
        validateStatus(status);
        validateWeightage(marksWeightage);

        // Authorization check: confirm the subject belongs to the current user.
        // requireOwnedSubject throws NotFoundException if not.
        requireOwnedSubject(subjectId);

        Task task = new Task(
                subjectId, title,
                description.isEmpty() ? null : description,
                taskType, dueDate, priority, status, marksWeightage);
        taskDAO.insert(task);
        return task;
    }

    // ---------------- READ --------------------

    public List<Task> getAllTasksForCurrentStudent() throws SQLException {
        int currentStudentId = Session.getInstance().getCurrentStudentId();
        return taskDAO.findAllByStudentId(currentStudentId);
    }

    /* Returns only tasks that are currently overdue (and not completed). */
    public List<Task> getOverdueTasks() throws SQLException {
        return getAllTasksForCurrentStudent().stream()
                .filter(Task::isOverdue)
                .collect(Collectors.toList());
        /*stream() turns the list into a "pipeline." filter(Task::isOverdue) keeps only elements where the predicate returns true.
        Task::isOverdue is a method reference — t -> t.isOverdue() in shorthand. collect(Collectors.toList()) materializes the result back into a List<Task>
         */
    }

    /* Returns tasks due today or in the next N days (excludes overdue and completed). */
    public List<Task> getTasksDueWithin(int days) throws SQLException {
        LocalDate today = LocalDate.now();
        LocalDate cutoff = today.plusDays(days);

        return getAllTasksForCurrentStudent().stream()
                .filter(t -> t.getStatus() != TaskStatus.COMPLETED)
                .filter(t -> t.getDueDate() != null)
                .filter(t -> !t.getDueDate().isBefore(today))     // due today or later
                .filter(t -> !t.getDueDate().isAfter(cutoff))     // and not past cutoff
                .collect(Collectors.toList());
    }

    /*
     Returns a single task by id, but ONLY if it belongs to the current student.
     Same security pattern as SubjectService.getSubject.
     */
    public Task getTask(int taskId) throws NotFoundException, SQLException {
        Task task = taskDAO.findById(taskId);
        if (task == null) {
            throw new NotFoundException("Task not found.");
        }
        // Verify ownership via the parent subject.
        try {
            requireOwnedSubject(task.getSubjectId());
        } catch (NotFoundException e) {
            throw new NotFoundException("Task not found.");
        }
        return task;
    }

    // ---------------- UPDATE --------------------

    public Task updateTask(int taskId, int subjectId, String title, String description,
                           TaskType taskType, LocalDate dueDate,
                           Priority priority, TaskStatus status,
                           BigDecimal marksWeightage)
            throws ValidationException, NotFoundException, SQLException {

        title       = title       == null ? "" : title.trim();
        description = description == null ? "" : description.trim();

        validateTitle(title);
        validateDescription(description);
        validateTaskType(taskType);
        validateDueDate(dueDate);
        validatePriority(priority);
        validateStatus(status);
        validateWeightage(marksWeightage);

        // Verify ownership of BOTH the existing task AND the (possibly new) subject.
        // The user might be reassigning a task to a different subject and that
        // target subject must also belong to them.
        Task existing = getTask(taskId);
        requireOwnedSubject(subjectId);

        existing.setSubjectId(subjectId);
        existing.setTitle(title);
        existing.setDescription(description.isEmpty() ? null : description);
        existing.setTaskType(taskType);
        existing.setDueDate(dueDate);
        existing.setPriority(priority);
        existing.setStatus(status);
        existing.setMarksWeightage(marksWeightage);

        boolean updated = taskDAO.update(existing);
        if (!updated) {
            throw new NotFoundException("Task not found.");
        }
        return existing;
    }

    // ---------------- DELETE --------------------


    public void deleteTask(int taskId) throws NotFoundException, SQLException {
        getTask(taskId);   // ownership check via shared method

        boolean deleted = taskDAO.delete(taskId);
        if (!deleted) {
            throw new NotFoundException("Task not found.");
        }
    }

    // ---------------- HELPERS --------------------

    /*
     Verifies that the given subjectId exists AND belongs to the current student.
     Throws NotFoundException otherwise. Used as an authorization guard before
     inserting/updating a task.
     */
    private void requireOwnedSubject(int subjectId) throws NotFoundException, SQLException {
        Subject subject = subjectDAO.findById(subjectId);
        int currentStudentId = Session.getInstance().getCurrentStudentId();
        if (subject == null || subject.getStudentId() != currentStudentId) {
            throw new NotFoundException("Subject not found.");
        }
    }

    private void validateTitle(String title) throws ValidationException {
        if (title.isEmpty()) {
            throw new ValidationException("Task title cannot be empty.");
        }
        if (title.length() > MAX_TITLE_LENGTH) {
            throw new ValidationException(
                    "Task title must be at most " + MAX_TITLE_LENGTH + " characters.");
        }
    }

    private void validateDescription(String description) throws ValidationException {
        if (description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new ValidationException(
                    "Description must be at most " + MAX_DESCRIPTION_LENGTH + " characters.");
        }
    }

    private void validateTaskType(TaskType type) throws ValidationException {
        if (type == null) throw new ValidationException("Please choose a task type.");
    }

    private void validateDueDate(LocalDate dueDate) throws ValidationException {
        if (dueDate == null) {
            throw new ValidationException("Please choose a due date.");
        }
    }

    private void validatePriority(Priority priority) throws ValidationException {
        if (priority == null) throw new ValidationException("Please choose a priority.");
    }

    private void validateStatus(TaskStatus status) throws ValidationException {
        if (status == null) throw new ValidationException("Please choose a status.");
    }

    private void validateWeightage(BigDecimal weightage) throws ValidationException {
        if (weightage == null) return;   // optional field
        if (weightage.compareTo(MIN_WEIGHTAGE) < 0 || weightage.compareTo(MAX_WEIGHTAGE) > 0) {
            throw new ValidationException(
                    "Marks weightage must be between 0 and 100.");
        }
    }
}