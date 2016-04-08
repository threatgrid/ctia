# CTIA Demo via Docker-Compose

This is a containerized and integrated demo deployment of the Cisco Threat Intelligence API.

This demo integrates:

- Nginx reverse proxy on port 80
- CTIA service on port 3000
- ElasticSearch service on port 9200
- Logstash service on port 5000
- Kibana service on port 5601
- Redis service on port 6379
- Logspout (routes container logs to logstash)

## Setting up HTTP Basic Auth

    $ sudo apt-get install apache2-utils -y
    $ sudo htpasswd -c /etc/nginx/.htpasswd exampleuser

## Building and Starting the Containers

To build and run the containers with docker-compose, install docker and docker compose.  Then type:

    $ sudo docker-compose up

## URI Endpoints

- http://hostname/ - CTIA
- http://hostname/kibana/ - Kibana dashboard

## Known Issues
