# syntax=docker/dockerfile:1

# ---- build ----
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /src
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests \
    && cp target/pm-platform-backend-*.jar /app.jar

# ---- run ----
FROM eclipse-temurin:25-jre
RUN groupadd --system pmp && useradd --system --gid pmp pmp
WORKDIR /app
COPY --from=build /app.jar app.jar
USER pmp
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
