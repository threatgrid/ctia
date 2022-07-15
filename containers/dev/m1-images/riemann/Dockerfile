FROM openjdk:11.0.10-jre-slim

ARG RIEMANN_VERSION

RUN apt-get update && \
    apt-get install -y --no-install-recommends curl bzip2 && \
    rm -rf /var/lib/apt/lists/*

RUN curl -L -o - "https://github.com/riemann/riemann/releases/download/$RIEMANN_VERSION/riemann-$RIEMANN_VERSION.tar.bz2" | tar -xj --strip-components=1

EXPOSE 5555/tcp 5555/udp 5556 5557
CMD ["/bin/riemann", "/etc/riemann.config"]
