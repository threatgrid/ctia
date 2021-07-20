((nil . ((cider-preferred-build-tool . "lein")
         (cider-default-cljs-repl . custom)
         (eval . (setq-local
                  cider-custom-cljs-repl-init-form "(do (require '[shadow.cljs.devtools.api :as shadow]) (require '[shadow.cljs.devtools.server :as server]) (server/start!) (shadow/watch :app) (shadow/nrepl-select :app))")))))
