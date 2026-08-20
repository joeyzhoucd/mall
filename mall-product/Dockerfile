# syntax=docker/dockerfile:1
FROM maven:3.8.8-eclipse-temurin-8 AS build
WORKDIR /workspace/mall-backend
COPY . .
RUN ls -la mall-product/src/main/resources/static/img/index/banner-1.jpg || (echo "DEBUG-FAIL: banner-1.jpg missing right after COPY . ." && exit 1)
RUN mvn -pl mall-product -am -DskipTests package -q
RUN unzip -l mall-product/target/mall-product-0.0.1-SNAPSHOT.jar | grep "static/img/index/banner-1.jpg" || (echo "DEBUG-FAIL: banner-1.jpg missing from built jar after mvn package" && exit 1)

FROM eclipse-temurin:8-jre-jammy
WORKDIR /app
COPY --from=build /workspace/mall-backend/mall-product/target/*.jar /app/app.jar
EXPOSE 15000
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
