# 1. Frontend build
FROM node:22-alpine AS frontend-build
WORKDIR /app/frontend

COPY frontend/package*.json ./
RUN npm ci

COPY frontend/ ./
RUN npm run build

# 2. Backend build
FROM gradle:8.14-jdk21 AS backend-build
WORKDIR /app

COPY . .
COPY --from=frontend-build /app/src/main/resources/static/ui ./src/main/resources/static/ui

RUN chmod +x gradlew \
    && ./gradlew bootJar -x buildFrontend -x installFrontendDependencies --no-daemon

# 3. Runtime
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=backend-build /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
