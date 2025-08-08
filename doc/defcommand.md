# defcommands

The core of `cli-tools` is the [[net.lewisship.cli-tools/defcommand]] macro.  This is how you define commands.

The core utility is the `net.lewisship.cli-tools/defcommand` macro, which defines a command in
terms of a command-line interface, and a body that acts on the data collected from the command line.

The interface defines options as well as positional arguments; those options and arguments are available
in the body of the command just as if they were parameters passed to the command.
`defcommand` defines a function that accepts a variadic number of command line argument strings,
parses them as options and positional arguments, binds those to local symbols, and evaluates the body.

An example to begin; let's say you are creating a command for administrating some part of your application.
You need to know a URL to update, and a set of key/value pairs to configure.  Let's throw in a `--verbose`
option just for kicks.

**src/app_admin/commands.clj**:

```clojure
(ns app-admin.commands
  "Command to administrate my service"
  (:require [net.lewisship.cli-tools :refer [defcommand]]))

(defcommand configure
  "Configures the system with keys and values"
  [verbose ["-v" "--verbose" "Enable verbose logging"]
   :args
   host ["HOST" "System configuration URL"
         :validate [#(re-matches #"https?://.+" %) "must be a URL"]]
   key-values ["DATA" "Data to configure as KEY=VALUE"
               :parse-fn (fn [s]
                           (when-let [[_ k v] (re-matches #"(.+)=(.+)" s)]
                             [(keyword k) v]))
               :update-fn (fn [m [k v]]
                            (assoc m k v))
               :repeatable true]]
  ; Placeholder:               
  (prn :verbose verbose :host host :key-values key-values))
```

The meat of this `configure` command has been replaced with a call to `prn`, but
the important part for this discussion is the interface, which takes the place of an ordinary
function's parameters declaration.

Initially, the interface is about options, and we define one option, `--verbose`, a flag.
Inside the body, the value will be bound to local symbol `verbose`, which will be nil if `--verbose` is
not specified, or true if it is.

`defcommand` always adds the `-h` / `--help` flag, and implements it; the body does not get
evaluated if help is requested, or if there's any kind of validation error processing
command line arguments.

An option definition always starts with three strings: the short option name, the long option name,
and the option description; these are positional, and a nil may be supplied.

