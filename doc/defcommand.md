# defcommand

The core of `cli-tools` is the [[net.lewisship.cli-tools/defcommand]] macro.  This is how you define commands.

`defcommand` defines a command as a special kind of function, in
terms of a command-line interface, and a body that acts on the data collected from the command line.

The interface defines options as well as positional arguments; those options and arguments are available
in the body of the command just as if they were parameters passed to the function.
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

The full details of an option definition are supplied by [tools.cli](https://github.com/clojure/tools.cli).

## Positional Arguments

The way positional arguments are defined is intended to be similar to how
options are defined by `tools.cli`:

```
["<LABEL>" "<DOC>" <key/value pairs>]
```

The `<LABEL>` is a string used in the summary, and in validation error messages;
the `<DOC>` is a string used in the summary.  After that come key/value pairs.

* `:optional` (boolean, default false) -- if true, the argument may be omitted if there isn't a
  command line argument to match

* `:repeatable` (boolean, default false) -- if true, then any remaining command line arguments are processed
  by the argument

* `:parse-fn` - passed the command line argument string, returns a value, or throws an exception

* `:validate` - a vector of function/message pairs

* `:update-fn` - optional function used to update the (initially nil) entry for the argument in the arguments map

* `:assoc-fn` - optional function used to update the arguments map; passed the map, the argument id, and the parsed value

* `:update-fn` and `:assoc-fn` are mutually exclusive.

For repeatable arguments, the default update function will construct a vector of values.
For non-repeatable arguments, the default update function simply sets the value.

Only the final positional argument may be repeatable.

Also note that all command line arguments _must be_ consumed, either as options or as positional arguments.
Any additional command line arguments will be reported as a validation error.

## defcommand options

The interface vector of defcommand may have additional options; these are keywords that change
how following values in the interface vector are parsed.
We saw this in the example above, where `:args` was used to switch from defining options to defining
positional arguments.

### :options

Indicates that any following terms define options; this is the initial parser state, so `:options`
is rarely used.

### :args

Indicates that any following terms define positional arguments.

### :command \<string\>

Overrides the default name for the command, which is normally the same as the function name.
This is useful, for example, when the desired command name would conflict with a clojure.core symbol,
or something else defined within your namespace.

The `:command` option is also useful when using cli-tools to define
the `-main` function for a simple tool (a tool with options and arguments,
but not subcommands); this command name will be used in the command summary (from the
`-h` / `--help` switch).

### :title \<string\>

Normally, the command title (which appears next to the command in the `help` tool summary) is just
the first sentence of the command's docstring, up to the first `.`.  If, for some reason,
that default is incorrect, the command's summary can be explicitly specified using `:title`.

### :in-order true

By default, options are parsed using `clojure.tools.cli/parse-opts`, with the `:in-order` option set to false;
this means that `parse-opts` will stop at the first
option-like string that isn't declared.

```
(defcommand remote
  "Use ssh to run a command remotely."
  [verbose ["-v" "--verbose"]
   :args
   command ["COMMAND" "Remote command to execute"]
   remote-args ["ARGS" "Arguments to remote command"
                :optional true
                :repeatable true]]
     ...)
```

You might expect that `app-admin remote ls -lR` would work, but it will fail
with an error that `-lR is not recognized`.

You can always use `--` to split options from arguments, so `app-admin remote -- ls -lR` will work,
but is clumsy.

Instead, add `:in-order true` to the end of the interface, and any
unrecognized options will be parsed as positional arguments instead,
so `app-admin remote ls -lR` will work, and `-lR` will be provided as a string in the `remote-args`
seq.

### :let \<bindings\>

It can be useful to define local symbols that can be referenced inside the option
and arguments definitions; the `:let` keyword is followed by a vector of bindings.

```clojure
(defcommand set-mode
  "Sets the execution mode"
  [mode ["-m" "--mode MODE" (str "Execution mode, one of " mode-names)
         :parse-fn keyword
         :validate [allowed-modes (str "Must be one of " mode-names)]]
   :let [allowed-modes #{:batch :async :real-time}
         mode-names (->> allowed-modes (map name) sort (string/join ", "))]]
  ...)
```

> Note that the `new.lewisship.cli-tools/select-option` function is an easier way to create such
> an option.

In the expanded code, the bindings are moved to the top, before the option and argument
definitions.  Further, if there are multiple `:let` blocks, they are concatinated.

This also means that the bindings _can not_ reference the symbols for options or arguments.

### :validate \<vector of test/message pairs\>

Often you will need to perform validations that consider multiple fields.
The `:validate` directive adds tests that occur after primary parsing of command line options
has occurred, but before executing the body of the function.

It is a vector of tests and messages.
Each test expression is evaluated in turn; if the result is falsey, then the message
is passed to `print-errors` as an error, and `exit` is called with the value 1.

A common case is to handle mutually exclusive arguments:

```clojure
(defcommand sort-data
  "Sorts some data"
  [alpha ["-a" "--alpha-numeric" "Sort in alpha-numeric order"]
   numeric ["-n" "--numeric" "Sort in numeric order"]
   :validate [(not (and alpha numeric)) "Only one of --alpha-numeric or --numeric is allowed"]]
  ; At most one of alpha or numeric is true here
)
```

Note that unlike a validate function for an option or argument, these are expressions that can leverage
local symbols (such as `alpha` and `numeric`) and not functions that are passed a value.
