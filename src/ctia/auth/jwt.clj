(ns ctia.auth.jwt
  (:refer-clojure :exclude [identity])
  (:require
   [cheshire.core :as json]
   [clj-http.client :as http]
   [clj-jwt.key :refer [public-key]]
   [clj-momo.lib.set :refer [as-set]]
   [clojure.core.memoize :as memo]
   [clojure.set :as set]
   [clojure.string :as string]
   [clojure.tools.logging :as log]
   [ctia.auth :as auth :refer [IIdentity]]
   [ctia.auth.capabilities :refer
    [all-entities gen-capabilities-for-entity-and-accesses]]
   [scopula.core :as scopula])
  (:import
   [java.security KeyFactory]
   [java.security.spec RSAPublicKeySpec]
   [java.math BigInteger]
   [java.util Base64]))

(defn load-public-key
  [path]
  (try (public-key path)
       (catch Exception e
         (log/errorf "Could not read the public key: %s" path)
         (throw e))))

(defn parse-jwt-pubkey-map
  "generate an hash-map (string => public-key)
  An example is:

  issuer1=path-to-public-key,issuer2=path-to-public-key2

  First we split by , for each entry.
  Then each entry has the format

  issuer=path-to-public-key-file
  "
  [txt]
  (when txt
    (let [jwt-pub-key-map-regex #"^([^=,]*=[^,]*)(,[^=,]*=[^,]*)*$"]
      (when-not (re-matches jwt-pub-key-map-regex txt)
        (let [err-msg (str "Wrong format for ctia.http.jwt.jwt-pubkey-map config."
                           " It should matches the following regex: "
                           jwt-pub-key-map-regex
                           "\nExamples: \"APPNAME=/path/to/file.key\""
                           "\n          \"APPNAME=/path/to/file.key,OTHER=/other/path.key\"")]
          (log/error err-msg)
          (throw (ex-info err-msg {:jwt-pubkey-map txt})))))
    (try (some->> (string/split txt #",")
                  (map #(string/split % #"="))
                  (map (fn [[k v]] [k (load-public-key v)]))
                  (into {}))
         (catch Exception e
           (let [err-msg (format
                          "Could not load JWT keys correctly. Please check ctia.http.jwt.jwt-pubkey-map config: %s"
                          (.getMessage e))]
             (log/error err-msg)
             (throw (ex-info err-msg {:jwt-pubkey-map txt})))))))

;; JWKS Support Functions

(defn- base64url-decode
  "Decode base64url encoded string to bytes"
  [^String s]
  (let [padded (case (mod (count s) 4)
                 2 (str s "==")
                 3 (str s "=")
                 s)
        standard (-> padded
                    (string/replace "-" "+")
                    (string/replace "_" "/"))]
    (.decode (Base64/getDecoder) standard)))

(defn- bytes->bigint
  "Convert byte array to BigInteger"
  [bytes]
  (BigInteger. 1 bytes))

(defn- jwk->public-key
  "Convert a JWK map to a Java PublicKey.
   Supports RSA keys with kty=RSA."
  [{:keys [kty n e] :as jwk}]
  (when (= kty "RSA")
    (try
      (let [modulus (-> n base64url-decode bytes->bigint)
            exponent (-> e base64url-decode bytes->bigint)
            key-spec (RSAPublicKeySpec. modulus exponent)
            key-factory (KeyFactory/getInstance "RSA")]
        (.generatePublic key-factory key-spec))
      (catch Exception ex
        (log/errorf ex "Failed to convert JWK to public key: %s" jwk)
        nil))))

(defn- fetch-jwks
  "Fetch JWKS from the given URL"
  [url]
  (try
    (log/debugf "Fetching JWKS from %s" url)
    (let [response (http/get url
                            {:as :json
                             :throw-exceptions false
                             :socket-timeout 5000
                             :connection-timeout 5000})]
      (if (= 200 (:status response))
        (do
          (log/debugf "Successfully fetched JWKS from %s" url)
          (:body response))
        (do
          (log/errorf "Failed to fetch JWKS from %s: status %d" 
                     url (:status response))
          nil)))
    (catch Exception ex
      (log/errorf ex "Exception fetching JWKS from %s" url)
      nil)))

(defn- build-key-map
  "Build a map of kid -> PublicKey from JWKS response"
  [jwks-response]
  (when jwks-response
    (try
      (reduce (fn [acc jwk]
                (if-let [kid (:kid jwk)]
                  (if-let [public-key (jwk->public-key jwk)]
                    (do
                      (log/debugf "Added key with kid: %s" kid)
                      (assoc acc kid public-key))
                    (do
                      (log/warnf "Failed to convert JWK with kid: %s" kid)
                      acc))
                  (do
                    (log/warnf "JWK missing kid: %s" jwk)
                    acc)))
              {}
              (:keys jwks-response))
      (catch Exception ex
        (log/errorf ex "Failed to build key map from JWKS")
        {}))))

(defn- fetch-and-build-key-map
  "Fetch JWKS and build key map"
  [url]
  (-> url
      fetch-jwks
      build-key-map))

(def fetch-cached-keys
  "Cached version of fetch-and-build-key-map with 5 minute TTL"
  (memo/ttl fetch-and-build-key-map
            :ttl/threshold (* 5 60 1000))) ; 5 minutes in milliseconds

(defn get-public-key-for-kid
  "Get public key for the given kid from JWKS endpoint.
   Returns nil if kid not found or on error."
  [jwks-url kid]
  (when (and jwks-url kid)
    (log/infof "Looking up key for kid: %s from %s" kid jwks-url)
    (let [key-map (fetch-cached-keys jwks-url)]
      (log/infof "Available keys in JWKS: %s" (keys key-map))
      (or (get key-map kid)
          (do
            (log/warnf "Kid %s not found in JWKS from %s. Available keys: %s" kid jwks-url (keys key-map))
            nil)))))

(defn get-public-key-for-kid-from-multiple-urls
  "Try to get public key for the given kid from multiple JWKS URLs.
   Returns the first matching key found, or nil if not found in any URL."
  [jwks-urls kid]
  (when (and jwks-urls kid)
    (log/infof "Looking up key for kid: %s from %d JWKS URLs" kid (count jwks-urls))
    (loop [urls jwks-urls]
      (when-let [url (first urls)]
        (if-let [public-key (get-public-key-for-kid url kid)]
          (do
            (log/infof "Found key for kid: %s at %s" kid url)
            public-key)
          (do
            (log/debugf "Kid %s not found at %s, trying next URL" kid url)
            (recur (rest urls))))))))

(defn parse-jwks-urls
  "Parse JWKS URLs configuration.
   Format: 'issuer1=url1,issuer1=url2,issuer2=url3'
   Returns a map of issuer -> [list of JWKS URLs]"
  [config-str]
  (when (and config-str (not (string/blank? config-str)))
    (try
      (let [jwks-url-regex #"^([^=,]+=[^,]+)(,[^=,]+=[^,]+)*$"]
        (when-not (re-matches jwks-url-regex config-str)
          (let [err-msg (str "Wrong format for JWKS URLs config. "
                            "Format: 'issuer1=url1,issuer1=url2,issuer2=url3'")]
            (log/error err-msg)
            (throw (ex-info err-msg {:jwks-urls config-str}))))
        ;; Build a map where each issuer can have multiple URLs
        (->> (string/split config-str #",")
             (map #(string/split % #"=" 2))
             (reduce (fn [acc [issuer url]]
                       (update acc issuer (fn [urls] (conj (or urls []) url))))
                     {})))
      (catch Exception ex
        (log/errorf ex "Failed to parse JWKS URLs: %s" config-str)
        (throw ex)))))


(defn entity-root-scope [get-in-config]
  (get-in-config [:ctia :auth :entities :scope]
                              "private-intel"))

(defn casebook-root-scope [get-in-config]
  (get-in-config [:ctia :auth :casebook :scope]
                              "casebook"))

(defn assets-root-scope [get-in-config]
  (get-in-config [:ctia :auth :assets :scope] "asset"))

(defn claim-prefix [get-in-config]
  (get-in-config [:ctia :http :jwt :claim-prefix]
                 "https://schemas.cisco.com/iroh/identity/claims"))

(defn unionize
  "Given a seq of set make the union of all of them"
  [sets]
  (apply set/union sets))

(defn gen-entity-capabilities
  "given a scope representation whose root scope is entity-root-scope generate
  capabilities"
  [scope-repr]
  (case (count (:path scope-repr))
    ;; example: ["private-intel" "sighting"] (for private-intel/sighting scope)
    2 (condp = (second (:path scope-repr))
        "import-bundle" (if (contains? (:access scope-repr) :write)
                          #{:import-bundle}
                          #{})
        (let [entity (get (all-entities)
                          (-> scope-repr
                              :path
                              second
                              keyword))]
          (gen-capabilities-for-entity-and-accesses
           entity
           (:access scope-repr))))
    ;; typically: ["private-intel"]
    1 (->> (all-entities)
           vals
           (map #(gen-capabilities-for-entity-and-accesses % (:access scope-repr)))
           unionize
           (set/union (if (contains? (:access scope-repr) :write)
                        #{:import-bundle}
                        #{})))
    #{}))

(defn gen-casebook-capabilities
  "given a scope representation whose root-scope is casebook generate
  capabilities"
  [scope-repr]
  (gen-capabilities-for-entity-and-accesses
   (:casebook (all-entities))
   (:access scope-repr)))

(defn gen-assets-capabilities
  "Generate capabilities for the root-scope 'asset'."
  [scope-repr]
  (->> [:asset :asset-mapping :asset-properties :target-record]
       (select-keys (all-entities))
       vals
       (map #(gen-capabilities-for-entity-and-accesses
              % (:access scope-repr)))
       unionize))

(defn scope-to-capabilities
  "given a scope generate capabilities"
  [scope get-in-config]
  (let [scope-repr (scopula/to-scope-repr scope)]
    (condp = (first (:path scope-repr))
      (entity-root-scope get-in-config)   (gen-entity-capabilities scope-repr)
      (casebook-root-scope get-in-config) (gen-casebook-capabilities scope-repr)
      (assets-root-scope get-in-config)   (gen-assets-capabilities scope-repr)
      #{})))

(defn scopes-to-capabilities
  "given a seq of scopes generate a set of capabilities"
  [scopes get-in-config]
  (->> scopes
       (map #(scope-to-capabilities % get-in-config))
       unionize))

(defn iroh-claim
  "JWT specific claims for iroh are URIs

  For example:

  https://schemas.cisco.com/iroh/identity/claims/user/id
  https://schemas.cisco.com/iroh/identity/claims/user/name
  https://schemas.cisco.com/iroh/identity/claims/user/email
  https://schemas.cisco.com/iroh/identity/claims/org/id
  https://schemas.cisco.com/iroh/identity/claims/roles
  https://schemas.cisco.com/iroh/identity/claims/oauth/client/id

  See https://github.com/threatgrid/iroh/issues/1707

  Note iroh-claim are strings not keywords because from
  https://clojure.org/reference/reader
  '/' has special meaning.
  "
  [keyword-name get-in-config]
  (str (claim-prefix get-in-config) "/" keyword-name))

(defn unlimited-client-ids
  "Retrieves and parses unlimited client-ids defined in the properties"
  [get-in-config]
  (some-> (get-in-config
            [:ctia :http :rate-limit :unlimited :client-ids])
          (string/split #",")
          set))

(defn parse-unlimited-props
  [get-in-config]
  (let [client-ids (unlimited-client-ids get-in-config)]
    (cond-> {}
      (seq client-ids) (assoc :client-ids client-ids))))

(defrecord JWTIdentity [jwt unlimited-fn get-in-config]
  IIdentity
  (authenticated? [_]
    true)
  (client-id [_]
    (get jwt (iroh-claim "oauth/client/id" get-in-config)))
  (login [_]
    (:sub jwt))
  (groups [_]
    (remove nil? [(get jwt (iroh-claim "org/id" get-in-config))]))
  (allowed-capabilities [_]
    (let [scopes (set (get jwt (iroh-claim "scopes" get-in-config)))]
      (scopes-to-capabilities scopes get-in-config)))
  (capable? [this required-capabilities]
    (set/subset? (as-set required-capabilities)
                 (auth/allowed-capabilities this)))
  (rate-limit-fn [_ limit-fn]
    (when (not (unlimited-fn jwt))
      limit-fn)))

(defn unlimited?
  [unlimited-properties get-in-config jwt]
  (let [client-id (get jwt (iroh-claim "oauth/client/id" get-in-config))
        unlimited-client-ids (get unlimited-properties :client-ids)]
    (contains? unlimited-client-ids client-id)))

(defn wrap-jwt-to-ctia-auth
  [handler get-in-config]
  (let [unlimited-properties (parse-unlimited-props get-in-config)]
    (fn [request]
      (let [processed-request
            (if-let [jwt (:jwt request)]
              ;; JWT present - use JWT identity
              (let [identity
                    (->JWTIdentity jwt (partial unlimited? unlimited-properties get-in-config) get-in-config)]
                (assoc request
                       :identity  identity
                       :client-id (auth/client-id identity)
                       :login     (auth/login identity)
                       :groups    (auth/groups identity)))
              ;; No JWT - pass request unchanged
              request)]
        (handler processed-request)))))
