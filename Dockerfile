# Build the distribution with the Gradle wrapper, then run it on a slim JRE.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY . .
RUN chmod +x gradlew && ./gradlew installDist --no-daemon -q && mv build/install/*/ /dist

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /dist/lib /app/lib
# Master by default; compose overrides the entrypoint for the secondaries.
ENTRYPOINT ["java", "-cp", "/app/lib/*", "org.replog.MasterApplication"]
