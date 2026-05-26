# ==========================================
# Stage 1: Build Java Application
# ==========================================
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /app

# Copy POM files and source code to leverage Docker cache
COPY pom.xml .
COPY gateway-converter ./gateway-converter
COPY gateway-swim ./gateway-swim
COPY gateway-cp ./gateway-cp

# Build only gateway-cp and its dependencies
# Use BuildKit cache mounting to speed up Maven builds
RUN --mount=type=cache,target=/root/.m2 mvn clean package -pl gateway-cp -am -DskipTests

# ==========================================
# Stage 2: Runtime Environment
# ==========================================
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Set timezone to UTC for aviation standard
ENV TZ=UTC
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# Copy the built jar file
COPY --from=builder /app/gateway-cp/target/gateway-cp-*.jar app.jar

# Expose port (default 8080)
EXPOSE 8080

# Run with G1GC garbage collector optimized for production JVM
ENTRYPOINT ["java", "-XX:+UseG1GC", "-jar", "app.jar"]
