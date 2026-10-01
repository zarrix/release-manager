FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY build/libs/release-manager-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
