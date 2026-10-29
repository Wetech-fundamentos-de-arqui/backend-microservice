# One image per module: docker build --build-arg MODULE=services/iam-service -t weride/iam-service .
# MODULE is the Maven path of a runnable module (api-gateway or services/<name>-service).
FROM maven:3.9.10-eclipse-temurin-24 AS build
ARG MODULE
WORKDIR /app
COPY . .
RUN mvn -B -q -pl ${MODULE} -am -DskipTests package

FROM eclipse-temurin:24-jre-alpine
ARG MODULE
WORKDIR /app
COPY --from=build /app/${MODULE}/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
