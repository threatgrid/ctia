(ns ctia.test-helpers.generators.id
  (:require [clojure.test.check.generators :as gen]
            [clojure.string :as str]
            [com.gfredericks.test.chuck.generators :as chuck]
            [ctia.lib.url :as url]
            [ctia.test-helpers.generators.common :refer :all]))

(def gen-proto (gen/elements ["http" "https"]))

(def gen-ipv4-addr
  (gen/fmap (fn [[a b c d]]
              (str a "." b "." c "." d))
            (gen/tuple (gen/choose 0 255)
                       (gen/choose 0 255)
                       (gen/choose 0 255)
                       (gen/choose 0 255))))

(def gen-host
  (gen/frequency
   [[2 (chuck/string-from-regex
        #"[a-zA-Z\d][-\da-zA-Z]{2,9}(\.[-\da-zA-Z]{3,10}){0,4}")]
    [1 gen-ipv4-addr]]))

(def gen-port
  (gen/one-of
   [(gen/return nil)
    (gen/choose 1000 65535)]))

(def gen-path
  (chuck/string-from-regex
   #"(\/([\w][-\w]{0,5}(\.[-\w]{1,4}){0,3})){1,3}"))

(def gen-type (gen-str-3+ gen-char-alpha-lower))

(def gen-user-defined-short-id
  (gen/such-that (comp seq str/trim)
                 gen/string-ascii))

(def gen-uuid-short-id
  (gen/fmap (fn [[type uuid]]
              (str type "-" uuid))
            (gen/tuple
             gen-type
             gen/uuid)))

(defn gen-short-id-of-type [type]
  (gen/fmap (fn [short-id-suffix]
              (str (name type) "-" short-id-suffix))
            (gen/one-of
             [gen-user-defined-short-id
              gen/uuid])))

(def gen-url-id-with-parts
  (gen/fmap (fn [[proto host port path [type short-id]]]
              [{:protocol proto
                :hostname host
                :path-prefix path
                :port port
                :type type
                :short-id short-id}
               (str proto
                    "://"
                    host
                    (if port (str ":" port))
                    path
                    "/ctia/"
                    type
                    "/"
                    (url/encode short-id))])
            (letfn [(prefix [[type id-part]]
                      [type (str type "-" id-part)])]
              (gen/tuple gen-proto
                         gen-host
                         gen-port
                         gen-path
                         (gen/one-of
                          [(gen/tuple gen-type
                                      gen-user-defined-short-id)
                           (gen/fmap prefix
                                     (gen/tuple gen-type
                                                gen/uuid))])))))

(def gen-long-id-with-parts gen-url-id-with-parts) ;; deprecated

(def gen-url-id
  (gen/fmap second
            gen-url-id-with-parts))
