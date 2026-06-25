package com.studentplanner.util;

import com.studentplanner.model.Student;

/*
 Holds information about the currently active user session.
 Implemented as a Singleton: exactly ONE Session object exists for the whole app.
 Any layer can read the current student without having to pass it around as a
 parameter in every method call.
 Why Singleton and not all-static?
 - Easier to extend later (e.g., support multiple profiles).
 - It's a named design pattern from the "Gang of Four" book — demonstrates
 awareness of OOP design patterns.
 */
public class Session {

    //Singleton plumbing
    // The one and only instance. 'static' = belongs to the class, not any object.
    private static Session instance;
    // Private constructor: no other class can do `new Session()`.
    private Session() {
    }
    /*
     Global access point. Creates the instance on first call,
     returns the existing one on subsequent calls.
     */
    public static Session getInstance() {
        if (instance == null) {
            instance = new Session();
        }
        return instance;
    }

    //Session state
    // The student currently using the app. May be null before login completes.
    private Student currentStudent;

    public Student getCurrentStudent() {
        return currentStudent;
    }

    public void setCurrentStudent(Student currentStudent) {
        this.currentStudent = currentStudent;
    }

    /*
     Convenience method: returns the id of the current student.
     Throws IllegalStateException if no student is logged in — this signals
     a programmer error (someone forgot to call setCurrentStudent on startup).
     */
    public int getCurrentStudentId() {
        if (currentStudent == null) {
            throw new IllegalStateException(
                    "No current student set. Call Session.getInstance().setCurrentStudent() first.");
        }
        return currentStudent.getStudentId();
    }
}