# Usar imagen base de Java 21
FROM openjdk:21-jre-slim

# Instalar curl para health checks
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# Crear directorio de trabajo
WORKDIR /app

# Copiar el JAR compilado
COPY target/*.jar app.jar

# Exponer puerto 8080 (requerido por Cloud Run)
EXPOSE 8080

# Configurar JVM para contenedores
ENV JAVA_OPTS="-Xms512m -Xmx1024m -Dspring.profiles.active=production"

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# Ejecutar la aplicación
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]