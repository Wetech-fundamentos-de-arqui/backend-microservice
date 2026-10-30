# syntax=docker/dockerfile:1
# One image per module: docker build --build-arg MODULE=services/iam-service -t weride/iam-service .
# MODULE is the Maven path of a runnable module (api-gateway or services/<name>-service).
FROM maven:3.9.10-eclipse-temurin-24 AS build
WORKDIR /app
COPY pom.xml ./pom.xml
COPY weride-shared/pom.xml weride-shared/pom.xml
COPY api-gateway/pom.xml api-gateway/pom.xml
COPY services/iam-service/pom.xml services/iam-service/pom.xml
COPY services/profile-service/pom.xml services/profile-service/pom.xml
COPY services/garage-service/pom.xml services/garage-service/pom.xml
COPY services/location-service/pom.xml services/location-service/pom.xml
COPY services/booking-service/pom.xml services/booking-service/pom.xml
COPY services/ride-service/pom.xml services/ride-service/pom.xml
COPY services/plan-service/pom.xml services/plan-service/pom.xml
COPY services/notification-service/pom.xml services/notification-service/pom.xml
COPY weride-shared/src weride-shared/src
ARG MODULE
COPY ${MODULE}/src ${MODULE}/src
RUN --mount=type=cache,target=/root/.m2,sharing=locked mvn -B -q -pl ${MODULE} -am -DskipTests package

FROM eclipse-temurin:24-jre-alpine
ARG MODULE
WORKDIR /app
COPY --from=build /app/${MODULE}/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
