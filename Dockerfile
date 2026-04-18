FROM gradle:8.5-jdk21 AS build

WORKDIR /app

COPY build.gradle settings.gradle gradle.properties ./
COPY gradle ./gradle
COPY gradlew ./gradlew
COPY src ./src
COPY gradle.properties ./gradle.properties

ENV QUARKUS_TEST_ENABLED=false
ENV QUARKUS_TEST_CONTINUOUS_TESTING_ENABLED=false
ENV TESTCONTAINERS_RYUK_DISABLED=true
ENV SKIP_TESTS=true

RUN ./gradlew quarkusBuild --no-daemon -x test -DskipTests=true -Dquarkus.test.enabled=false

FROM eclipse-temurin:21-jre

WORKDIR /work

COPY --from=build /app/build/quarkus-app/quarkus-run.jar /work/
COPY --from=build /app/build/quarkus-app/lib/ /work/lib/
COPY --from=build /app/build/quarkus-app/app/ /work/app/
COPY --from=build /app/build/quarkus-app/quarkus/ /work/quarkus/

EXPOSE 8080

CMD ["java", "-jar", "-XX:+UseContainerSupport", "/work/quarkus-run.jar"]