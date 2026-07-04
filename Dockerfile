FROM eclipse-temurin:25-alpine AS build
WORKDIR /src
COPY . .

RUN ./gradlew clean shadowJar
FROM eclipse-temurin:25-alpine AS runner
RUN mkdir -p /app
WORKDIR /app
COPY --from=build /src/build/libs/gachamelia.jar /app/gachamelia.jar

CMD ["java", "--enable-native-access=ALL-UNNAMED", "-jar", "gachamelia.jar"]
