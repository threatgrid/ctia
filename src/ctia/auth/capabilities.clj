(ns ctia.auth.capabilities
  (:require
   [ctia.entity.entities
    :refer [entities]]
   [clojure.set :as set]
   [clojure.string :as string]))

(def all-entities
  (assoc entities
         :verdict
         {:plural :verdicts
          :entity :verdict}))

(def prefixes
  {:read #{:read :search :list}
   :write #{:create :delete}})

(defn gen-capabilities-for-entity-and-accesses
  "Given an entity and a set of access (:read or :write) generate a set of
  capabilities"
  [{:keys [entity plural]}
   accesses]
  (set (for [access accesses
             prefix (get prefixes access)]
         (keyword (str (name prefix) "-"
                       (if (= :list prefix)
                         (name plural)
                         (name entity)))))))

(def all-entity-capabilities
  (apply set/union
         (map #(gen-capabilities-for-entity-and-accesses
                % (keys prefixes))
              (vals all-entities))))

(def misc-capabilities
  #{:read-verdict
    ;; Other
    :developer
    :specify-id
    :import-bundle})

(def all-capabilities
  (set/union
   misc-capabilities
   all-entity-capabilities))

(comment

  ;; A nice to have feature to help provide a list of meaningful scopes in the
  ;; documentation
  ;; It shouldn't be part of the real code so let's keep it in a comment.
  ;;
  ;; Typically it was used to provide the full list of scopes here:
  ;;
  ;; https://github.com/threatgrid/iroh-ui/issues/492#issuecomment-402153118

  (defn cap-to-scope
    [k]
    (let [[loc p] (string/split (name k) #"-" 2)
          suff (if (#{"list" "search" "read"} loc)
                 ":read"
                 ":write")
          n (if (= "list" loc)
              (subs p 0 (dec (count p)))
              p)]
      (if n
        (str "private-intel/" n suff)
        (str "private-intel/" loc))))

  (sort (set/union
         (set (map cap-to-scope all-capabilities)))))

(def default-capabilities
  {:user
   #{:read-actor
     :read-asset
     :read-attack-pattern
     :read-campaign
     :read-coa
     :read-feedback
     :read-incident
     :read-indicator
     :list-indicators
     :read-judgement
     :list-judgements
     :read-malware
     :read-relationship
     :list-relationships
     :read-sighting
     :list-sightings
     :read-identity-assertion
     :list-identity-assertions
     :read-tool
     :read-verdict
     :read-weakness
     :list-weaknesses
     :import-bundle}
   :admin
   all-capabilities})
