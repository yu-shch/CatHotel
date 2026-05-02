FROM eclipse-temurin:17-jdk

WORKDIR /app

COPY . .

RUN apt-get update && apt-get install -y maven
RUN mvn clean package -DskipTests

EXPOSE 10000

CMD ["java", "-Dspring.profiles.active=demo", "-jar", "target/orange-project-0.0.1-SNAPSHOT.jar"]