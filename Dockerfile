# ============================================
# Stage 1: Builder
# ============================================
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Gradle Wrapper and version catalog copy (caching optimization)
COPY gradlew .
COPY gradle/wrapper gradle/wrapper
COPY gradle/libs.versions.toml gradle/
RUN chmod +x ./gradlew

# Dependency files first (caching optimization)
COPY build.gradle.kts .
COPY settings.gradle.kts .

# Copy all module build files (caching optimization)
COPY rate-limiter-core/build.gradle.kts rate-limiter-core/
COPY rate-limiter-spring-boot-starter/build.gradle.kts rate-limiter-spring-boot-starter/
COPY rate-limiter-app/build.gradle.kts rate-limiter-app/

# Download dependencies (reused on source changes)
RUN ./gradlew dependencies --no-daemon || true

# Copy source code for all modules
COPY rate-limiter-core/src rate-limiter-core/src
COPY rate-limiter-spring-boot-starter/src rate-limiter-spring-boot-starter/src
COPY rate-limiter-app/src rate-limiter-app/src

# Build application
RUN ./gradlew :rate-limiter-app:bootJar --no-daemon -x test

# Extract JAR file (Spring Boot Layered JAR)
RUN java -Djarmode=layertools -jar rate-limiter-app/build/libs/*.jar extract

# ============================================
# Stage 2: Runtime
# ============================================
FROM eclipse-temurin:21-jre-alpine AS runtime

# Security: Create non-root user and install wget for healthcheck
RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup && \
    apk add --no-cache wget

WORKDIR /app

# Copy Layered JAR (in order of change frequency)
COPY --from=builder /app/dependencies/ ./
COPY --from=builder /app/spring-boot-loader/ ./
COPY --from=builder /app/snapshot-dependencies/ ./
COPY --from=builder /app/application/ ./

# Change ownership
RUN chown -R appuser:appgroup /app

# Switch to non-root user
USER appuser

# Expose port
EXPOSE 8080

# Health check configuration
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# JVM options for container support
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 -Djava.security.egd=file:/dev/./urandom"

# Application entry point
# Use exec to ensure Java becomes PID 1 for proper signal handling (SIGTERM)
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
