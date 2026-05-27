FROM gradle:8.8-jdk21 AS builder
WORKDIR /workspace

# Layer: resolve dependencies before copying source (cache-friendly)
COPY build.gradle.kts settings.gradle.kts ./
RUN gradle dependencies --no-daemon -q 2>&1 | tail -1 || true

COPY src/ src/
RUN gradle bootJar --no-daemon -x spotlessCheck

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /workspace/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
