(ns ctia.lib.firehose
  (:require
   [clojure.tools.logging :as log])
  (:import
   [software.amazon.awssdk.core SdkBytes]
   [software.amazon.awssdk.services.firehose.model
    PutRecordRequest PutRecordResponse Record FirehoseException]
   [software.amazon.awssdk.services.firehose FirehoseClient]))

(defn default-client
  ^FirehoseClient []
  (FirehoseClient/create))

(defn put-record
  [^FirehoseClient client stream-name bytes]
  (try
    (let [^SdkBytes sdk-bytes (SdkBytes/fromByteArray bytes)
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
