# ===================================================
# Stage 1: Build & Package the Application (Builder)
# ===================================================
FROM maven:3.9.6-eclipse-temurin-17-alpine AS builder

WORKDIR /app

# Copy pom.xml and download dependencies first (Leverages Docker Layer Caching!)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and package JAR (skipping unit tests during packaging)
COPY src ./src
RUN mvn clean package -DskipTests

# ===================================================
# Stage 2: Ultra-Lightweight Production Runtime
# ===================================================
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Security Best Practice: Create a non-root system user
RUN addgroup -S flashpass && adduser -S flashpass -G flashpass
USER flashpass

# Copy ONLY the compiled executable JAR from Stage 1 (Builder)
COPY --from=builder /app/target/*.jar app.jar

# Expose HTTP port
EXPOSE 8080

# Configure memory-friendly JVM flags for container environments
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
