# Spring Boot backend (plan Phase 7). Built from the repo root: the build copies ../policy/policy_table.json.
#   docker build -f backend/Dockerfile -t gajamitra-backend .
# The frontend joins this image in Phase 5 (Node stage).
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY policy/ policy/
COPY backend/.mvn/ backend/.mvn/
COPY backend/mvnw backend/pom.xml backend/
RUN cd backend && chmod +x mvnw && ./mvnw -q -B dependency:go-offline
COPY backend/src/ backend/src/
RUN cd backend && ./mvnw -q -B -DskipTests package && cp target/*.jar /app.jar

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 gajamitra && mkdir -p /data/videos && chown gajamitra /data/videos
USER gajamitra
COPY --from=build /app.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
