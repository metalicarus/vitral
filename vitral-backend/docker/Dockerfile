# Build stage: compiles the Quarkus server with Maven.
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build
COPY server/pom.xml .
RUN mvn -B -q dependency:go-offline
COPY server/src ./src
RUN mvn -B -q package -DskipTests

# Runtime stage: only the JRE and the built application.
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /build/target/quarkus-app/lib/ ./lib/
COPY --from=build /build/target/quarkus-app/*.jar ./
COPY --from=build /build/target/quarkus-app/app/ ./app/
COPY --from=build /build/target/quarkus-app/quarkus/ ./quarkus/
RUN useradd --system --uid 1001 vitral && mkdir /data && chown vitral /data
USER vitral
# The SQLite database lives in /data; mount a volume there to keep it.
ENV VITRAL_DB_PATH=/data/vitral.db
VOLUME /data
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "quarkus-run.jar"]