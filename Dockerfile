FROM openjdk:11-jdk-slim as builder

WORKDIR /code

ADD . /code

RUN /bin/sh gradlew installDist

FROM openjdk:8-jre-alpine

COPY --from=builder /code/build/install/ /opt/

RUN ln -s /opt/citygml-tools/citygml-tools /usr/local/bin/ && \
    adduser -D -S -h /data -s /sbin/nologin -G root --uid 1001 citygml-tools && \
    chgrp 0 /etc/passwd && \
    chmod g=u /etc/passwd && \
    chgrp -R 0 /data && \
    chmod -R g=u /data

COPY --chown=1001:0 resources/docker/docker_uid_entrypoint.sh /usr/local/bin/

WORKDIR /data

USER 1001

ENTRYPOINT ["/usr/local/bin/docker_uid_entrypoint.sh"]

CMD ["--help"]