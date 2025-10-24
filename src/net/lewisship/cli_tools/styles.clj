(ns net.lewisship.cli-tools.styles
  "Defines styles (ANSI fonts) for output from cli-tools."
  {:added "0.16.0"})

(def default-styles
  {:invalid-input       :yellow
   :possible-completion :italic
   :default-value       :bold
   :error-label         :bold.red
   :tool-name           :bold.green
   :command-path        :bold.green
   :option-label        :bold
   :option-default      :italic
   :parse-error         :red
   :missing-doc         :yellow.italic
   :command             :bold
   :subgroup-label      :italic
   :search-term         :italic
   :option-name         :bold.green
   :no-command-match    :bold
   :unknown-term        :red})

(def ^:dynamic *style-overrides* default-styles)

(defn style
  "Retrieves a style; searches in *default-styles* first and, if not found, then in the default-styles."
  [k]
  (or (get *style-overrides* k)
      (get default-styles k)))
