# Firehose

[Firehose](https://aws.amazon.com/kinesis/data-firehose/) is a aws ETL service for streaming into data stores.

This optional hook is implemented in `ctia.flows.hooks.event-hooks` with the sdk implemented in `ctia.lib.firehose`.
and is configured using `ctia.hook.firehose.*` properties.

In `resources/ctia-default.properties` it is set to:

```
ctia.hook.firehose.enabled=false
ctia.hook.firehose.stream-name=test-ctia-firehose-local 
ctia.hook.firehose.local=true
```

The values are straight forward, `ctia.hook.firehose.stream-name` points to whatever the stream name is.

## Firehose Testing

This assumes you have aws cli installed. Find instructions [here](https://awscli.amazonaws.com/v2/documentation/api/latest/reference/index.html).

* [Localstack](#localstack)
* [Remote Firehose](#remote-firehose)

### <a id="localstack"></a>Using localstack

On startup the localstack container will configure a aws user (for it's container) and create the `firehose-destination` bucket as well as the `test-ctia-firehose-local` firehose stream.
Some setup will be cached at `/tmp/localstack` which can be deleted to start fresh.

#### Setup

If you want to know more about what set up entails see [here](#death-by-detail). Otherwise from the base directory run:

```
./scripts/setup-local-firehose
```

#### Configuration Changes

Switch `ctia.hook.firehose.enabled` to `true` in `/resources/ctia-default.properties`
After processing events you'll be able to see them in the firehose-destination bucket

```
aws --endpoint-url=http://localhost:4566 s3api list-objects --bucket firehose-destination
```


#### <a id="death-by-detail"></a>Lots of Details
[Local Stack Documentation for aws-cli integration](https://docs.localstack.cloud/integrations/aws-cli/#aws-cli)

Configure aws credentials by adding a test-local to your aws configuration

```
aws configure --profile test-local

-- with the following options
aws_access_key_id = test
aws_secret_access_key = test
region = us-east-1
output = json
```

export the profile
```bash
export AWS_PROFILE=test-local
```

alternatively if this is an infrequent use case:

```bash
export AWS_ACCESS_KEY_ID="test"
export AWS_SECRET_ACCESS_KEY="test"
export AWS_DEFAULT_REGION="us-east-1"
```

Create a bucket and firehose with a s3 destination
```bash
aws --endpoint-url=http://localhost:4566 s3api create-bucket --bucket firehose-destination
aws --endpoint-url=http://localhost:4566 firehose create-delivery-stream --cli-input-json file://$PWD/firehose_skeleton.json
```

With the firehose options being:
```json
{
  "DeliveryStreamName": "test-ctia-firehose-local",
  "DeliveryStreamType": "DirectPut",
  "S3DestinationConfiguration": {
    "RoleARN": "arn:aws:iam::000000000000:role/super-role",
    "BucketARN": "arn:aws:s3:::firehose-destination",
    "Prefix": "test-output",
    "ErrorOutputPrefix": "test-error-log",
    "BufferingHints": {
      "SizeInMBs": 1,
      "IntervalInSeconds": 60
    },
    "CompressionFormat": "GZIP",
    "CloudWatchLoggingOptions": {
      "Enabled": false,
      "LogGroupName": "",
      "LogStreamName": ""
    }
  },
  "Tags": [
    {
      "Key": "",
      "Value": "tagValue"
    }
  ]
}
```

This creates a firehose endpoint called `test-ctia-firehose-local` with a destination of s3 bucket `firehose-destination`


#### <a id="remote-firehose"></a>Using a configured remote firehose


It is assumed that your configured credentials and config for the exported aws profile have access to and are set in the same region as the firehose instance you are testing against.

Update the `ctia.hook.firehose` properties to

```
ctia.hook.firehose.enabled=true
ctia.hook.firehose.stream-name=$REMOTE_STREAM_NAME
ctia.hook.firehose.local=false
```

#### Making a stream

CDK snippet for creating a firehose stream with s3 destination

```typescript
import { Duration, RemovalPolicy, Stack, StackProps, Size } from "aws-cdk-lib";
import { Construct } from "constructs";
import * as s3 from "aws-cdk-lib/aws-s3";
import * as firehose from "@aws-cdk/aws-kinesisfirehose-alpha";
import * as destinations from '@aws-cdk/aws-kinesisfirehose-destinations-alpha';

export class ExampleFirehoseInfraStack extends Stack {
  constructor(scope: Construct, id: string, props?: StackProps) {
    super(scope, id, props);

    const newS3Bucket = new s3.Bucket(this, "s3-bucket", {
      bucketName: "s3-bucket-example-ctia-$SOMETHING_HERE_FOR_UNIQUENESS",
      removalPolicy: RemovalPolicy.DESTROY,
    });

    const s3Destination = new destinations.S3Bucket(newS3Bucket, {
      compression: destinations.Compression.GZIP,
      bufferingInterval: Duration.minutes(1),
      bufferingSize: Size.mebibytes(30),
      dataOutputPrefix: 'firehose/incoming/',
      errorOutputPrefix: 'firehose-errors/!{firehose:error-output-type}/!{timestamp:yyyy}/anyMonth/!{timestamp:dd}',
    });

    new firehose.DeliveryStream(this, 'firehose-example-stream', {
      destinations: [s3Destination],
      deliveryStreamName: 'firehose-stream-name-ctia-example',
    });
}}
```
