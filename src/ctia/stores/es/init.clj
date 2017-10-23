(ns ctia.stores.es.init
  (:require
   [clj-momo.lib.es
    [conn :refer [connect]]
    [index :as es-index]
    [schemas :refer [ESConnState]]]
   [ctia.properties :refer [properties]]
   [ctia.stores.es
    [mapping :refer [store-mappings store-settings]]
    [store :as es-store]]
   [schema.core :as s]
   [schema-tools.core :as st]))


(s/defschema StoreProperties
  {:entity s/Keyword
   :indexname s/Str
   :shards s/Num
   :replicas s/Num
   s/Keyword s/Any})


(s/defn init-store-conn :- ESConnState
  "initiate an ES store connection, returning a map containing a
   connection manager and dedicated store index properties"
  [{:keys [entity indexname shards replicas] :as props} :- StoreProperties]

  (let [settings {:number_of_shards shards
                  :number_of_replicas replicas}]
    {:index indexname
     :props props
     :config {:settings (merge store-settings settings)
              :mappings (get store-mappings entity)}
     :conn (connect props)}))


(s/defn init-es-conn! :- ESConnState
  "initiate an ES Store connection,
   put the index template, return an ESConnState"
  [properties :- StoreProperties]
  (let [{:keys [conn index config] :as conn-state} (init-store-conn properties)]
    (es-index/create-template! conn index config)
    conn-state))


(s/defn get-store-properties :- StoreProperties
  "Lookup the merged store properties map"
  [store-kw :- s/Keyword]
  (let [props @properties]
    (merge
     {:entity store-kw}
     (get-in props [:ctia :store :es :default] {})
     (get-in props [:ctia :store :es store-kw] {}))))


(defn- make-factory
  "Return a store instance factory. Most of the ES stores are
  initialized in a common way, so this is used to remove boiler-plate
  code."
  [store-constructor]
  (fn store-factory [store-kw]
    (-> (get-store-properties store-kw)
        init-es-conn!
        store-constructor)))

(def ^:private factories
  {:actor          (make-factory es-store/->ActorStore)
   :attack-pattern (make-factory es-store/->AttackPatternStore)
   :campaign       (make-factory es-store/->CampaignStore)
   :coa            (make-factory es-store/->COAStore)
   :data-table     (make-factory es-store/->DataTableStore)
   :event          (make-factory es-store/->EventStore)
   :exploit-target (make-factory es-store/->ExploitTargetStore)
   :feedback       (make-factory es-store/->FeedbackStore)
   :identity       (make-factory es-store/->IdentityStore)
   :incident       (make-factory es-store/->IncidentStore)
   :indicator      (make-factory es-store/->IndicatorStore)
   :judgement      (make-factory es-store/->JudgementStore)
   :malware        (make-factory es-store/->MalwareStore)
   :relationship   (make-factory es-store/->RelationshipStore)
   :sighting       (make-factory es-store/->SightingStore)
   :tool           (make-factory es-store/->ToolStore)})

(defn init-store! [store-kw]
  (when-let [factory (get factories store-kw)]
    (factory store-kw)))
