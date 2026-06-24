FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

COPY . .

ARG GITHUB_USERNAME
ARG GITHUB_TOKEN

RUN chmod +x ./gradlew

RUN ./gradlew bootJar \
    -Pgpr.user=${GITHUB_USERNAME} \
    -Pgpr.key=${GITHUB_TOKEN} \
    --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8090

ENTRYPOINT ["java", "-jar", "app.jar"]
