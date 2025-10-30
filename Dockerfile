# Multi-stage build para otimizar o tamanho da imagem
# Stage 1: Build stage
FROM llsilvas/java21-maven-otel:2.16.0 as builder

# Definir diretório de trabalho
WORKDIR /app

# Copiar apenas os arquivos necessários para download de dependências (cache layer)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copiar código fonte
COPY src ./src

# Compilar aplicação
RUN mvn clean package -DskipTests -B

# Extrair layers do Spring Boot
RUN mkdir -p extracted && \
    java -Djarmode=tools -jar target/*.jar extract --layers --destination extracted

RUN ls -la /app/extracted

# Etapa de runtime com JRE otimizada
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Instalar dumb-init
RUN apk add --no-cache dumb-init

# Criar usuário não-root
RUN addgroup -g 1001 -S spring && \
    adduser -u 1001 -S spring -G spring

COPY --from=builder /opt/opentelemetry-javaagent.jar /app/opentelemetry-javaagent.jar

# Copiar layers em ordem de frequência de mudança (menos frequente primeiro)
COPY --from=builder /app/extracted/dependencies/ ./
COPY --from=builder /app/extracted/spring-boot-loader/ ./
COPY --from=builder /app/extracted/snapshot-dependencies/ ./
COPY --from=builder /app/extracted/application/ ./

RUN chown -R spring:spring /app
USER spring:spring

# Configurar JVM para containers
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+UseG1GC \
               -XX:+UseStringDeduplication \
               -Djava.security.egd=file:/dev/./urandom"

# Expor porta
EXPOSE 8099

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8099/actuator/health || exit 1

# Usar dumb-init como PID 1
ENTRYPOINT ["dumb-init", "--"]

# Comando para iniciar aplicação usando layers
CMD ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.JarLauncher"]