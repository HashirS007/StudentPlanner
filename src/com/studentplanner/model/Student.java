package com.studentplanner.model;

/*
  Model class representing one row in the 'students' table.
  Mirrors columns: student_id, full_name, email, password_hash, password_salt.
  Note that this model carries the password HASH, not the plaintext password.
  Plaintext is only seen briefly inside AuthService and is never stored on
  a model object.
 */
public class Student {
    // ---------- Attributes ----------
    private int studentId;
    private String fullName;
    private String email;
    private String passwordHash;
    private String passwordSalt;
    // ---------- Constructors ----------
    // Empty constructor useful when building gradually from form input.
    public Student() {
    }
    //Constructor for SIGNUP no id yet (MySQL generates it on insert),but hash and salt are already computed by AuthService.
    public Student(String fullName, String email, String passwordHash, String passwordSalt) {
        this.fullName = fullName;
        this.email = email;
        this.passwordHash = passwordHash;
        this.passwordSalt = passwordSalt;
    }
    // Full constructor — used when LOADING an existing student from the DB.
    public Student(int studentId, String fullName, String email,
                   String passwordHash, String passwordSalt) {
        this.studentId = studentId;
        this.fullName = fullName;
        this.email = email;
        this.passwordHash = passwordHash;
        this.passwordSalt = passwordSalt;
    }

    // ---------- Getters & Setters ----------
    public int getStudentId() { return studentId; }
    public void setStudentId(int studentId) { this.studentId = studentId; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getPasswordSalt() { return passwordSalt; }
    public void setPasswordSalt(String passwordSalt) { this.passwordSalt = passwordSalt; }

    @Override
    public String toString() {
        return fullName + " (" + email + ")";
    }
}