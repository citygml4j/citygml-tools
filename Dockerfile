# Build stage #################################################################
# Arguments
ARG BUILDER_IMAGE_TAG='17-jdk-jammy'
ARG RUNTIME_IMAGE_TAG='17-jdk-jammy'

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

# Version info
ARG CITYGML_TOOLS_VERSION
ENV CITYGML_TOOLS_VERSION=${CITYGML_TOOLS_VERSION}

# Copy from builder
COPY --from=builder /build/build/install/ /opt/

# Run as non-root user, add start script in path and set permissions
RUN groupadd --gid 1000 -r citygml-tools && \
    useradd --uid 1000 --gid 1000 -d /data -m -r --no-log-init citygml-tools && \
    ln -sf /opt/citygml-tools/citygml-tools /usr/local/bin/

WORKDIR /data
USER 1000

ENTRYPOINT ["citygml-tools"]
CMD ["--help"]