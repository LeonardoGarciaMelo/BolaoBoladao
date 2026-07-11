# syntax=docker/dockerfile:1

# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Copia o pom pai e o do shared-contracts primeiro para cache de dependências
COPY pom.xml pom.xml
COPY shared-contracts/pom.xml shared-contracts/pom.xml
COPY partidas-service/pom.xml partidas-service/pom.xml

# Resolve dependências (cache de camada)
RUN mvn -q -pl shared-contracts,partidas-service -am dependency:go-offline || true

# Copia o código-fonte
COPY shared-contracts/src shared-contracts/src
COPY partidas-service/src partidas-service/src

# Build apenas do módulo necessário + dependências (shared-contracts)
RUN mvn -q -pl partidas-service -am -DskipTests package

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S bolao && adduser -S bolao -G bolao

COPY --from=build /workspace/partidas-service/target/quarkus-app/lib/ /app/lib/
COPY --from=build /workspace/partidas-service/target/quarkus-app/*.jar /app/
COPY --from=build /workspace/partidas-service/target/quarkus-app/app/ /app/app/
COPY --from=build /workspace/partidas-service/target/quarkus-app/quarkus/ /app/quarkus/

USER bolao

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "/app/quarkus-run.jar"]
