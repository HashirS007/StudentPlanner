package com.studentplanner.util;

import com.studentplanner.config.DatabaseConfig;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

public class DatabaseInitializer {

    public static void initializeDatabase() { // The main setup method responsible for calling the other methods
        createDatabase();
        createTables();
        createIndexes();
    }

    private static void createDatabase() { //creates the db if it doesn't already exist
        String sql = "create database if not exists " + DatabaseConfig.DATABASE_NAME;

        try ( /*purpose of () is that it is a try with resource block,
              "resource" is an object that needs to be properly closed or released after you are done using it
              Any object declared in these automatically closes after the try block finishes*/
                Connection connection = DatabaseConfig.getServerConnection();
                Statement statement = connection.createStatement()
        ) {
            statement.executeUpdate(sql);
            System.out.println("Database checked/created successfully.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    // create the tables
    private static void createTables() {
        String studentsTable = """
                create table if not exists students (
                    student_id int auto_increment primary key,
                    full_name varchar(100) not null,
                    email varchar(100) not null unique,
                    password_hash varchar(64) not null,
                    password_salt varchar(32) not null
                )
                """;

        String subjectsTable = """
                create table if not exists subjects (
                    subject_id int auto_increment primary key,
                    student_id int not null,
                    subject_name varchar(100) not null,
                    instructor_name varchar(100),
                    credit_hours int not null,
                    foreign key (student_id) references students(student_id)
                    on delete cascade
                )
                """;

        String tasksTable = """
                create table if not exists tasks (
                    task_id int auto_increment primary key,
                    subject_id int not null,
                    title varchar(150) not null,
                    description varchar(255),
                    task_type varchar(50) not null,
                    due_date date not null,
                    priority varchar(20) not null,
                    status varchar(20) not null,
                    marks_weightage decimal(5,2),
                    foreign key (subject_id) references subjects(subject_id)
                    on delete cascade
                )
                """;

        String timetableTable = """
                create table if not exists timetable_slots (
                    slot_id int auto_increment primary key,
                    student_id int not null,
                    subject_id int,
                    day_of_week varchar(20) not null,
                    start_time time not null,
                    end_time time not null,
                    activity_type varchar(50) not null,
                    foreign key (student_id) references students(student_id)
                    on delete cascade,
                    foreign key (subject_id) references subjects(subject_id)
                    on delete set null
                )
                """;
        String studySessionsTable = """
                create table if not exists study_sessions (
                    session_id int auto_increment primary key,
                    student_id int not null,
                    subject_id int,
                    task_id int,
                    started_at datetime not null,
                    ended_at datetime not null,
                    duration_seconds int not null,
                    completed boolean not null default false,
                    foreign key (student_id) references students(student_id)
                    on delete cascade,
                    foreign key (subject_id) references subjects(subject_id)
                    on delete set null,
                    foreign key (task_id) references tasks(task_id)
                    on delete set null
                )
        """;

        try (
                Connection connection = DatabaseConfig.getDatabaseConnection();
                Statement statement = connection.createStatement()
        ) {
            statement.executeUpdate(studentsTable);
            statement.executeUpdate(subjectsTable);
            statement.executeUpdate(tasksTable);
            statement.executeUpdate(timetableTable);
            statement.executeUpdate(studySessionsTable);
            System.out.println("Tables checked/created successfully.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void createIndexes() { //Adds indexes on columns we will search or join often.
        try (Connection connection = DatabaseConfig.getDatabaseConnection()) {
            createIndexIfMissing(
                    connection,
                    "subjects",
                    "idx_subjects_student_id",
                    "create index idx_subjects_student_id on subjects(student_id)"
            );

            createIndexIfMissing(
                    connection,
                    "tasks",
                    "idx_tasks_subject_id",
                    "create index idx_tasks_subject_id on tasks(subject_id)"
            );

            createIndexIfMissing(
                    connection,
                    "tasks",
                    "idx_tasks_due_date",
                    "create index idx_tasks_due_date on tasks(due_date)"
            );

            createIndexIfMissing(
                    connection,
                    "tasks",
                    "idx_tasks_status",
                    "create index idx_tasks_status on tasks(status)"
            );

            createIndexIfMissing(
                    connection,
                    "timetable_slots",
                    "idx_timetable_student_id",
                    "create index idx_timetable_student_id on timetable_slots(student_id)"
            );

            createIndexIfMissing(
                    connection,
                    "timetable_slots",
                    "idx_timetable_day_of_week",
                    "create index idx_timetable_day_of_week on timetable_slots(day_of_week)"
            );

            createIndexIfMissing(
                    connection,
                    "study_sessions",
                    "idx_sessions_student_started",
                    "create index idx_sessions_student_started on study_sessions(student_id, started_at)"
            );

            System.out.println("Indexes checked/created successfully.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void createIndexIfMissing(Connection connection, String tableName, String indexName, String createSql) {
        try {
            if (!indexExists(connection, tableName, indexName)) {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate(createSql);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static boolean indexExists(Connection connection, String tableName, String indexName) {
        try {
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet resultSet = metaData.getIndexInfo(null, null, tableName, false, false)) {
                while (resultSet.next()) {
                    String existingIndexName = resultSet.getString("INDEX_NAME");
                    if (existingIndexName != null && existingIndexName.equalsIgnoreCase(indexName)) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
}