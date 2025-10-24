# Styling cli-tools

Beauty is in the eye of the beholder; `cli-tools` makes a default
set of choices for what kind of colors and fonts to use
for its output, but this can be overridden.

Styles are defined by two vars in namespace `net.lewisship.cli-tools.styles`; each is a
map from a usage name (such as :tool-name-label) to a
[font definition](https://cljdoc.org/d/org.clj-commons/pretty/3.6.7/api/clj-commons.ansi#compose).

* `default-styles` are the defaults for all usages
* `*style-overrides*` contains overrides for `default-styles` (and is dynamic)

By overriding or rebinding this var, the fonts can be overridden.

The most common usages to override are:

- :tool-name (default :bold.green) used when writing the name of the tool itself
- :command-path (default :bold.green) used when writing the command path

Example:

```clojure
    (binding [style/*style-overrides* {:tool-name :inverse.bold}]
      (cli-tools/dispatch { ... }))

```
