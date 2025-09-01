# JVM Specific Notes

`cli-tools` is normally used with Babashka; Babashka bundles, inside its executable,
[many useful libraries](https://book.babashka.org/#libraries), including several needed by `cli-tools`.

If you are using `cli-tools` in a Clojure (for the JVM) application, you will need to supply those otherwise-bundled libraries:

    org.clojure/tools.cli    {:mvn/version "1.1.230"}
    babashka/fs              {:mvn/version "0.5.24"}

If using the [Job Board](jobs.md):

    babashka/process         {:mvn/version "0.5.22"}

If using [zsh completions](completions.md):

    selmer/selmer            {:mvn/version "1.12.61"}

If [defining commands using babaska.cli](babashka-cli.md):

    org.babashka/cli {:mvn/version "0.8.66"}
