# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace/mall-backend
COPY . .
RUN mvn -pl mall-config -am -DskipTests package -q

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /workspace/mall-backend/mall-config/target/*.jar /app/app.jar
EXPOSE 8888
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
