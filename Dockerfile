# Single deployable image: the Angular app is built and packaged inside the jar,
# so one container serves the UI and the API from one origin.

# ---- build the frontend -------------------------------------------------------
# Angular 22 requires ^22.22.3 || ^24.15.0 || >=26.0.0.
FROM node:24.20-alpine AS frontend
WORKDIR /build

# Dependencies first: this layer is reused whenever only source files change.
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

COPY frontend/ ./
RUN npm run build

# ---- build the backend --------------------------------------------------------
FROM maven:3.9-eclipse-temurin-25 AS backend
WORKDIR /build

# Same trick: resolve dependencies before the sources are copied in.
COPY backend/pom.xml ./
RUN mvn -B -q dependency:go-offline

COPY backend/src ./src
# The built UI goes where Spring Boot serves static content from.
COPY --from=frontend /build/dist/frontend/browser ./src/main/resources/static

# Stamped into the jar and printed on start-up, so a running container can be
# matched to a commit. Platforms that expose the SHA can pass it through:
#   docker build --build-arg GIT_COMMIT=$(git rev-parse --short HEAD) .
ARG GIT_COMMIT=unknown
RUN mvn -B -q -DskipTests package -Dgit.commit="$GIT_COMMIT"

# ---- run ----------------------------------------------------------------------
FROM eclipse-temurin:25-jre-alpine AS runtime

# Never run as root.
RUN addgroup -S apparel && adduser -S apparel -G apparel
WORKDIR /app

COPY --from=backend /build/target/*.jar app.jar
RUN chown -R apparel:apparel /app
USER apparel

EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-XX:MaxRAMPercentage=75"

# Compose and orchestrators read this; it is the same probe either way.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=5 \
    CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
