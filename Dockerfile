# 1. 빌드 단계 (Build Stage)
FROM eclipse-temurin:17-jdk-jammy AS builder
WORKDIR /app
COPY . .
RUN chmod +x ./gradlew && ./gradlew bootJar -x test

# 2. 실행 단계 (Final Stage)
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# 빌드 단계에서 생성된 .jar 파일 복사
COPY --from=builder /app/build/libs/*.jar app.jar

# 8080 포트 외부에 노출
EXPOSE 8080

# 컨테이너가 시작될 때 명령어 실행
ENTRYPOINT ["java", "-jar", "./app.jar"]
