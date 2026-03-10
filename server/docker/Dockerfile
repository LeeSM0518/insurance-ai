FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY app.jar app.jar

RUN addgroup -g 1000 appgroup && adduser -u 1000 -G appgroup -D appuser \
    && mkdir -p /app/logs && chown -R appuser:appgroup /app/logs
USER appuser

EXPOSE 8080
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC"
CMD ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
