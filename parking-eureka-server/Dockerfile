FROM gradle:8.10.2-jdk17 AS builder
WORKDIR /app
COPY . .
RUN bash -lc 'for i in 1 2 3; do gradle --no-daemon -Dhttps.protocols=TLSv1.2 bootJar && exit 0; echo "Попытка ${i} сборки не удалась, повторяем..."; sleep 5; done; exit 1'

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
EXPOSE 8761
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
