(ns ctia.task.migration.migrations.describe-test
  (:require  [ctia.task.migration.migrations.describe :as sut]
             [clojure.test :refer [deftest testing is]]
             [clojure.math.combinatorics :as combo]))

;; describibable source entities
(def actor
  {:id "http://ex.tld/ctia/actor/actor-5023697b-3857-4652-9b53-ccda297f9c3e"
   :type "actor"
   :schema_version "1.0.0"
    :title "title"
   :description "description"
   :short_description "short description"
   :actor_type "Hacker",
   :confidence "High",
   :source "a source"
   :valid_time {}})

(def campaign
  {:id "http://ex.tld/ctia/campaign/campaign-b1f8e40a-0e99-48fc-bb12-32a65421cfb5"
   :type "campaign"
   :schema_version "1.0.0"
   :campaign_type "anything goes here"
   :valid_time {}
   :title "campaign"
   :description "description"
   :short_description "short description"})

;; non describable source entities
(def attack-pattern
  {:name "Clear Command History",
   :description
   "macOS and Linux both keep track of the commands users type in their terminal so that users can easily remember what they've done. These logs can be accessed in a few different ways. While logged in, this command history is tracked in a file pointed to by the environment variable <code>HISTFILE</code>. When a user logs off a system, this information is flushed to a file in the user's home directory called <code>~/.bash_history</code>. The benefit of this is that it allows users to go back to commands they've used before in different sessions. Since everything typed on the command-line is saved, passwords passed in on the command line are also saved. Adversaries can abuse this by searching these files for cleartext passwords. Additionally, adversaries can use a variety of methods to prevent their own commands from appear in these logs such as <code>unset HISTFILE</code>, <code>export HISTFILESIZE=0</code>, <code>history -c</code>, <code>rm ~/.bash_history</code>.\n\nDetection: User authentication, especially via remote terminal services like SSH, without new entries in that user's <code>~/.bash_history</code> is suspicious. Additionally, the modification of the HISTFILE and HISTFILESIZE environment variables or the removal/clearing of the <code>~/.bash_history</code> file are indicators of suspicious activity.\n\nPlatforms: Linux, MacOS, OS X\n\nData Sources: Authentication logs, File monitoring",
   :schema_version "1.0.0",
   :type "attack-pattern",
   :external_ids
   ["attack-pattern--b344346f-1321-4639-abd0-df3c95f1c0b0"],
   :external_references
   [{:source_name "mitre-attack",
     :url "https://attack.mitre.org/wiki/Technique/T1146",
     :external_id "T1146"}],
   :x_mitre_platforms ["Linux" "MacOS" "OS X"],
   :id
   "https://intel.amp.cisco.com:443/ctia/attack-pattern/attack-pattern-22bb86b3-900f-4a3f-b429-189f64a47904",
   :tlp "green",
   :x_mitre_data_sources ["Authentication logs" "File monitoring"],
   :kill_chain_phases
   [{:kill_chain_name "mitre-attack", :phase_name "defense-evasion"}
    {:kill_chain_name "lockheed-martin-cyber-kill-chain",
     :phase_name "installation"}],
   :groups ["tenzin"],
   :timestamp "2017-07-28T15:03:13.641Z",
   :owner "Tenzin"})

(def malware
  {:name "ViperRAT",
   :description
   "[ViperRAT](https://attack.mitre.org/software/S0506) is sophisticated surveillanceware that has been in operation since at least 2015 and was used to target the Israeli Defense Force.(Citation: Lookout ViperRAT) ",
   :labels ["malware"],
   :abstraction_level "family",
   :schema_version "1.0.23",
   :type "malware",
   :source "MITRE Device Access",
   :external_ids
   ["malware--f666e17c-b290-43b3-8947-b96bd5148fbb"
    "hydrant-bade42ca32e73a13467cccc7ed827662d4330537490dc80125c67580dea7b3d5"
    "ATT&CK-S0506"],
   :external_references
   [{:source_name "mitre-attack",
     :url "https://attack.mitre.org/software/S0506",
     :external_id "S0506"}
    {:source_name "Lookout ViperRAT",
     :description
     "M. Flossman. (2017, February 16). ViperRAT: The mobile APT targeting the Israeli Defense Force that should be on your radar. Retrieved September 11, 2020.",
     :url "https://blog.lookout.com/viperrat-mobile-apt"}],
   :source_uri "https://attack.mitre.org",
   :id
   "https://intel.amp.cisco.com:443/ctia/malware/malware-01d0f465-7fd2-4e44-a7dc-8e982fbef7a9",
   :tlp "green",
   :groups ["tenzin"],
   :timestamp "2020-09-29T20:03:42.662Z",
   :owner "Tenzin"})

