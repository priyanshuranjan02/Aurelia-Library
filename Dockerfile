# Build the Spring Boot executable JAR with the repository's Maven wrapper.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY backend/pom.xml backend/pom.xml
COPY backend/.mvn/ backend/.mvn/
COPY backend/mvnw backend/mvnw
RUN chmod +x backend/mvnw
COPY backend/src/ backend/src/
RUN cd backend && ./mvnw -B -DskipTests package

# Keep the runtime image small and run as a non-root user.
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 --create-home appuser
COPY --from=build --chown=appuser:appuser \
  /workspace/backend/target/library-management-system-0.0.1-SNAPSHOT.jar \
  /app/app.jar
USER appuser
EXPOSE 8080
# Render supplies PORT; local Docker defaults to 8080.
ENTRYPOINT ["sh", "-c", "exec java -XX:MaxRAMPercentage=65.0 -XX:+ExitOnOutOfMemoryError -Dserver.port=${PORT:-8080} -jar /app/app.jar"]
