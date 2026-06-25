# Student Planner

A desktop **study‑planning application** for students, built in **Java** with **JavaFX** and a **MySQL** backend. Students can sign up and log in, manage their subjects and tasks, build a weekly timetable, run focus (study) sessions, and see dashboard statistics and notifications.

> Second‑semester *Object‑Oriented Programming* project.

## Features

- **Authentication** — sign up / log in with salted **SHA‑256** password hashing (per‑user random salt).
- **Subjects** — track subjects, instructors, and credit hours.
- **Tasks** — assignments/exams per subject with due dates, priority, type, status, and weightage.
- **Timetable** — weekly schedule of class/study slots.
- **Focus sessions** — timed study sessions logged against subjects and tasks.
- **Dashboard & notifications** — at‑a‑glance stats and reminders.

## Architecture

The code follows a clean, layered OOP design under `src/com/studentplanner`:

| Layer | Package | Responsibility |
|-------|---------|----------------|
| **Model** | `model` | Plain data objects (`Student`, `Subject`, `Task`, `TimetableSlot`, `StudySession`, enums). |
| **DAO** | `dao` | Database access — raw SQL via JDBC, no business logic. |
| **Service** | `service` | Business rules, validation, and authorization (with typed exceptions). |
| **UI** | `ui` | JavaFX views (`LoginView`, `DashboardView`, `TaskView`, `TimetableView`, `FocusView`, …) + `styles.css`. |
| **Util / Config** | `util`, `config` | Password hashing, session state, DB initialization, and connection config. |

`MainApp` is the JavaFX entry point: it initializes the database, then swaps the scene root between login, signup, and the main app screens.

## Prerequisites

- **JDK 21**
- **Maven**
- **MySQL** running locally on `localhost:3306`

Dependencies (managed by Maven — see `pom.xml`): JavaFX 21 (`javafx-controls`, `javafx-fxml`) and `mysql-connector-j` 8.4.0.

## Configuration

Database credentials are **not** hard‑coded. `DatabaseConfig` reads them from environment variables, falling back to placeholders:

| Variable | Default | Meaning |
|----------|---------|---------|
| `DB_USERNAME` | `root` | MySQL user |
| `DB_PASSWORD` | `YOUR_PASSWORD_HERE` | MySQL password |

Set these before running, e.g. (PowerShell):

```powershell
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "your_real_password"
```

The app creates the `student_planner_db` database, tables, and indexes automatically on first run (`DatabaseInitializer`).

## Running

```bash
mvn clean javafx:run
```

(The `javafx-maven-plugin` is configured with `com.studentplanner.MainApp` as the main class.)
