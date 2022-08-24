(ns ctia.lib.firehose
  (:require
   [clojure.string :as string]
   [clojure.tools.logging :as log])
  (:import
   [java.net URI]
   [software.amazon.awssdk.regions Region]
   [software.amazon.awssdk.core SdkBytes]
   [software.amazon.awssdk.auth.credentials
    AwsBasicCredentials StaticCredentialsProvider]
   [software.amazon.awssdk.services.firehose.model
    PutRecordRequest PutRecordResponse Record FirehoseException]
   [software.amazon.awssdk.services.firehose FirehoseClient FirehoseClientBuilder]))

(defn has-value?
  "Given a `value`  return true if that string is populated."
  [value]
  (not (string/blank? value)))

(defn get-region
  "Given a config with a region return the Region for that value.
  Will blow up if given a bad value"
  [{:keys [region]}]
  (-> region
      (string/lower-case)
      (Region/of)))

(defn basic-auth?
  "Return true given `local` true, `access-key` and `secret-key` with
  values."
  [{:keys [access-key secret-key local]}]
  (and
   (has-value? access-key)
   (has-value? secret-key)
   local))

(defn make-basic-auth
  "Given a config with `access-key` and `secret-key` values create a
  basic credentials provider."
  ^StaticCredentialsProvider
  [{:keys [access-key secret-key]}]
  (StaticCredentialsProvider/create
   (AwsBasicCredentials/create access-key secret-key)))

(defn create-client-builder
  "Given an `aws-config` of type  return a FirehoseClientBuilder respecting given
  values."
  ^FirehoseClientBuilder
  [{:keys [region endpoint]
    :as   aws-config}]
  (cond-> (FirehoseClient/builder)
    (has-value? region)      (.region (get-region aws-config))
    (has-value? endpoint)    (.endpointOverride (URI. endpoint))
    (basic-auth? aws-config) (.credentialsProvider (make-basic-auth aws-config))))

(defn build-client
  "Given a FirehoseClientBuilder `client-builder`, build and return the
  FirehoseClient"
  ^FirehoseClient
  [^FirehoseClientBuilder client-builder]
  (.build client-builder))

(defn put-record
  "Given a firehose client, stream, and byte array attempt to build and send a
  single record to firehose."
  [^FirehoseClient client stream-name encoded-event]
  (try
    (let [^SdkBytes sdk-bytes (SdkBytes/fromByteArray encoded-event)

          ^Record record
          (.build (doto (Record/builder)
                    (.data sdk-bytes)))

          ^PutRecordRequest record-request
          (.build (doto (PutRecordRequest/builder)
                    (.deliveryStreamName stream-name)
                    (.record record)))]

      (.putRecord client record-request))
    (catch FirehoseException e
      (log/error e "Unable to put-record to Firehose"))))
