# 政策合规智能体 MVP · 单体应用镜像（多阶段构建：JDK21编译 → JRE21运行）
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY rule-module rule-module
COPY audit-module audit-module
COPY remediation-module remediation-module
COPY policy-module policy-module
COPY agent-module agent-module
COPY llm-module llm-module
COPY app app
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/app/target/app-1.0.0-M1.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
