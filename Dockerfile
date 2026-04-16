FROM gcr.io/distroless/java21-debian12:nonroot
WORKDIR /app
COPY build/libs/app.jar app.jar
USER nonroot
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
