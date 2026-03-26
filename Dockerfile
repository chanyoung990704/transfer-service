# Stage 1: Build
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /app
COPY . .
RUN ./gradlew bootJar --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar

# JVM 옵션 및 실행 설정
ENTRYPOINT ["java", "-jar", "app.jar"]
