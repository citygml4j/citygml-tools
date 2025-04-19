# Build stage #################################################################
# Arguments
ARG BUILDER_IMAGE_TAG='21-jdk-noble'
ARG RUNTIME_IMAGE_TAG='21-jre-noble'

# Base image
FROM eclipse-temurin:${BUILDER_IMAGE_TAG} AS builder

# Copy source code
WORKDIR /build
COPY . /build

# Build
RUN set -x && \
    chmod u+x ./gradlew && ./gradlew installDist

# Runtime stage ###############################################################
# Base image
FROM eclipse-temurin:${RUNTIME_IMAGE_TAG} AS runtime

# Version info
ARG CITYGML_TOOLS_VERSION
ENV CITYGML_TOOLS_VERSION=${CITYGML_TOOLS_VERSION}

# Copy from builder
COPY --from=builder /build/build/install/ /opt/

# Run as non-root user, add start script in path and set permissions
RUN set -x && \
    ln -sf /opt/citygml-tools/citygml-tools /usr/local/bin/

USER 1000
WORKDIR /data

ENTRYPOINT ["citygml-tools"]
CMD ["--help"]