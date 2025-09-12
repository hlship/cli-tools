# Extra Options

The `dispatch` functions start by building a call to `clojure.tools.cli/parse-opts`.

By default, it only allows for the `-C / --color`, `-N / --no-color`, and `-h / --help` options.

You can use the  :extra-tools-options dispatch options key to specify additional options to parse.

When doing so, you must also supply a :tool-options-handler function.

For example, you may want to add a `-v / --version` option.

You would specify this in your call to `dispatch`:

```clojure
(cli-tools/dispatch
  {...
   :extra-tool-options [["-v" "--version" "Display tool version"]]
   :tool-options-handler my-handler
   ...})
```

Your tool options handler should do what's needed when a corresponding option is provided:

```clojure
(defn my-handler
  [{:keys [version]} dispatch-options callback]
  (when version
    (abort 0 "Version: " (slurp (io/resources "tool-version.txt"))))

  (callback))
```

This handler is passed the options map, the dispatch options, and a callback.
When the `--version` option is specified, it reads a file (presumably, you have a step in
your build process to add this file) and prints the version number stored within.

Otherwise, the normal path is to invoke the callback.

In some cases, your handler may change or replace the remaining command line arguments; these are provided in the
:arguments key of `dispatch-options`; you can change the arguments by passing a seq of arguments
to the callback.

Why would you do this?  It might be easier to implement the behavior of some commands as a `defcommand` and 
your options handler can change the arguments to point to the command. In fact, this is what 
`dispatch` does with the `--help` option: it replaces the arguments with `["help"]`.
