# This Dockerfile was generated from templates/Dockerfile.j2

FROM centos:7
LABEL maintainer "Elastic Docker Team <docker@elastic.co>"

ENV ELASTIC_CONTAINER true
ENV PATH /usr/share/elasticsearch/bin:$PATH
ENV JAVA_HOME /usr/lib/jvm/jre-1.8.0-openjdk

RUN yum update -y && yum install -y java-1.8.0-openjdk-headless wget which && yum clean all

RUN groupadd -g 1000 elasticsearch && adduser -u 1000 -g 1000 -d /usr/share/elasticsearch elasticsearch

WORKDIR /usr/share/elasticsearch

USER elasticsearch

# Download and extract defined ES version.
RUN curl -fsSL https://artifacts.elastic.co/downloads/elasticsearch/elasticsearch-5.6.16.tar.gz | \
    tar zx --strip-components=1

RUN set -ex && for esdirs in config data logs; do \
        mkdir -p "$esdirs"; \
    done

# Install x-pack and also the ingest-{agent,geoip} modules required for Filebeat
RUN for PLUGIN in x-pack ingest-user-agent ingest-geoip; do \
      elasticsearch-plugin install --batch "$PLUGIN"; \
    done
COPY elasticsearch.yml log4j2.properties config/
COPY x-pack/log4j2.properties config/x-pack/
COPY bin/es-docker bin/es-docker

USER root
RUN chown elasticsearch:elasticsearch \
      config/elasticsearch.yml \
      config/log4j2.properties \
      config/x-pack/log4j2.properties \
      bin/es-docker && \
    chmod 0750 bin/es-docker

USER elasticsearch
CMD ["/bin/bash", "bin/es-docker"]

EXPOSE 9200 9300
