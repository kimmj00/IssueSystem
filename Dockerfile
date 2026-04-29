# 1단계: Gradle로 Spring Boot jar 빌드
FROM eclipse-temurin:17-jdk AS build

WORKDIR /app

# Gradle 관련 파일 복사
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

# 소스 복사
COPY src src

# gradlew 실행 권한 부여 후 bootJar 생성
RUN chmod +x gradlew && ./gradlew clean bootJar

# 2단계: 실제 실행용 이미지
FROM eclipse-temurin:17-jre

WORKDIR /app

# 빌드된 jar 복사
COPY --from=build /app/build/libs/*.jar app.jar

# Render가 주는 PORT 환경변수를 Spring Boot가 사용
ENV JAVA_OPTS=""

# 애플리케이션 실행
CMD ["sh", "-c", "java $JAVA_OPTS -Dserver.port=${PORT:-8080} -jar app.jar"]