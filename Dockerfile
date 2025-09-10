FROM openjdk:17-jdk-slim

WORKDIR /app

# Copy the jar file
COPY build/libs/*.jar app.jar

# Expose port
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]