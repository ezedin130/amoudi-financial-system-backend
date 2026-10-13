# =============================================================================
# Stage 1: Build & Package Spring Boot Application
# =============================================================================
FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /build

# Copy Maven wrapper and POM to leverage Docker layer caching for dependencies
COPY .mvn .mvn
COPY mvnw pom.xml ./

# Ensure Maven wrapper is executable
RUN chmod +x mvnw

# Resolve project dependencies in a separate cached layer
RUN ./mvnw dependency:go-offline -B

# Copy full application source code
COPY src ./src

# Compile and package executable Spring Boot fat JAR
RUN ./mvnw clean package -DskipTests -B

# =============================================================================
# Stage 2: Minimal Production Runtime
# =============================================================================
FROM eclipse-temurin:21-jre-jammy AS runner

# Install curl for container health check probes
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Create non-privileged system user and group (UID/GID 10001)
RUN groupadd -r -g 10001 appgroup \
    && useradd -r -u 10001 -g appgroup -d /app -s /sbin/nologin appuser

WORKDIR /app

# Create upload directory and grant full ownership to non-root user
RUN mkdir -p /app/storage/uploads \
    && chown -R appuser:appgroup /app

# Copy the built application JAR from builder stage
COPY --from=builder --chown=appuser:appgroup /build/target/amoudi-financial-*.jar /app/app.jar

# Switch to non-root runtime user
USER appuser:appgroup

# Default runtime configuration
ENV PORT=8080 \
    SPRING_PROFILES_ACTIVE=prod \
    STORAGE_PATH=/app/storage/uploads

# Expose default HTTP port (Render dynamically provides $PORT at runtime)
EXPOSE 8080

# Native Docker container healthcheck
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:${PORT:-8080}/api/v1/health || exit 1

# Execute Spring Boot with container-aware memory allocation and headless AWT
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-Djava.awt.headless=true", "-jar", "/app/app.jar"]
