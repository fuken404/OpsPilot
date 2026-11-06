# Imagen de la API. Se compila dentro de Docker para que el resultado no dependa de la máquina que construye.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q -DskipTests package \
 && cp target/opspilot-*.jar app.jar \
 && java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

FROM eclipse-temurin:25-jre
RUN groupadd --system app && useradd --system --gid app --home /app app \
 && mkdir -p /data/attachments && chown app:app /data/attachments
WORKDIR /app
# Capas de menos a más cambiante: al desplegar solo viaja lo que cambió.
COPY --from=build /src/extracted/dependencies/ ./
COPY --from=build /src/extracted/spring-boot-loader/ ./
COPY --from=build /src/extracted/snapshot-dependencies/ ./
COPY --from=build /src/extracted/application/ ./
USER app
ENV ATTACHMENTS_DIR=/data/attachments \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8081
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD ["sh", "-c", "exec 3<>/dev/tcp/127.0.0.1/8081 && printf 'GET /actuator/health HTTP/1.0\\r\\n\\r\\n' >&3 && grep -q UP <&3"]
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
