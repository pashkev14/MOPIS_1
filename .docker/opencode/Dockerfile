FROM ghcr.io/anomalyco/opencode:latest

RUN apk add --no-cache \
    openjdk25 \
    maven \
    git \
    bash

ENV JAVA_HOME=/usr/lib/jvm/java-25-openjdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"

RUN if id -u 1000 >/dev/null 2>&1; then deluser --remove-home $(id -un 1000); fi \
    && adduser -D -u 1000 -s /bin/bash coder \
    && mkdir -p /home/coder/.m2 /home/coder/.config/opencode /home/coder/.local/share/opencode /workspace \
    && chown -R coder:coder /home/coder /workspace

USER coder
WORKDIR /workspace

CMD ["."]
