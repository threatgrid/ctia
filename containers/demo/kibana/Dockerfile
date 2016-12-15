FROM kibana:5.1

RUN apt-get update && apt-get install -y netcat

COPY ./entrypoint.sh /tmp/entrypoint.sh
RUN chmod +x /tmp/entrypoint.sh

CMD ["/tmp/entrypoint.sh"]
