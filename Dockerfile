FROM clojure:alpine
RUN mkdir -p /usr/src/app
WORKDIR /usr/src/app
COPY project.clj /usr/src/app/
RUN lein deps
COPY . /usr/src/app
RUN lein uberjar
CMD ["java", "-jar", "target/server.jar"]
EXPOSE 3000
