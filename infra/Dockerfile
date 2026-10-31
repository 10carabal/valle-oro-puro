# Build multi-stage del monorepo Maven → imagen JRE por servicio.
# El servicio se elige con --build-arg MODULE=<auth|catalog|sales>-service (ver docker-compose.yml)
FROM maven:3.9-eclipse-temurin-21 AS build
ARG MODULE
WORKDIR /app
COPY pom.xml ./
COPY backend ./backend
RUN mvn -B -pl backend/${MODULE} -am package -DskipTests

FROM eclipse-temurin:21-jre
ARG MODULE
WORKDIR /app
COPY --from=build /app/backend/${MODULE}/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
