package com.studentplanner.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConfig {
    public static final String DATABASE_NAME = "student_planner_db"; // The name of the database we want to use

    public static final String SERVER_URL = //connects to the mySQL server without selecting a db.
            "jdbc:mysql://localhost:3306/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";

    public static final String DATABASE_URL = //connects directly to the student planner db
            "jdbc:mysql://localhost:3306/" + DATABASE_NAME +
                    "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";

    // Credentials are read from environment variables so no real password is committed to source control.
    // Set DB_USERNAME and DB_PASSWORD in your environment (or IDE run configuration) before running.
    // The defaults below are placeholders for a local MySQL setup — replace via env vars.
    public static final String USERNAME = System.getenv().getOrDefault("DB_USERNAME", "root");
    public static final String PASSWORD = System.getenv().getOrDefault("DB_PASSWORD", "YOUR_PASSWORD_HERE");

    public static Connection getServerConnection() throws SQLException { // returns a JDBC connection to the MySql server
        return DriverManager.getConnection(SERVER_URL, USERNAME, PASSWORD);
    }

    public static Connection getDatabaseConnection() throws SQLException { // returns a JDBC connection to the MySql db
        return DriverManager.getConnection(DATABASE_URL, USERNAME, PASSWORD);
    }
}