(def tool
  {:name "PoshC2",
   :description
   "[PoshC2](https://attack.mitre.org/software/S0378) is an open source remote administration and post-exploitation framework that is publicly available on GitHub. The server-side components of the tool are primarily written in Python, while the implants are written in [PowerShell](https://attack.mitre.org/techniques/T1086). Although [PoshC2](https://attack.mitre.org/software/S0378) is primarily focused on Windows implantation, it does contain a basic Python dropper for Linux/macOS.(Citation: GitHub PoshC2)",
   :labels ["tool"],
   :schema_version "1.0.16",
   :type "tool",
   :x_mitre_aliases ["PoshC2"],
   :source "MITRE Enterprise ATT&CK",
   :external_ids
   ["tool--4b57c098-f043-4da2-83ef-7588a6d426bc"
    "hydrant-05856bc0326b15f3d99036be057f9f7d06794dc57cac2f8f9d21c365c03f4d44"
    "ATT&CK-S0378"],
   :external_references
   [{:source_name "mitre-attack",
     :url "https://attack.mitre.org/software/S0378",
     :external_id "S0378"}
    {:source_name "GitHub PoshC2",
     :description
     "Nettitude. (2018, July 23). Python Server for PoshC2. Retrieved April 23, 2019.",
     :url "https://github.com/nettitude/PoshC2_Python"}],
   :source_uri "https://attack.mitre.org",
   :id
   "https://intel.amp.cisco.com:443/ctia/tool/tool-0b32dbc5-7207-4ffd-a484-ec6465d62586",
   :tlp "green",
   :groups ["tenzin"],
   :timestamp "2020-03-30T02:37:23.626Z",
   :owner "Tenzin"})


(defn check-unchanged-fields
  [source migrated]
  (is (= (dissoc source :name :title :description :short_description)
         (dissoc migrated :name :title :description :short_description))
      "field not related to entity description shall be preserved."))

(defn mutate-fn
  [fields]
  (fn [entity] (apply dissoc entity fields)))

(defn mutant-generator
  [mutated-fields]
  (let [subsets (combo/subsets mutated-fields)
        mutate-fns (map mutate-fn subsets)]
    (fn [entity]
      (map #(% entity) mutate-fns))))

(defn has-mutation?
  [entity [field has?]]
  (= has? (contains? entity field)))

(defn is-mutant?
  [mutations entity]
  (every? #(has-mutation? entity %) mutations))

(deftest migrate-non-describable-test
  (testing "describe shall properly migrate non describable entities of type attack-pattern, malware and tool"
    (let [gen-mutants (mutant-generator [:name :description])
          source-entities (mapcat #(gen-mutants %) [attack-pattern malware tool])
          target-entities (map sut/describe source-entities)
          w-name-wo-desc? (partial is-mutant? {:name true :description false})
          wo-name-wo-desc? (partial is-mutant? {:name false :description false})]
      (assert (some w-name-wo-desc? source-entities))
      (assert (some wo-name-wo-desc? source-entities))
      (doseq [[source migrated] (zipmap source-entities target-entities)]
        (let [{src-name :name} source

              {migrated-name :name
               migrated-title :title
               migrated-desc :description
               migrated-short-desc :short_description} migrated]

          (is (nil? migrated-name))
          (is (seq migrated-title))
          (is (seq migrated-desc))
          (is (seq migrated-short-desc))

          (if src-name
            (is (= src-name migrated-title)
                "the name key must be renamed into title")
            (is (= "No title provided" migrated-title)
                "missing name on corrupted data should be fixed."))

          (when (w-name-wo-desc? source)
            (is (= migrated-title migrated-short-desc)
                "when provided, the title should be used as short_description on non describable entities"))

          (when (wo-name-wo-desc? source)
            (is (= "No description provided" migrated-desc migrated-short-desc)))

          (check-unchanged-fields source migrated))))))

(deftest migrate-describable-test
  (testing "describe shall properly migrate describable entities"
    (let [gen-mutants (mutant-generator [:title :description :short_description])
          source-entities (mapcat #(gen-mutants %) [actor campaign])
          target-entities (map sut/describe source-entities)
          w-title? (partial is-mutant? {:title true})
          wo-title? (partial is-mutant? {:title false})
          w-desc? (partial is-mutant? {:description true})
          w-short-desc? (partial is-mutant? {:short_description true})
          w-title-wo-desc-wo-short-desc? (partial is-mutant? {:title true
                                                              :description false
                                                              :short_description false})]
      (assert (some w-title? source-entities))
      (assert (some wo-title? source-entities))
      (assert (some w-desc? source-entities))
      (assert (some w-short-desc? source-entities))
      (assert (some w-title-wo-desc-wo-short-desc? source-entities))

      (doseq [[source migrated] (zipmap source-entities target-entities)]
        (let [{src-title :title
               src-desc :description
               src-short-desc :short_description} source

              {migrated-title :title
               migrated-desc :description
               migrated-short-desc :short_description} migrated]

          (is (seq migrated-title))
          (is (seq migrated-desc))
          (is (seq migrated-short-desc))

          (when (w-title? source)
            (is (= src-title migrated-title)))

          (when (w-desc? source)
            (is (= src-desc migrated-desc)))

          (when (w-short-desc? source)
            (is (= src-short-desc migrated-short-desc)))

          (when (wo-title? source)
            (is (= "No title provided" migrated-title)
                "missing name on corrupted data should be fixed."))

          (when (w-title-wo-desc-wo-short-desc? source)
            (is (= migrated-title migrated-short-desc)
                "when provided, the title should be used as short_description on non describable entities"))

          (check-unchanged-fields source migrated))))))
