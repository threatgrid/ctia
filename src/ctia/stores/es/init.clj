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
   [schema-tools.core :as st]
   [ctim.schemas.actor :as actor]
   [ctim.schemas.attack-pattern :as attack]
   [ctim.schemas.campaign :as campaign]
   [ctim.schemas.coa :as coa]

   [ctia.actor.store.es :refer [->ActorStore]]
   [ctia.attack-pattern.store.es :refer [->AttackPatternStore]]
   [ctia.campaign.store.es :refer [->CampaignStore]]
   [ctia.coa.store.es :refer [->COAStore]]
   [ctia.data-table.store.es :refer [->DataTableStore]]
   [ctia.event.store.es :refer [->EventStore]]
   [ctia.exploit-target.store.es :refer [->ExploitTargetStore]]
   [ctia.feedback.store.es :refer [->FeedbackStore]]
   [ctia.identity.store.es :refer [->IdentityStore]]
   [ctia.incident.store.es :refer [->IncidentStore]]
   [ctia.indicator.store.es :refer [->IndicatorStore]]
   [ctia.investigation.store.es :refer [->InvestigationStore]]
   [ctia.judgement.store.es :refer [->JudgementStore]]
   [ctia.malware.store.es :refer [->MalwareStore]]
   [ctia.relationship.store.es :refer [->RelationshipStore]]
   [ctia.casebook.store.es :refer [->CasebookStore]]
   [ctia.sighting.store.es :refer [->SightingStore]]
   [ctia.tool.store.es :refer [->ToolStore]]))


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
  {:actor          (make-factory ->ActorStore)
   :attack-pattern (make-factory ->AttackPatternStore)
   :campaign       (make-factory ->CampaignStore)
   :coa            (make-factory ->COAStore)
   :data-table     (make-factory ->DataTableStore)
   :event          (make-factory ->EventStore)
   :exploit-target (make-factory ->ExploitTargetStore)
   :feedback       (make-factory ->FeedbackStore)
   :identity       (make-factory ->IdentityStore)
   :incident       (make-factory ->IncidentStore)
   :indicator      (make-factory ->IndicatorStore)
   :investigation  (make-factory ->InvestigationStore)
   :judgement      (make-factory ->JudgementStore)
   :malware        (make-factory ->MalwareStore)
   :relationship   (make-factory ->RelationshipStore)
   :casebook       (make-factory ->CasebookStore)
   :sighting       (make-factory ->SightingStore)
   :tool           (make-factory ->ToolStore)})

(defn init-store! [store-kw]
  (when-let [factory (get factories store-kw)]
    (factory store-kw)))
