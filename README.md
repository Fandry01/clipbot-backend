# Clipbot Backend

This project is a Spring Boot service that requires Java 24 preview features for compilation.

## Prerequisites

- Java Development Kit (JDK) 24.
- Docker (optional, for running dependencies like PostgreSQL).

## Running the Tests

Use the provided Maven Wrapper so you do not need a globally installed Maven:

```bash
./mvnw test
```

If this is the first time you are building the project on a machine that does not have the Maven wrapper dependencies cached yet, Maven will attempt to download all dependencies from Maven Central. Make sure the machine has outbound internet access. Should you run into transient HTTP 403 or similar download errors, re-run the command after a short delay.

If you already have the dependencies downloaded locally, you can run the tests in offline mode:

```bash
./mvnw -o test
```

## Running the Application

To start the application locally, enable preview features when launching the JVM:

```bash
./mvnw spring-boot:run -Dspring-boot.run.jvmArguments="--enable-preview"
```

Alternatively, you can build the jar and run it manually:

```bash
./mvnw clean package
java --enable-preview -jar target/clipbot-backend-0.0.1-SNAPSHOT.jar
```

## IDE Setup

When importing the project into an IDE, ensure the compiler is configured for Java 24 and preview features are enabled so that the source compiles correctly.
