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

    $ htpasswd -c ./nginx/config/authdb exampleuser

## Building and Starting the Containers

### Linux Host Configuration
If your docker host OS is Linux,
Elasticsearch 5x bootstrap checks the maximum map count configuration,
tune it accordingly with `sudo sysctl -w vm.max_map_count=262144`

To build and run the containers with docker-compose, install docker and docker compose.  Then type:

    $ sudo docker-compose up

## URI Endpoints

- http://hostname/ - CTIA
- http://hostname/kibana/ - Kibana dashboard

## Known Issues
