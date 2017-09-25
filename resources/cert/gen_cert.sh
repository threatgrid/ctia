#!/bin/bash
openssl genrsa -out ctia-jwt.key 2048
openssl req -out ctia-jwt.csr -key ctia-jwt.key -new -sha256
openssl rsa -in ctia-jwt.key -pubout -out ctia-jwt.pub
