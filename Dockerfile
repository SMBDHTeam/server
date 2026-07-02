
FROM eclipse-temurin:17-jdk AS builder

WORKDIR /app

COPY gradlew gradlew
COPY gradle gradle
COPY build.gradle build.gradle
COPY settings.gradle settings.gradle


RUN chmod +x ./gradlew


RUN ./gradlew dependencies --no-daemon || true


COPY src src


RUN ./gradlew clean bootJar -x test --no-daemon


FROM eclipse-temurin:17-jre AS runtime


WORKDIR /app


ENV TZ=Asia/Seoul


ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"


COPY --from=builder /app/build/libs/*.jar app.jar


EXPOSE 8080


ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]