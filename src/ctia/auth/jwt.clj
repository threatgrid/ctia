(ns ctia.auth.jwt
  (:refer-clojure :exclude [identity])
  (:require [cheshire.core :as json]
            [clj-jwt.key :refer [public-key]]
            [clj-momo.lib.set :refer [as-set]]
            [clojure.set :as set]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [ctia.auth :as auth :refer [IIdentity]]
            [ctia.auth.capabilities
             :refer
             [all-entities gen-capabilities-for-entity-and-accesses]]
            [ctia.properties :as p]
            [ring.util.http-response :as resp]
            [scopula.core :as scopula]))

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


(defn jwt-error-handler
  "Return an `unauthorized` HTTP response
  and log the error along debug infos"
  [error-msg infos]
  (let [err {:error :invalid_jwt
             :error_description error-msg}]
    (log/info error-msg (pr-str (into infos err)))
    (resp/unauthorized (json/generate-string err))))

(defn entity-root-scope []
  (p/get-in-global-properties [:ctia :auth :entities :scope]
                              "private-intel"))

(defn casebook-root-scope []
  (p/get-in-global-properties [:ctia :auth :casebook :scope]
                              "casebook"))

(def claim-prefix
  (p/get-in-global-properties [:ctia :http :jwt :claim-prefix]
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
        (let [entity (get all-entities
                          (-> scope-repr
                              :path
                              second
                              keyword))]
          (gen-capabilities-for-entity-and-accesses
           entity
           (:access scope-repr))))
    ;; typically: ["private-intel"]
    1 (->> all-entities
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
   (:casebook all-entities)
   (:access scope-repr)))

(defn scope-to-capabilities
  "given a scope generate capabilities"
  [scope]
  (let [scope-repr (scopula/to-scope-repr scope)]
    (condp = (first (:path scope-repr))
      (entity-root-scope)   (gen-entity-capabilities scope-repr)
      (casebook-root-scope) (gen-casebook-capabilities scope-repr)
      #{})))

(defn scopes-to-capabilities
  "given a seq of scopes generate a set of capabilities"
  [scopes]
  (->> scopes
       (map scope-to-capabilities)
       unionize))

(defn iroh-claim
  "JWT specific claims for iroh are URIs

  For example:

  https://schemas.cisco.com/iroh/identity/claims/user/id
  https://schemas.cisco.com/iroh/identity/claims/user/name
  https://schemas.cisco.com/iroh/identity/claims/user/email
  https://schemas.cisco.com/iroh/identity/claims/org/id
  https://schemas.cisco.com/iroh/identity/claims/roles

  See https://github.com/threatgrid/iroh/issues/1707

  Note iroh-claim are strings not keywords because from
  https://clojure.org/reference/reader
  '/' has special meaning.
  "
  [keyword-name]
  (str claim-prefix "/" keyword-name))

(defn unlimited-client-ids
  "Retrieves and parses unlimited client-ids defined in the properties"
  []
  (some-> (p/get-in-global-properties
            [:ctia :http :rate-limit :unlimited :client-ids])
          (string/split #",")
          set))

(defn parse-unlimited-props
  []
  (let [client-ids (unlimited-client-ids)]
    (cond-> {}
      (seq client-ids) (assoc :client-ids client-ids))))

(defrecord JWTIdentity [jwt unlimited-fn]
  IIdentity
  (authenticated? [_]
    true)
  (login [_]
    (:sub jwt))
  (groups [_]
    (remove nil? [(get jwt (iroh-claim "org/id"))]))
  (allowed-capabilities [_]
    (let [scopes (set (get jwt (iroh-claim "scopes")))]
      (scopes-to-capabilities scopes)))
  (capable? [this required-capabilities]
    (set/subset? (as-set required-capabilities)
                 (auth/allowed-capabilities this)))
  (rate-limit-fn [_ limit-fn]
    (when (not (unlimited-fn jwt))
      limit-fn)))

(defn unlimited?
  [unlimited-properties jwt]
  (let [client-id (get jwt (iroh-claim "oauth/client/id"))
        unlimited-client-ids (get unlimited-properties :client-ids)]
    (contains? unlimited-client-ids client-id)))

(defn wrap-jwt-to-ctia-auth
  [handler]
  (let [unlimited-properties (parse-unlimited-props)]
    (fn [request]
      (handler
       (if-let [jwt (:jwt request)]
         (let [identity
               (->JWTIdentity jwt (partial unlimited? unlimited-properties))]
           (assoc request
                  :identity identity
                  :login    (auth/login identity)
                  :groups   (auth/groups identity)))
         request)))))
