(ns ctia.import.capec
  (:require [clojure.xml :as xml]
            [ctim.schemas.ttp :as ttp]
            [schema.core :as s]
            [ctia.lib.time :as time]
            [clojure.pprint :refer [pprint]]
            [ctia.import.threatgrid.http :as http]))

(defn capec-tag [kw]
  (keyword (str "capec" kw)))

(defn sieve
  "Helps keep clojure.xml traversal code more concise, with sensible defaults."
  ([e tag] (sieve e tag first :content))
  ([e tag scope] (sieve e tag scope :content))
  ([e tag scope child] (->> e
                            (filter #(= (:tag %) (capec-tag tag)))
                            scope
                            child)))

(defn attack-pattern-elements [doc]
  (-> doc
      :content
      (sieve :Attack_Patterns)
      (sieve :Attack_Pattern seq seq)))

(s/defschema ExploitTargetSummary
  {(s/required-key "exploit_target_id") s/Str})

(s/defschema ResourcesSummary
  {(s/required-key "tools") [{:description (s/maybe s/Str)}]})

(s/defschema AttackPatternSummary
  {(s/required-key "title") s/Str
   (s/required-key "description") s/Str
   (s/required-key "capec_id") s/Str
   (s/required-key "exploit_targets") [ExploitTargetSummary]
   (s/required-key "resources") ResourcesSummary
   (s/required-key "indicators") [(s/maybe s/Str)]})

(s/defn attack-pattern->ctim-behavior :- ttp/Behavior
  [{:keys [title capec_id]
    :as ap}]
  {:attack_patterns [{:description title
                      :capec_id capec_id}]})

(s/defn ^:always-validate attack-pattern->ctim-ttp :- ttp/NewTTP
  [{:keys [title capec_id description exploit_targets resources indicators]
    :as ap}]
  {:title title
   :description description
   :tlp "white"
   :behavior (attack-pattern->ctim-behavior ap)
   :exploit_targets exploit_targets
   :resources resources
   :source "capec_v2.8"
   :ttp_type "CAPEC TTP"
   :indicators indicators})

(defn description [attack-pattern]
  (-> attack-pattern
      :content
      (sieve :Description)
      (sieve :Summary)
      (sieve :Text)
      first))

(defn related-cwe-ids [attack-pattern]
  (map (fn [weakness]
         (-> weakness
             :content
             (sieve :CWE_ID)
             first))
       (-> attack-pattern
           :content
           (sieve :Related_Weaknesses)
           (sieve :Related_Weakness seq seq))))

(defn cwe-ids->exploit-targets [cwe-ids]
  (vec (for [id cwe-ids]
         {:exploit_target_id (str "https://cwe.mitre.org/data/definitions/"
                                  id
                                  ".html")})))

(defn resources-required [attack-pattern]
  (-> attack-pattern
      (sieve :Resources_Required)
      (sieve :Text)
      first))

(defn skills-required [attack-pattern]
  (-> attack-pattern
      :content
      (sieve :Attacker_Skills_or_Knowledge_Required)
      (sieve :Attacker_Skill_or_Knowledge_Required)
      (sieve :Skill_or_Knowledge_Type)
      (sieve :Text)
      first))

(defn punctuate [s]
  (if (nil? s)
    ""
    (if (.endsWith s ".")
      (str s " ")
      (str s ". "))))

(defn resources [attack-pattern]
  {:tools [{:description (str (punctuate (resources-required attack-pattern))
                              (skills-required attack-pattern))}]})

(defn make-attack-pattern
  [ap]
  {:title (-> ap :attrs :Name)
   :description (str (description ap))
   :capec_id (-> ap :attrs :ID)
   :exploit_targets (-> ap related-cwe-ids cwe-ids->exploit-targets)
   :resources (resources ap)
   :indicators []})

(defn capec-tags [capec]
  (->> (attack-pattern-elements capec)
       first
       :content
       (map #(:tag %))))

(defn ctia-post [ttps & {:keys [ctia-uri api-key]}]
  (println "called ctia-post for" (count ttps) "ttps.")
  (http/post-to-ctia ttps :ttps ctia-uri))

(defn -main [& [file-path ctia-uri api-key :as _args_]]
  (println "Running CAPEC TTP Importer.")
  (assert (not-empty file-path) "File path must be set.")
  (assert (not-empty ctia-uri) "URI must be set.")
  (let [ttps (->> (xml/parse file-path)
                  attack-pattern-elements
                  (map make-attack-pattern)
                  (map attack-pattern->ctim-ttp))]
    (-> ttps
        (ctia-post :ctia-uri ctia-uri
                   :api-key (not-empty api-key))
        pprint)))

(comment
  (let [ttp1 {:title "ttp"
              :description "description"
              :ttp_type "foo"
              :indicators [{:indicator_id "indicator-1"}
                           {:indicator_id "indicator-2"}]
              :exploit_targets [{:exploit_target_id "exploit-target-123"}
                                {:exploit_target_id "exploit-target-234"}]
              :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"
                           :end_time "2016-07-11T00:40:48.212-00:00"}}
        ttp2 {:title "ttp2"
              :description "description"
              :ttp_type "foo"
              :indicators [{:indicator_id "indicator-1"}
                           {:indicator_id "indicator-2"}]
              :exploit_targets [{:exploit_target_id "exploit-target-123"}
                                {:exploit_target_id "exploit-target-234"}]
              :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"
                           :end_time "2016-07-11T00:40:48.212-00:00"}}
        ]
    (pprint
     (http/post-to-ctia [ttp1 ttp2] :ttps "http://localhost:3000"))))
