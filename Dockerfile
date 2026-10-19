# ---------- Etapa 1: build ----------
# Imagen completa con JDK + Maven; no llega a producción.
FROM maven:3.9-eclipse-temurin-26 AS build
WORKDIR /workspace

# Primero solo el pom: la capa de dependencias se cachea mientras el pom no cambie.
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests

# Extrae el fat-jar en capas (dependencias, loader, snapshots, aplicación) para que un cambio
# de código solo invalide la última capa de la imagen.
RUN java -Djarmode=tools -jar target/orders-service.jar extract --layers --launcher --destination target/extracted

# ---------- Etapa 2: runtime ----------
# Distroless: solo la JRE, sin shell ni gestor de paquetes -> menor superficie de ataque y menos CVEs.
# La variante :nonroot ejecuta como UID 65532.
FROM gcr.io/distroless/java21-debian12:nonroot
WORKDIR /app

COPY --from=build /workspace/target/extracted/dependencies/ ./
COPY --from=build /workspace/target/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/target/extracted/application/ ./

USER nonroot:nonroot
EXPOSE 8080

# MaxRAMPercentage respeta los límites de memoria del contenedor / pod.
ENTRYPOINT ["/usr/bin/java", "-XX:MaxRAMPercentage=75.0", "-XX:+ExitOnOutOfMemoryError", "org.springframework.boot.loader.launch.JarLauncher"]
