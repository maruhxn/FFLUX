# 운영용 Spring 이미지. GitHub Actions가 빌드해 GHCR에 올린다(로컬 확인은 루트 compose.yml의 --build).
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY . .
RUN ./gradlew :app:bootJar --no-daemon -x test

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /workspace/app/build/libs/app-*.jar app.jar
# main()이 JVM 시간대를 Asia/Seoul로 맞춘다.
ENTRYPOINT ["java", "-jar", "app.jar"]
