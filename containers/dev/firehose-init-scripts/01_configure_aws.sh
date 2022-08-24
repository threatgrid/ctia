set -x

echo "###### setting config vars ####"
aws configure set aws_access_key_id test --profile localstack
aws configure set aws_secret_access_key test --profile  localstack
aws configure set region us-east-1 --profile localstack
aws configure set output_format json --profile localstack

echo "###### set profile ####"
aws configure list --profile localstack

set +x
