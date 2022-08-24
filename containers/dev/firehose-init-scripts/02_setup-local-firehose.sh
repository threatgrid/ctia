echo "-------------------------------------Script-02"

echo "########### Creating Bucket firehose-destination  ###########"
awslocal s3api create-bucket --bucket firehose-destination

echo "########### Creating firehose ctia-test-delivery-stream  ###########"
awslocal firehose create-delivery-stream --delivery-stream-name test-ctia-firehose-local --delivery-stream-type DirectPut --s3-destination-configuration "RoleARN=arn:aws:iam::000000000000:role/super-role,BucketARN=arn:aws:s3:::firehose-destination,Prefix=test-output,ErrorOutputPrefix=firehose-errors,BufferingHints={SizeInMBs=30,IntervalInSeconds=60},CompressionFormat=GZIP, CloudWatchLoggingOptions={Enabled=false,LogGroupName=ctia-test-firehose,LogStreamName=ctia-log-stream-firehose-name}"

echo "########### Remove all objects from bucket ###########"
awslocal s3 rm --recursive s3://firehose-destination

