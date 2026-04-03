# Multi-stage Dockerfile for Legit Backend
FROM gradle:8.5-jdk21 AS builder

WORKDIR /app

# Copy gradle files
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY gradle ./gradle

# Download dependencies (cached layer)
RUN gradle dependencies --no-daemon || true

# Copy source code
COPY src ./src

# Build application
RUN gradle shadowJar --no-daemon

# Runtime stage
FROM openjdk:21-jdk-slim

WORKDIR /app

# Install curl and jq for healthcheck and config reading
RUN apt-get update && \
    apt-get install -y curl jq && \
    rm -rf /var/lib/apt/lists/*

# Copy built JAR
COPY --from=builder /app/build/libs/*-all.jar app.jar

# Copy entrypoint script
COPY docker-entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh

# Create logs directory
RUN mkdir -p /app/logs

# Expose port
EXPOSE 8080

# Environment variables with defaults
ENV JAVA_OPTS="-Xmx512m -Xms256m"
ENV MONGODB_URI="mongodb://mongodb:27017"
ENV MONGODB_DATABASE="legit"
ENV BLOCKCHAIN_RPC_URL="http://blockchain:8545"
ENV BLOCKCHAIN_PRIVATE_KEY="0x4f3edf983ac636a65a842ce7c78d9aa706d3b113bce9c46f30d7d21715b23b1d"
ENV BLOCKCHAIN_AUDIT_CONTRACT=""
ENV BLOCKCHAIN_REPUTATION_CONTRACT=""

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/api/v1/gateway/health || exit 1

# Use entrypoint
ENTRYPOINT ["/app/entrypoint.sh"]
