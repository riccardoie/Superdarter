# ---- Build stage ----
FROM maven:3-eclipse-temurin-25 AS build
WORKDIR /app

# Cache dependencies in their own layer (only re-runs when pom.xml changes)
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy sources, then package (CI runs the test suite separately)
COPY src ./src
RUN mvn package -DskipTests

# ---- Runtime stage ----
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
