# Build stage #################################################################
# Arguments
ARG BUILDER_IMAGE_TAG='11-jdk-alpine'
ARG RUNTIME_IMAGE_TAG='11-jre-alpine'

# Base image
FROM eclipse-temurin:${BUILDER_IMAGE_TAG} AS builder

# Copy source code
WORKDIR /build
COPY . /build

# Build
RUN chmod u+x ./gradlew && ./gradlew installDist

# Runtime stage ###############################################################
# Base image
FROM eclipse-temurin:${RUNTIME_IMAGE_TAG} AS runtime

# Copy from builder
COPY --from=builder /build/build/install/ /opt/

# Run as non-root user
RUN addgroup -g 1000 -S citygml-tools && \
    adduser -u 1000 -G citygml-tools -S citygml-tools

# Add start script to path
RUN ln -sf /opt/citygml-tools/citygml-tools /usr/local/bin/

WORKDIR /data
USER 1000

ENTRYPOINT ["citygml-tools"]
CMD ["--help"]