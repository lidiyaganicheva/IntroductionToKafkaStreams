FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY . .
RUN mvn clean package dependency:copy-dependencies

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/counter-1.0-SNAPSHOT.jar app.jar
COPY --from=build /app/target/dependency/*.jar libs/
CMD ["java", "-cp", "app.jar:libs/*", "RootDomainCounter"]