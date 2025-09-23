(ns ctia.auth.jwt
  (:refer-clojure :exclude [identity])
  (:require
   [cheshire.core :as json]
   [clj-http.client :as http]
   [clj-jwt.key :refer [public-key]]
   [clj-momo.lib.set :refer [as-set]]
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
                  (map (fn [[k v]] [k (try (public-key v)
                                          (catch Exception e
                                            (log/errorf "Could not read the public key: %s" v)
                                            (throw e)))]))
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
  [url & [{:keys [socket-timeout connection-timeout]
           :or {socket-timeout 5000
                connection-timeout 5000}}]]
  (try
    (log/debugf "Fetching JWKS from %s" url)
    (let [response (http/get url
                            {:as :json
                             :throw-exceptions false
                             :socket-timeout socket-timeout
                             :connection-timeout connection-timeout})
          status (:status response)]
      (if (<= 200 status 299)
        (do
          (log/debugf "Successfully fetched JWKS from %s (status %d)" url status)
          (:body response))
        (do
          (log/errorf "Failed to fetch JWKS from %s: status %d" url status)
          nil)))
    (catch Exception ex
      (log/errorf ex "Exception fetching JWKS from %s" url)
      nil)))

(defn- build-key-map
  "Build a map of kid -> PublicKey from JWKS response"
  [jwks-response]
  (when jwks-response
    (try
      (let [jwks-keys (:keys jwks-response)]
        (when-not (sequential? jwks-keys)
          (log/errorf "JWKS response does not contain a valid 'keys' array: %s"
                     (pr-str jwks-response))
          (throw (ex-info "Invalid JWKS format - missing or invalid 'keys' array"
                          {:jwks-response jwks-response})))

        (log/debugf "Processing %d keys from JWKS response" (count jwks-keys))

        (reduce (fn [acc jwk]
                  (cond
                    ;; No kid - skip but warn
                    (not (:kid jwk))
                    (do
                      (log/warnf "JWK missing 'kid' field, skipping: %s"
                                (pr-str (select-keys jwk [:kty :use :alg])))
                      acc)

                    ;; Has kid, try to convert
                    :else
                    (if-let [public-key (jwk->public-key jwk)]
                      (do
                        (log/debugf "Successfully added key with kid: %s, type: %s"
                                   (:kid jwk) (:kty jwk))
                        (assoc acc (:kid jwk) public-key))
                      (do
                        (log/warnf "Failed to convert JWK to public key - kid: %s, kty: %s, alg: %s"
                                  (:kid jwk) (:kty jwk) (:alg jwk))
                        acc))))
                {}
                jwks-keys))
      (catch Exception ex
        (log/errorf ex "Failed to build key map from JWKS")
        {}))))

;; Background JWKS refresh system and configuration
(defonce ^:private jwks-state (atom {:keys {}
                                     :refresh-future nil
                                     :config nil
                                     :timeout-config {:socket-timeout 5000
                                                      :connection-timeout 5000}
                                     :refresh-interval (* 5 60 1000)})) ; 5 minutes default

(defn set-jwks-timeout-config!
  "Set JWKS HTTP timeout configuration"
  [{:keys [socket-timeout connection-timeout]}]
  (swap! jwks-state update :timeout-config merge
         (cond-> {}
           socket-timeout (assoc :socket-timeout socket-timeout)
           connection-timeout (assoc :connection-timeout connection-timeout))))

(defn- fetch-and-build-key-map
  "Fetch JWKS and build key map"
  [url]
  (-> url
      (fetch-jwks (:timeout-config @jwks-state))
      build-key-map))

(defn- refresh-all-jwks-keys
  "Refresh all JWKS keys from configured URLs"
  [jwks-urls-config]
  (when (seq jwks-urls-config)
    (try
      (let [all-urls (distinct (apply concat (vals jwks-urls-config)))]
        (log/infof "Refreshing JWKS keys from %d URLs for issuers: %s"
                  (count all-urls) (pr-str (keys jwks-urls-config)))

        (let [new-keys (reduce (fn [acc url]
                                (try
                                  (let [keys-from-url (fetch-and-build-key-map url)]
                                    (when (seq keys-from-url)
                                      (log/debugf "Loaded %d keys from %s"
                                                 (count keys-from-url) url))
                                    (merge acc keys-from-url))
                                  (catch Exception ex
                                    (log/errorf ex "Failed to refresh keys from %s" url)
                                    acc)))
                              {}
                              all-urls)]
          (log/infof "Refreshed %d total keys with kids: %s"
                    (count new-keys) (pr-str (keys new-keys)))
          (swap! jwks-state assoc :keys new-keys)
          new-keys))
      (catch Exception ex
        (log/errorf ex "Failed to refresh JWKS keys")
        nil))))

(defn- start-jwks-refresh-scheduler
  "Start background scheduler to refresh JWKS keys"
  [jwks-urls-config refresh-interval-ms]
  (when (seq jwks-urls-config)
    (log/infof "Starting JWKS refresh scheduler with %d ms interval" refresh-interval-ms)
    (let [executor (java.util.concurrent.Executors/newSingleThreadScheduledExecutor
                    (reify java.util.concurrent.ThreadFactory
                      (newThread [_ r]
                        (let [t (Thread. r "jwks-refresh")]
                          (.setDaemon t true)
                          t))))
          future (.scheduleAtFixedRate executor
                                      #(refresh-all-jwks-keys jwks-urls-config)
                                      refresh-interval-ms
                                      refresh-interval-ms
                                      java.util.concurrent.TimeUnit/MILLISECONDS)]
      (swap! jwks-state assoc :refresh-future future)
      future)))

(defn stop-jwks-refresh-scheduler
  "Stop the JWKS refresh scheduler"
  []
  (when-let [future (:refresh-future @jwks-state)]
    (log/info "Stopping JWKS refresh scheduler")
    (.cancel future true)
    (swap! jwks-state assoc :refresh-future nil)))

(defn initialize-jwks-keys
  "Initialize JWKS keys at startup and start background refresh.
   refresh-interval-ms defaults to 5 minutes."
  ([jwks-urls-config]
   (initialize-jwks-keys jwks-urls-config (* 5 60 1000))) ; 5 minutes default
  ([jwks-urls-config refresh-interval-ms]
   (when (seq jwks-urls-config)
     (log/info "Initializing JWKS keys at startup")
     ;; Stop any existing scheduler
     (stop-jwks-refresh-scheduler)
     ;; Store config
     (swap! jwks-state assoc :config jwks-urls-config)
     ;; Load keys immediately
     (refresh-all-jwks-keys jwks-urls-config)
     ;; Start background refresh
     (start-jwks-refresh-scheduler jwks-urls-config refresh-interval-ms))))

(defn get-jwks-key-by-kid
  "Get a JWKS public key by kid from the preloaded keys.
   Returns nil if not found."
  [kid]
  (when kid
    (get-in @jwks-state [:keys kid])))



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

(defn wrap-jwt-to-ctia-auth
  [handler get-in-config]
  (let [unlimited-client-ids (some-> (get-in-config
                                       [:ctia :http :rate-limit :unlimited :client-ids])
                                     (string/split #",")
                                     set)
        unlimited-fn (fn [jwt]
                       (let [client-id (get jwt (iroh-claim "oauth/client/id" get-in-config))]
                         (contains? unlimited-client-ids client-id)))]
    (fn [request]
      (handler
       (if-let [jwt (:jwt request)]
         (let [identity
               (->JWTIdentity jwt unlimited-fn get-in-config)]
           (assoc request
                  :identity  identity
                  :client-id (auth/client-id identity)
                  :login     (auth/login identity)
                  :groups    (auth/groups identity)))
         request)))))
