(ns ctia.lib.firehose
  (:require
   [clojure.tools.logging :as log])
  (:import
   [java.net URI]
   [software.amazon.awssdk.core SdkBytes]
   [software.amazon.awssdk.auth.credentials AwsBasicCredentials StaticCredentialsProvider]
   [software.amazon.awssdk.services.firehose.model
    PutRecordRequest PutRecordResponse Record FirehoseException]
   [software.amazon.awssdk.services.firehose FirehoseClient]))

(defn default-client
  ^FirehoseClient []
  (FirehoseClient/create))


;; for local testing
(def local-basic-auth-client (AwsBasicCredentials/create "test" "test"))

(defn local-client
  "For local testing with localstack for the firehose hook"
  ^FirehoseClient []
  (.build
   (doto (FirehoseClient/builder)
     (.endpointOverride (URI. "http://localhost:4566"))
     (.credentialsProvider (StaticCredentialsProvider/create local-basic-auth-client)))))

(defn get-client-fn
  "For local testing purposes. If you are using local stack while
  running locally this will give you a local client"
  [local?]
  (if local?
    local-client
    default-client))

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
