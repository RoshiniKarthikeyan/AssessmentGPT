# CodeAssess AI

A complete minimum demo of an AI-assisted Java coding assessment platform built with React, Spring Boot, JWT authentication, H2/MySQL support, Apache POI, and an optional OpenAI integration.

## Demo credentials

- Admin: `admin` / `admin123`
- Candidate: `candidate` / `candidate123`

## Start locally

### Frontend

```powershell
cd frontend
npm install
npm run dev
```

Open http://127.0.0.1:5173.

### Backend

Requires Java 17 and Maven.

```powershell
$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-17.0.21.10002-hotspot"
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
cd backend
.\mvnw.cmd spring-boot:run
```

The API runs at http://localhost:8080. The default H2 database is intended for local demo use and is available at http://localhost:8080/h2-console.

## MySQL

Create the database and user:

```sql
CREATE DATABASE assessment CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'assessment'@'%' IDENTIFIED BY 'assessment';
GRANT ALL PRIVILEGES ON assessment.* TO 'assessment'@'%';
```

Then start with a MySQL profile:

```powershell
$env:SPRING_PROFILES_ACTIVE="mysql"
$env:SPRING_DATASOURCE_URL="jdbc:mysql://localhost:3306/assessment"
$env:SPRING_DATASOURCE_USERNAME="assessment"
$env:SPRING_DATASOURCE_PASSWORD="assessment"
```

The schema reference is in `database/schema.sql`.

## OpenAI

Set `OPENAI_API_KEY` before starting the backend:

```powershell
$env:OPENAI_API_KEY="your-key"
```

If the variable is missing, all AI endpoints remain functional in clearly labeled deterministic **DEMO AI** mode.

## Docker execution limitation

Docker is not required to run the demo UI. The execution endpoint is explicitly labeled as **DEMO EXECUTION** and uses deterministic test simulation because Docker is unavailable in this environment. It does not execute candidate code in the Spring JVM or pretend to run hidden tests. Production Docker execution should use `docker run --network none --read-only --memory=256m --cpus=1 --pids-limit=64 --cap-drop=ALL --security-opt=no-new-privileges --user=1000:1000` with a temporary bind mount.

## Excel export

Log in as admin and open **Export** to download `assessment-report.xlsx`. The workbook includes the candidate summary and five workbook sheets; the current demo populates summary data and creates the remaining sheet containers.
