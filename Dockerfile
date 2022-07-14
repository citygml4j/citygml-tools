# Build stage #################################################################
# Arguments
ARG BUILDER_IMAGE_TAG='17-jdk-slim'
ARG RUNTIME_IMAGE_TAG='17-slim'

# Base image
FROM openjdk:${BUILDER_IMAGE_TAG} AS builder

# Copy source code
WORKDIR /build
COPY . /build

# Build
RUN chmod u+x ./gradlew && ./gradlew installDist

# Runtime stage ###############################################################
# Base image
FROM openjdk:${RUNTIME_IMAGE_TAG} AS runtime

# Copy from builder
COPY --from=builder /build/build/install/ /opt/

# Run as non-root user
RUN groupadd --gid 1000 -r citygml-tools && \
    useradd --uid 1000 --gid 1000 -d /data -m -r --no-log-init citygml-tools

# Add start script to path
RUN ln -sf /opt/citygml-tools/citygml-tools /usr/local/bin/

WORKDIR /data
USER 1000

ENTRYPOINT ["citygml-tools"]
CMD ["--help"]