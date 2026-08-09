# --- Stage 1: build ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copy only pom.xml first to leverage Docker layer caching for dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Now copy the actual source and build
COPY src/ src/
RUN mvn clean package -DskipTests -B

# --- Stage 2: run ---
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# Run as non-root user (good practice, some platforms require it)
RUN useradd -r -u 1001 appuser
USER appuser

COPY --from=build /app/target/*.jar app.jar

EXPOSE 9090

ENTRYPOINT ["java", "-jar", "app.jar"]