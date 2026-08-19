# syntax=docker/dockerfile:1
FROM maven:3.8.8-eclipse-temurin-8 AS build
WORKDIR /workspace/mall-backend
COPY . .
RUN mvn -pl mall-search -am -DskipTests package -q

FROM eclipse-temurin:8-jre-jammy
WORKDIR /app
COPY --from=build /workspace/mall-backend/mall-search/target/*.jar /app/app.jar
EXPOSE 14000
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
