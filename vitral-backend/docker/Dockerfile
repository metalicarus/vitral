# Build stage: compiles the Quarkus server with Maven.
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build
COPY server/pom.xml .
RUN mvn -B -q dependency:go-offline
COPY server/src ./src
RUN mvn -B -q package -DskipTests

# Runtime stage: only the JRE and the built application.
FROM eclipse-temurin:25-jre
# libvips generates thumbnails and previews; the libde265 plugin lets it read HEIC.
RUN apt-get update \
    && apt-get install -y --no-install-recommends libvips-tools libheif-plugin-libde265 \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /build/target/quarkus-app/lib/ ./lib/
COPY --from=build /build/target/quarkus-app/*.jar ./
COPY --from=build /build/target/quarkus-app/app/ ./app/
COPY --from=build /build/target/quarkus-app/quarkus/ ./quarkus/
RUN useradd --system --uid 1001 vitral && mkdir /data && chown vitral /data
USER vitral
# Database, photo library and temporary uploads live in /data; mount a volume there to keep them.
ENV VITRAL_DATA_DIR=/data
VOLUME /data
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "quarkus-run.jar"]