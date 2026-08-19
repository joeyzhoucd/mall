# syntax=docker/dockerfile:1
FROM maven:3.8.8-eclipse-temurin-8 AS build
WORKDIR /workspace/mall-backend
COPY . .
RUN mvn -pl mall-admin -am -DskipTests package -q

FROM eclipse-temurin:8-jre-jammy
WORKDIR /app
COPY --from=build /workspace/mall-backend/mall-admin/target/*.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
