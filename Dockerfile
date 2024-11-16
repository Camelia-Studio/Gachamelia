FROM eclipse-temurin:21-alpine AS build
WORKDIR /src
COPY . .

RUN ./gradlew clean shadowJar
FROM eclipse-temurin:21-alpine AS runner
RUN mkdir -p /app
WORKDIR /app
COPY --from=build /src/build/libs/gachamelia.jar /app/gachamelia.jar

CMD ["java", "-jar", "gachamelia.jar"]