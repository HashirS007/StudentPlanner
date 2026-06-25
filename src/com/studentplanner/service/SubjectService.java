package com.studentplanner.service;

import com.studentplanner.dao.SubjectDAO;
import com.studentplanner.model.Subject;
import com.studentplanner.service.exception.NotFoundException;
import com.studentplanner.service.exception.ValidationException;
import com.studentplanner.util.Session;

import java.sql.SQLException;
import java.util.List;

/*
 Service for managing subjects belonging to the current student.
 Responsibilities:
 - Validate user input (non-empty name, credit hours in range, etc.)
 - Enforce per-row authorization: a student can only see/edit/delete
 their OWN subjects, never anyone else's.
- Coordinate calls to SubjectDAO.
 Does NOT:
  - Run SQL (that's SubjectDAO's job).
  - Show dialogs or build UI (that's SubjectView's job).
  - Authenticate users (that's AuthService's job)
 All "current student" lookups go through Session.getInstance(), so this
 class never has to take studentId as a parameter — keeping its API small.
 */
public class SubjectService {
    private static final int MIN_CREDIT_HOURS = 1;
    private static final int MAX_CREDIT_HOURS = 6;
    private static final int MAX_SUBJECT_NAME_LENGTH = 100;
    private static final int MAX_INSTRUCTOR_NAME_LENGTH = 100;

    // Composition: SubjectService HAS-A SubjectDAO.
    private final SubjectDAO subjectDAO;

    public SubjectService() {
        this.subjectDAO = new SubjectDAO();
    }

    /*
        Adds a new subject for the currently logged-in student.
     */
    public Subject addSubject(String subjectName, String instructorName, int creditHours)
            throws ValidationException, SQLException {

        subjectName    = subjectName    == null ? "" : subjectName.trim();
        instructorName = instructorName == null ? "" : instructorName.trim();

        validateSubjectName(subjectName);
        validateInstructorName(instructorName);
        validateCreditHours(creditHours);

        int currentStudentId = Session.getInstance().getCurrentStudentId();

        // Empty string for instructor -> store as null in DB (cleaner data)
        String instructorOrNull = instructorName.isEmpty() ? null : instructorName;

        Subject subject = new Subject(currentStudentId, subjectName, instructorOrNull, creditHours);
        subjectDAO.insertSubject(subject);   // fills in subjectId via RETURN_GENERATED_KEYS
        return subject;
    }

    /*
    Returns all subjects belonging to the current student.
     */
    public List<Subject> getAllSubjectsForCurrentStudent() throws SQLException {
        int currentStudentId = Session.getInstance().getCurrentStudentId();
        return subjectDAO.findAllByStudentId(currentStudentId);
    }

    /*
     Returns a single subject by id, but ONLY if it belongs to the current student.
     */
    public Subject getSubject(int subjectId) throws NotFoundException, SQLException {
        Subject subject = subjectDAO.findById(subjectId);
        int currentStudentId = Session.getInstance().getCurrentStudentId();
        if (subject == null || subject.getStudentId() != currentStudentId) {
            throw new NotFoundException("Subject not found.");
        }
        return subject;
    }

    /*
     Updates an existing subject
     */
    public Subject updateSubject(int subjectId, String subjectName,
                                 String instructorName, int creditHours)
            throws ValidationException, NotFoundException, SQLException {

        subjectName    = subjectName    == null ? "" : subjectName.trim();
        instructorName = instructorName == null ? "" : instructorName.trim();

        validateSubjectName(subjectName);
        validateInstructorName(instructorName);
        validateCreditHours(creditHours);

        // Authorization check: load it first so we can verify ownership.
        Subject existing = getSubject(subjectId);

        existing.setSubjectName(subjectName);
        existing.setInstructorName(instructorName.isEmpty() ? null : instructorName);
        existing.setCreditHours(creditHours);

        boolean updated = subjectDAO.update(existing);
        if (!updated) {
            throw new NotFoundException("Subject not found.");
        }
        return existing;
    }


    /*
     Deletes a subject
     The DB will cascade-delete related tasks automatically.
     */
    public void deleteSubject(int subjectId) throws NotFoundException, SQLException {
        // Authorization check via getSubject(): throws if not theirs / not found.
        getSubject(subjectId);

        boolean deleted = subjectDAO.delete(subjectId);
        if (!deleted) {
            throw new NotFoundException("Subject not found.");
        }
    }

    private void validateSubjectName(String name) throws ValidationException {
        if (name.isEmpty()) {
            throw new ValidationException("Subject name cannot be empty.");
        }
        if (name.length() > MAX_SUBJECT_NAME_LENGTH) {
            throw new ValidationException(
                    "Subject name must be at most " + MAX_SUBJECT_NAME_LENGTH + " characters.");
        }
    }

    /*
     Instructor name is optional empty string is allowed (will be stored
     as NULL). We only validate the length for non-empty values.
     */
    private void validateInstructorName(String name) throws ValidationException {
        if (name.length() > MAX_INSTRUCTOR_NAME_LENGTH) {
            throw new ValidationException(
                    "Instructor name must be at most " + MAX_INSTRUCTOR_NAME_LENGTH + " characters.");
        }
    }

    private void validateCreditHours(int creditHours) throws ValidationException {
        if (creditHours < MIN_CREDIT_HOURS || creditHours > MAX_CREDIT_HOURS) {
            throw new ValidationException(
                    "Credit hours must be between " + MIN_CREDIT_HOURS +
                            " and " + MAX_CREDIT_HOURS + ".");
        }
    }
}