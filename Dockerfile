# 1) Build Stage
FROM gradle:8.5-jdk21 AS build
WORKDIR /app

COPY gradlew ./
COPY gradle gradle
COPY build.gradle settings.gradle ./

RUN chmod +x ./gradlew

# 의존성 다운로드
RUN gradle dependencies --no-daemon || true

COPY src src
RUN ./gradlew clean build --no-daemon

# 2) Runtime
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]