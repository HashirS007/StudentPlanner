package com.studentplanner.model;

import java.time.DayOfWeek;
import java.time.LocalTime;

/*
 Model class representing one row in the 'timetable_slots' table.
 Each slot belongs to a Student. Optionally it may also be linked to a
 Subject `subjectId` is nullable in the DB (foreign key with ON DELETE
 SET NULL), modeled here as a Java Integer so we can carry null.
 Note the use of Integer (boxed) instead of int (primitive) for subjectId.
 Primitive `int` cannot be null but the DB column can be. Using the
 wrapper class lets us faithfully represent "no subject" without inventing
 a sentinel value like -1.
 */

public class TimetableSlot {

    // ---------- Fields ----------
    private int slotId;
    private int studentId;
    private Integer subjectId;          // NULLABLE
    private DayOfWeek dayOfWeek;
    private LocalTime startTime;
    private LocalTime endTime;
    private ActivityType activityType;

    // ---------- Constructors ----------

    public TimetableSlot() {
    }

    // Constructor for inserting a NEW slot slotId is auto-generated.
    public TimetableSlot(int studentId, Integer subjectId, DayOfWeek dayOfWeek,
                         LocalTime startTime, LocalTime endTime, ActivityType activityType) {
        this.studentId = studentId;
        this.subjectId = subjectId;
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
        this.activityType = activityType;
    }

    // Full constructor used when LOADING an existing slot from the DB.
    public TimetableSlot(int slotId, int studentId, Integer subjectId,
                         DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime,
                         ActivityType activityType) {
        this.slotId = slotId;
        this.studentId = studentId;
        this.subjectId = subjectId;
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
        this.activityType = activityType;
    }

    // ---------- Getters & Setters ----------
    public int getSlotId() { return slotId; }
    public void setSlotId(int slotId) { this.slotId = slotId; }

    public int getStudentId() { return studentId; }
    public void setStudentId(int studentId) { this.studentId = studentId; }

    public Integer getSubjectId() { return subjectId; }
    public void setSubjectId(Integer subjectId) { this.subjectId = subjectId; }

    public DayOfWeek getDayOfWeek() { return dayOfWeek; }
    public void setDayOfWeek(DayOfWeek dayOfWeek) { this.dayOfWeek = dayOfWeek; }

    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }

    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }

    public ActivityType getActivityType() { return activityType; }
    public void setActivityType(ActivityType activityType) { this.activityType = activityType; }

    // ---------- Behavior ----------
    /*
     Returns the duration of this slot in minutes.
     */
    public int getDurationMinutes() {
        if (startTime == null || endTime == null) return 0;
        return (int) java.time.Duration.between(startTime, endTime).toMinutes();
    }

    /*
     Returns true if this slot's time range overlaps with another's
     on the SAME day. Used to detect scheduling conflicts.
     Two ranges overlap iff: this.start < other.end AND other.start < this.end.
     */
    public boolean overlapsWith(TimetableSlot other) {
        if (other == null) return false;
        if (this.dayOfWeek != other.dayOfWeek) return false;
        return this.startTime.isBefore(other.endTime)
                && other.startTime.isBefore(this.endTime);
    }

    @Override
    public String toString() {
        return dayOfWeek + " " + startTime + "–" + endTime + " " + activityType;
    }
}