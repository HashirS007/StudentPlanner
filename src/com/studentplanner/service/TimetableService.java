package com.studentplanner.service;

import com.studentplanner.dao.SubjectDAO;
import com.studentplanner.dao.TimetableDAO;
import com.studentplanner.model.ActivityType;
import com.studentplanner.model.Subject;
import com.studentplanner.model.TimetableSlot;
import com.studentplanner.service.exception.NotFoundException;
import com.studentplanner.service.exception.ValidationException;
import com.studentplanner.util.Session;

import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/*
 Service for managing timetable slots belonging to the current student.
 Authorization model:
 - Slots are filtered by current student via Session.
 - If a slot references a Subject, that subject must also belong to the
   current student (transitive ownership check, like TaskService).
 Validation rules:
 - Day, start, end, activity type must be present.
 - End time must be strictly AFTER start time.
 - Optional schedule-conflict detection: callers can use detectConflict()
   to warn the user before adding/editing.
 */
public class TimetableService {

    private final TimetableDAO timetableDAO;
    private final SubjectDAO subjectDAO;

    public TimetableService() {
        this.timetableDAO = new TimetableDAO();
        this.subjectDAO = new SubjectDAO();
    }

    // CREATE
    public TimetableSlot addSlot(Integer subjectId, DayOfWeek dayOfWeek,
                                 LocalTime startTime, LocalTime endTime,
                                 ActivityType activityType)
            throws ValidationException, NotFoundException, SQLException {

        validateDay(dayOfWeek);
        validateTimes(startTime, endTime);
        validateActivityType(activityType);

        // Subject is optional, but if provided it must belong to current user
        if (subjectId != null) {
            requireOwnedSubject(subjectId);
        }

        int currentStudentId = Session.getInstance().getCurrentStudentId();
        TimetableSlot slot = new TimetableSlot(
                currentStudentId, subjectId, dayOfWeek, startTime, endTime, activityType);
        timetableDAO.insert(slot);
        return slot;
    }

    // READ
    public List<TimetableSlot> getAllSlotsForCurrentStudent() throws SQLException {
        int currentStudentId = Session.getInstance().getCurrentStudentId();
        return timetableDAO.findAllByStudentId(currentStudentId);
    }

    public TimetableSlot getSlot(int slotId) throws NotFoundException, SQLException {
        TimetableSlot slot = timetableDAO.findById(slotId);
        int currentStudentId = Session.getInstance().getCurrentStudentId();
        if (slot == null || slot.getStudentId() != currentStudentId) {
            throw new NotFoundException("Timetable slot not found.");
        }
        return slot;
    }

    /*
     Returns the FIRST existing slot that overlaps with the proposed time
     range, or empty if there's no conflict. The View calls this BEFORE
     adding/editing to warn the user about clashes
     ignoreSlotId the slot being edited, so it doesn't count as
     conflicting with itself; pass null when adding
     */
    public Optional<TimetableSlot> detectConflict(DayOfWeek day, LocalTime start,
                                                  LocalTime end, Integer ignoreSlotId)
            throws SQLException {
        // Build a "candidate" slot to use overlapsWith() against existing ones
        TimetableSlot candidate = new TimetableSlot(
                0, null, day, start, end, ActivityType.OTHER);

        for (TimetableSlot existing : getAllSlotsForCurrentStudent()) {
            if (ignoreSlotId != null && existing.getSlotId() == ignoreSlotId) continue;
            if (candidate.overlapsWith(existing)) return Optional.of(existing);
        }
        return Optional.empty();
    }

    // UPDATE
    public TimetableSlot updateSlot(int slotId, Integer subjectId, DayOfWeek dayOfWeek,
                                    LocalTime startTime, LocalTime endTime,
                                    ActivityType activityType)
            throws ValidationException, NotFoundException, SQLException {

        validateDay(dayOfWeek);
        validateTimes(startTime, endTime);
        validateActivityType(activityType);

        TimetableSlot existing = getSlot(slotId);   // ownership check
        if (subjectId != null) {
            requireOwnedSubject(subjectId);
        }

        existing.setSubjectId(subjectId);
        existing.setDayOfWeek(dayOfWeek);
        existing.setStartTime(startTime);
        existing.setEndTime(endTime);
        existing.setActivityType(activityType);

        boolean updated = timetableDAO.update(existing);
        if (!updated) {
            throw new NotFoundException("Timetable slot not found.");
        }
        return existing;
    }

    // DELETE
    public void deleteSlot(int slotId) throws NotFoundException, SQLException {
        getSlot(slotId);   // ownership check
        boolean deleted = timetableDAO.delete(slotId);
        if (!deleted) {
            throw new NotFoundException("Timetable slot not found.");
        }
    }

    // PRIVATE HELPERS
    private void requireOwnedSubject(int subjectId) throws NotFoundException, SQLException {
        Subject subject = subjectDAO.findById(subjectId);
        int currentStudentId = Session.getInstance().getCurrentStudentId();
        if (subject == null || subject.getStudentId() != currentStudentId) {
            throw new NotFoundException("Subject not found.");
        }
    }

    private void validateDay(DayOfWeek day) throws ValidationException {
        if (day == null) throw new ValidationException("Please choose a day.");
    }

    private void validateTimes(LocalTime start, LocalTime end) throws ValidationException {
        if (start == null) throw new ValidationException("Please choose a start time.");
        if (end == null)   throw new ValidationException("Please choose an end time.");
        if (!end.isAfter(start)) {
            throw new ValidationException("End time must be after start time.");
        }
    }

    private void validateActivityType(ActivityType type) throws ValidationException {
        if (type == null) throw new ValidationException("Please choose an activity type.");
    }
}