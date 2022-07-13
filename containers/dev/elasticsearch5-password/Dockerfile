FROM alpine:latest

RUN apk --no-cache add bash curl

# https://github.com/vishnubob/wait-for-it
ADD ./wait-for-it.sh /bin/wait-for-it
ADD ./entrypoint.sh entrypoint

ENTRYPOINT ["./entrypoint"]
