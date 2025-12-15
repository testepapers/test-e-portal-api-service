##
## Build stage
##
FROM eclipse-temurin:21-jdk AS build

WORKDIR /workspace

# Copy Gradle wrapper + build scripts first (better layer caching)
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle

# Download dependencies (cached layer)
RUN ./gradlew --no-daemon -v

# Copy source
COPY src ./src

# Build boot jar
RUN ./gradlew --no-daemon clean bootJar

##
## Runtime stage
##
FROM eclipse-temurin:21-jre

WORKDIR /app

# Spring Boot listens on 8080 by default
EXPOSE 8080

COPY --from=build /workspace/build/libs/*-SNAPSHOT.jar /app/app.jar

# Optional: override profile at runtime with: -e SPRING_PROFILES_ACTIVE=prod
ENTRYPOINT ["java","-jar","/app/app.jar"]


