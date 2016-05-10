FROM clojure:alpine
RUN mkdir -p /usr/src/app
WORKDIR /usr/src/app
COPY target/ctia.jar /usr/src/app/
CMD ["java", "-Xmx4g", "-Djava.awt.headless=true", "-server", "-cp", "ctia.jar:resources:.", "clojure.main", "-m", "ctia.main"]
EXPOSE 3000 3001
