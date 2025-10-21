# Используем официальный образ Maven с JDK 17
FROM maven:3.8.6-jdk-17 AS build
COPY . /app
WORKDIR /app
# Собираем проект, пропуская тесты (чтобы ускорить сборку)
RUN mvn clean package -DskipTests

# Создаем минимальный образ для запуска
FROM openjdk:17-slim
COPY --from=build /app/target/CryptoMonitorBot-1.0-SNAPSHOT.jar /app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app.jar"]