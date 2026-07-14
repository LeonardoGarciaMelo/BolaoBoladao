# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml pom.xml
COPY shared-contracts/pom.xml shared-contracts/pom.xml
COPY partidas-service/pom.xml partidas-service/pom.xml
COPY user-service/pom.xml user-service/pom.xml
COPY api-gateway/pom.xml api-gateway/pom.xml
COPY carteira-service/pom.xml carteira-service/pom.xml
RUN mvn -q -pl user-service -am dependency:go-offline || true
COPY user-service/src user-service/src
RUN mvn -q -pl user-service -am -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S bolao && adduser -S bolao -G bolao
COPY --from=build /workspace/user-service/target/quarkus-app/lib/ /app/lib/
COPY --from=build /workspace/user-service/target/quarkus-app/*.jar /app/
COPY --from=build /workspace/user-service/target/quarkus-app/app/ /app/app/
COPY --from=build /workspace/user-service/target/quarkus-app/quarkus/ /app/quarkus/
USER bolao
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app/quarkus-run.jar"]
