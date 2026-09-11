# Stage 1: Build application with Maven Wrapper
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

# Copy Maven Wrapper and POM first to leverage Docker layer caching
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN sed -i 's/\r$//' ./mvnw && chmod +x ./mvnw
RUN ./mvnw dependency:go-offline -B || true

# Copy source code and build executable jar
COPY src/ ./src/
RUN ./mvnw clean package -DskipTests -B

# Stage 2: Minimal Java 21 JRE runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Run as non-root user
RUN addgroup -S flowforge && adduser -S flowforge -G flowforge
USER flowforge

# Copy compiled JAR from builder stage
COPY --from=builder /app/target/*.jar app.jar

ENV PORT=8081
EXPOSE 8081

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
