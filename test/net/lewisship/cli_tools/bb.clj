(ns net.lewisship.cli-tools.bb
  "Commands defined using babashka.cli")

(defn alpha
  "This command take a symbol and a long."
  {:org.babashka/cli {:coerce {:a :symbol
                               :b :long}}}
  [args]
  (prn :in :alpha :args args))

(defn find-command
  "This finds something using a long."
  {:org.babashka/cli {:coerce  {:id :long}
                      :title   "Find by id"
                      :command "find"}}
  [args]
  (prn :in :find-command :arg args))
