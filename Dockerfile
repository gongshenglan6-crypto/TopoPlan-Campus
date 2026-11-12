FROM node:22-alpine AS frontend
WORKDIR /build/frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /build/backend
COPY backend/demo1/pom.xml ./
RUN mvn -B -q dependency:go-offline
COPY backend/demo1/src ./src
COPY --from=frontend /build/frontend/dist ./src/main/resources/static
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=backend /build/backend/target/demo1-0.0.1-SNAPSHOT.jar ./app.jar
COPY docs/knowledge/ ./knowledge/
ENV TOPO_SERVER_ADDRESS=0.0.0.0 \
    PORT=8080 \
    TOPO_SESSION_COOKIE_SECURE=true \
    TOPO_UPLOAD_DIR=/data/uploads \
    TOPO_LOG_FILE=/data/logs/topoplan-backend.log \
    TOPO_KNOWLEDGE_SEED_FILES=/app/knowledge/network-basics.md \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=65.0"
RUN mkdir -p /data/uploads /data/logs
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
