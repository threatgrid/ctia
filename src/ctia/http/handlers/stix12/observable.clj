(ns ctia.http.handlers.stix12.observable
  (:require [clojure.string :as str])
  (:import [org.mitre.cybox.cybox_2
            ObjectType
            Observable]

           [org.mitre.cybox.common_2
            AnyURIObjectPropertyType
            HashListType
            HashType
            SimpleHashValueType
            StringObjectPropertyType]

           [org.mitre.cybox.default_vocabularies_2
            HashNameVocab10]

           [org.mitre.cybox.objects
            AccountObjectType
            Address
            CategoryTypeEnum
            DomainName
            FileObjectType
            URIObjectType
            URITypeEnum]))

(defn type->category [type]
  (case type
    "ip"     CategoryTypeEnum/IPV_4_ADDR
    "ipv6"   CategoryTypeEnum/IPV_6_ADDR
    "device" CategoryTypeEnum/MAC))

(defmulti ctia-observable->stix-observable
  (fn [{type :type}]
    (case type
      "ip"     :address
      "ipv6"   :address
      "device" :address
      "sha256" :file
      "sha1"   :file
      "md5"    :file
      "user"   :account
      (keyword type))))

(defmethod ctia-observable->stix-observable :address
  [{:keys [type value]}]
  (-> (doto (Observable.)
        (.setTitle (str type ": " value))
        (.setObject
         (doto (ObjectType.)
           (.setProperties
            (doto (Address.)
              (.setCategory (type->category type))
              (.setAddressValue
               (doto (StringObjectPropertyType.)
                 (.setValue value))))))))))

(defmethod ctia-observable->stix-observable :domain
  [{:keys [type value]}]
  (-> (doto (Observable.)
        (.setTitle (str type ": " value))
        (.setObject
         (doto (ObjectType.)
           (.setProperties
            (doto (DomainName.)
              ;; Set type? (FQDN | TLD)
              (.setValue
               (doto (StringObjectPropertyType.)
                 (.setValue value))))))))))

(defmethod ctia-observable->stix-observable :file
  [{:keys [type value]}]
  (-> (doto (Observable.)
        (.setTitle (str type ": " value))
        (.setObject
         (doto (ObjectType.)
           (.setProperties
            (doto (FileObjectType.)
              (.setHashes
               (HashListType.
                [(doto (HashType.)
                   (.setSimpleHashValue
                    (doto (SimpleHashValueType.)
                      (.setValue value)))
                   (.setType
                    (doto (HashNameVocab10.)
                      (.setVocabName "HashNameVocab")
                      (.setValue (str/upper-case type)))))])))))))))

(defmethod ctia-observable->stix-observable :url
  [{:keys [type value]}]
  (-> (doto (Observable.)
        (.setTitle (str type ": " value))
        (.setObject
         (doto (ObjectType.)
           (.setProperties
            (doto (URIObjectType.)
              (.setType URITypeEnum/URL)
              (.setValue
               (doto (AnyURIObjectPropertyType.)
                 (.setValue value))))))))))

(defmethod ctia-observable->stix-observable :account
  [{:keys [type value]}]
  (-> (doto (Observable.)
        (.setTitle (str type ": " value))
        (.setObject
         (doto (ObjectType.)
           (.setProperties
            (doto (AccountObjectType.)
              (.setDescription
               (doto (StringObjectPropertyType.)
                 (.setValue value))))))))))
