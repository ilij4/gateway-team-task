FROM eclipse-temurin:21-jre AS runtime

RUN useradd -u 10001 -r -s /sbin/nologin appuser
WORKDIR /app

COPY target/gateway-team-task-0.0.1-SNAPSHOT.jar /app/app.jar

EXPOSE 8080 8443

#HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
#  CMD curl -fsS http://localhost:${PORT}/health || exit 1

USER appuser
ENTRYPOINT ["java","-jar","/app/app.jar"]
