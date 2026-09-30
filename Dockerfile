FROM eclipse-temurin:17-jre

WORKDIR /app

# Copy the jar file
COPY build/libs/payflow.jar app.jar

ENV BIND_ADDRESS=0.0.0.0
USER 10001:10001

# Expose port
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
