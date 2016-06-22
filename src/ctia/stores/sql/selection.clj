(ns ctia.stores.sql.selection)

(defn judgement-entity-values
  "Selects judgement values that are meant to be stored in the DB."
  [judgements]
  (map
   #(select-keys %
                 [:id
                  :type
                  :tlp
                  :schema_version
                  :observable
                  :disposition
                  :disposition_name
                  :source
                  :priority
                  :confidence
                  :severity
                  :valid_time
                  :reason
                  :source_uri
                  :reason_uri
                  :owner
                  :created])
   judgements))
