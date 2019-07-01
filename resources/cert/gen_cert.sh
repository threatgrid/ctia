#!/bin/bash

certname=${1:-ctia-jwt}

echo "generating $certname.key"
openssl genrsa -out $certname.key 2048
echo "generating $certname.csr"
openssl req -out $certname.csr -key $certname.key -new -sha256
echo "generating $certname.pub"
openssl rsa -in $certname.key -pubout -out $certname.pub
