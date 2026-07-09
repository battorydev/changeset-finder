# Liquibase Changeset Finder

A premium, dark-themed JavaFX desktop application designed to parse, explore, analyze, and manage Liquibase formatted SQL changesets within database migration projects.

---

## 🚀 Overview

In large-scale database schema migration projects using Liquibase, managing multiple SQL scripts across different environments (`DEV`, `UAT`, `PRD`) can quickly become challenging. Keeping track of which changesets run in which contexts, auditing SQL statements, and finding duplicate changeset IDs can be tedious.

**Liquibase Changeset Finder** solves these problems by scanning your SQL files recursively, extracting changeset metadata (author, ID, contexts, and SQL statements), and providing a clean interactive user interface to explore and validate them.

---

## ✨ Features

- **📂 Directory Parsing**: Recursively scans any directory to parse Liquibase-formatted SQL files (recognized by the `--liquibase formatted sql` header).
- **🔍 Interactive Changeset Explorer**: View list of changesets showing their ID, author, and associated context tags. Filter the changesets instantly by context (e.g., `UAT`, `PRD`, `no-context`).
- **⚠️ Duplicate ID Detection**: Automatically flags duplicate changeset IDs across all files. Compare duplicate occurrences side-by-side with their original files, authors, contexts, and SQL statements to resolve conflicts before running Liquibase.
- **🌳 SQL File Tree Browser**: View the original directory layout of your migration files. Select individual files to read their full SQL content, or view all statements combined into a single view.
- **📥 Consolidated Export**: Export the current filtered list of changesets and their SQL content to a single unified `.txt` or `.sql` file for code review or manual execution.
- **🌙 Premium Dark Theme**: Styled with a tailored modern dark mode (custom CSS) featuring smooth gradients, glowing selection states, and environment context tags.

---

## 🛠️ Technology Stack

- **Core**: Java 21
- **GUI Framework**: JavaFX 21.0.2
- **Build System**: Maven
- **Style System**: Custom JavaFX CSS (`styles.css`)

---

## 📂 Project Structure

```text
changeset-finder/
│
├── demo1/                         # Sample Liquibase formatted SQL files
│   ├── sample.sql                 # Sample changeset definitions
│   └── demo1-1/
│       └── dup.sql                # Contains duplicate changeset for testing
│
├── src/
│   └── main/
│       ├── java/
│       │   ├── com/changesetfinder/
│       │   │   ├── App.java              # Main JavaFX GUI application
│       │   │   └── TestParser.java       # CLI Test Parser (prints summary to terminal)
│       │   │
│       │   │   ├── model/
│       │   │   │   └── Changeset.java    # Model representing a Liquibase changeset
│       │   │   │
│       │   │   └── parser/
│       │   │       └── LiquibaseParser.java # Parsing engine for SQL changesets
│       │   │
│       │   └── module-info.java          # Java Module System configuration
│       │
│       └── resources/
│           └── styles.css                # Premium Dark Mode styles for GUI
│
├── pom.xml                        # Maven dependency and build config
└── README.md                      # Project documentation (this file)
```

---

## 📝 Supported Liquibase SQL Format

The tool parses files that strictly follow the Liquibase Formatted SQL guidelines:

```sql
--liquibase formatted sql

--changeset author_name:changeset_id_001 context:DEV,UAT
CREATE TABLE sample_table (
    id INT PRIMARY KEY,
    name VARCHAR(255)
);

--changeset author_name:changeset_id_002 context:PRD
ALTER TABLE sample_table ADD COLUMN description TEXT;
```

---

## 🚦 Getting Started

### Prerequisites
- **Java Development Kit (JDK)**: version 21 or later
- **Maven**: version 3.x or later

### Building the Project
Clone the repository and compile the codebase:
```bash
mvn clean compile
```

### Running the Application

#### 1. Launch the Desktop GUI App
To run the interactive desktop client:
```bash
mvn javafx:run
```
*Once open, click **Select Folder** and choose the `demo1` directory (or your own Liquibase SQL directory) to see it in action!*

#### 2. Run the CLI Test Parser
To run a fast parser demo in your terminal:
```bash
mvn compile exec:java -Dexec.mainClass="com.changesetfinder.TestParser"
```
This CLI tool scans the bundled `demo1` directory and prints parsed statistics along with duplicate warnings directly to standard output.
