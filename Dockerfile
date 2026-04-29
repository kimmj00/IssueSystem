# Java 17 JDK 이미지로 jar 빌드
FROM eclipse-temurin:17-jdk AS build

WORKDIR /app

# Node.js 설치
RUN apt-get update && apt-get install -y curl \
    && curl -fsSL https://deb.nodesource.com/setup_22.x | bash - \
    && apt-get install -y nodejs \
    && rm -rf /var/lib/apt/lists/*

# Gradle 관련 파일 복사
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

# 백엔드 소스 복사
COPY src src

# 프론트 소스 복사
COPY issue-system-frontend issue-system-frontend

# bootJar 생성
RUN chmod +x gradlew && ./gradlew clean bootJar

# 실행용 이미지
FROM eclipse-temurin:17-jre

WORKDIR /app

# 빌드된 jar 복사
COPY --from=build /app/build/libs/*.jar app.jar

# Render PORT로 실행
CMD ["sh", "-c", "java -Dserver.port=${PORT:-8080} -jar app.jar"]