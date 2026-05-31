# ==========================================
# Stage 1: Build Java Application
# ==========================================
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /app

# Copy POM files and source code to leverage Docker cache
COPY pom.xml .
COPY gateway-converter ./gateway-converter
COPY gateway-swim ./gateway-swim

# Remove gateway-cp module from parent pom.xml inside container to build in isolation
RUN sed -i '/<module>gateway-cp<\/module>/d' pom.xml

# Build only gateway-swim and its dependency module (gateway-converter)
# Use BuildKit cache mounting to speed up Maven builds
RUN --mount=type=cache,target=/root/.m2 mvn clean package -pl gateway-swim -am -DskipTests

# ==========================================
# Stage 2: Runtime Environment
# ==========================================
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Set timezone to UTC for aviation standard
ENV TZ=UTC
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# Copy the built jar file
COPY --from=builder /app/gateway-swim/target/gateway-swim-*.jar app.jar

# Expose port (default 8082)
EXPOSE 8082

# Run with G1GC garbage collector optimized for production JVM
ENTRYPOINT ["java", "-XX:+UseG1GC", "-jar", "app.jar"]
