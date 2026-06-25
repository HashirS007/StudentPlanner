package com.studentplanner.model;

public class Subject {
    //attr
    private int subjectId;
    private String subjectName;
    private int creditHours;
    private int studentId; // it is a FK to the students table
    private String instructorName;
    //constructors
    public Subject(){}

    public Subject(int studentId, String subjectName, String instructorName, int creditHours) {
        this.studentId = studentId;
        this.subjectName = subjectName;
        this.instructorName = instructorName;
        this.creditHours = creditHours;
    }

    public Subject(int subjectId, int studentId, String subjectName,
                   String instructorName, int creditHours) {
        this.subjectId = subjectId;
        this.studentId = studentId;
        this.subjectName = subjectName;
        this.instructorName = instructorName;
        this.creditHours = creditHours;
    }
    // getters
    public int getSubjectId() {
        return subjectId;
    }

    public String getSubjectName() {
        return subjectName;
    }

    public int getCreditHours() {
        return creditHours;
    }

    public int getStudentId() {
        return studentId;
    }

    public String getInstructorName() {
        return instructorName;
    }
    //setters
    public void setSubjectId(int subjectId) {
        this.subjectId = subjectId;
    }

    public void setSubjectName(String subjectName) {
        this.subjectName = subjectName;
    }

    public void setCreditHours(int creditHours) {
        this.creditHours = creditHours;
    }

    public void setStudentId(int studentId) {
        this.studentId = studentId;
    }

    public void setInstructorName(String instructorName) {
        this.instructorName = instructorName;
    }


    @Override
    public String toString() {
        return subjectName + " (" + creditHours + " cr)";
    }
}
