# io.github.hlship/cli-tools

`cli-tools` is a complement to [Babashka](https://github.com/babashka/babashka) used to create tools
with sub-commands, much like [Babashka tasks](https://book.babashka.org/#tasks).

`cli-tools` is more verbose than [babashka-cli](https://github.com/babashka/cli) and more opinionated.
At the core, you define local symbols and instruction for how those symbols map to command line options
or positional arguments; `cli-tools` takes care of the majority of command line parsing and validation
for you.

`cli-tools` is generally used to create tools that contain individual commands; each of these commands
has its own unique options and arguments; `cli-tools` identifies the command from the first command line
argument, then passes the remaining arguments to the selected command.

`cli-tools` can be used for tools that simply have options and arguments but not commands.  
It isn't intended for tools that have more deeply nested levels of sub-commands.

`cli-tools` can work with Babashka, or with Clojure, but the near instantaneous startup time of Babashka is compelling
for the kind of low-ceremony tools that `cli-tools` is intended for.

## defcommand

The core utility is the function `net.lewisship.cli-tools/defcommand`, which defines a command in
terms of a command-line interface, and a body that acts on the data collected from the command line.

The interface defines options as well as positional arguments, and does so while mapping that values for
those options and arguments to local symbols.

An example to begin; let's say you are creating a Babaska command for administrating some part of your application.
You need to know a URL to update, and a set of key/value pairs to configure.  Let's throw in a `--verbose`
option just for kicks.

**src/app_admin/commands.clj**:

```clojure
(ns app-admin.commands
  "Commands specific to this project"
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
the important part is the interface, which takes the place of an ordinary
parameters declaration.

Initially, the interface is about options, and we define an option, `--verbose`, a flag.
Inside the body, the value will be bound to local symbol `verbose`.

`defcommand` always adds a `-h` / `--help` flag, and implements it; the body does not get
executed if help is requested, or if there's any kind of validation error processing 
command line arguments.

A namespace with commands is only part of the solution, to get from a terminal command line
to the body of the `configure` function, we need to add a bit of infrastructure.

First, we need a main namespace.

**src/app_admin/main.clj**:

```clojure
(ns app-admin.main
  (:require [net.lewisship.cli-tools :as cli-tools]))

(defn -main [& _]
  (cli-tools/dispatch {:tool-name "app-admin"
                       :namespaces ['app-admin.commands]}))
```

`dispatch` will find all `defcommand`s in the given namespaces, parse the first command line argument and use
it to find the correct command to delegate to.  That command gets the remaining command line arguments.

`dispatch` also recognizes `-h`, `--help`, or `help`, and will print out a summary of the available commands.

Finally, `dispatch` will allow an abbreviation of a command name to work, as long as that abbeviation uniquely
identifies a single possible command.

Since our tool is built in Babashka, we need a `bb.edn` that sets up the classpath (we would create
a `deps.edn` file, if the tool requires Clojure).

**bb.edn**:

```clojure
{:paths ["src"]
 :deps io.github.hlship/cli-tools {:mvn/version "<version>"}}}
```

Next, we need a Bash script, `app-admin`, to properly invoke `bb`:

**bin/app-admin**:

```shell
#!/usr/bin/env bash
set -euo pipefail
/usr/bin/env bb --config $(dirname $0)/../bb.edn -m app-admin.main $@
```

The above assumes that `bin` is a subdirectory, and that `bb.edn` is stored above it, at the project root.

The final step is to add that `bin` directory to the shell `$PATH` environment variable.

With all this in place, we can now run `app-admin configure` through its paces:

```
> app-admin configure -h
Usage: app-admin configure [OPTIONS] HOST DATA+
Configures the system with keys and values

Options:
  -v, --verbose  Enable verbose logging
  -h, --help     This command summary

Arguments:
  HOST: System configuration URL
  DATA: Data to configure as KEY=VALUE
> 
```

Help is provided automatically, and builds its content from the interface and the docstring
of the function.  The docstring is required.

Validations are reported as the command summary with errors at the end:

```
> app-admin configure example.org
Usage: app-admin configure [OPTIONS] HOST DATA+
Configures the system with keys and values

Options:
  -v, --verbose  Enable verbose logging
  -h, --help     This command summary

Arguments:
  HOST: System configuration URL
  DATA: Data to configure as KEY=VALUE

Error:
  HOST: must be a URL
>
```

Unless there are errors, the body of the command is invoked:

```
> app-admin configure http://example.org/conf pages=10 skip=true
:verbose nil :host "http://example.org/conf" :key-values {:pages "10", :skip "true"}
> 
```

The body here just prints out the values passed in.

## Abbreviated Commands

Feel free to give your commands long names; when `dispatch` is identifying a command to invoke
from the provided name on the command line, it will
find any commands whose name contains the provided name; so `app-admin conf` would work, as would `app-admin c` ... 
as long as there aren't multiple matches for the substring.

When there are multiple matches, `dispatch` will abort and the error message will identify which commands matched the provided
string.

Exception: when the provided command name _exactly_ matches a command's name, then that command will be used even if 
that command name is itself a prefix or substring of some other command name.

## Positional Arguments

The way positional arguments are defined is intended to be similar to how
options are defined in `clojure.tools.cli`:

```
["<LABEL>" "<DOC>" <key/value pairs>]
```

The `<LABEL>` is a string used in the summary, and in validation error messages;
the `<DOC>` is a string used in the summary.  After that come key/value pairs.

* `:optional` (boolean) -- if true, the argument may be omitted if there isn't a
    command line argument to match

* `:repeatable` (boolean) -- if true, then any remaining command line arguments are processed
by the argument

* `:parse-fn` - passed the command line argument, returns a value, or throws an exception

* `:validate` - a vector of function/message pairs

* `:update-fn` - optional function used to update the (initially nil) entry for the argument in the arguments map
 
* `:assoc-fn` - optional function used to update the arguments map; passed the map, the id, and the parsed value

* `:update-fn` and `:assoc-fn` are mutually exclusive.

For repeatable arguments, the default update function will construct a vector of values.
For non-repeatable arguments, the default update function simply sets the value.

Only the final command line argument may be repeatable.

Also note that all command line arguments _must be_ consumed, either as options or as positional arguments.

## defcommand extras

### :as \<symbol\>

Inside the interface, you can request the _command map_ using `:as`.
This map captures information about the command, command line arguments,
and any parsed information; it is used when invoking `net.lewisship.cli-tools/print-summary`, 
which a command may wish to do to present errors to the user.

### :command \<string\>

Overrides the default name for the command, which is normally the same as the function name.
This is useful, for example, when the desired command name would conflict with a clojure.core symbol,
or something else defined with your namespace.

### :summary \<string\>

Normally, the summary (which appears next to the command in the `help` tool summary) is just
the first sentence of the command's docstring, up to the first `.`.  If, for some reason,
that default is incorrect, the command's summary can be explicitly specified using :summary.

### :in-order true

By default, options are parsed with the `:in-order` option set to false;
this means that `clojure.tools.cli/parse-opts` will stop at the first
option-like string that isn't declared.

```
(defcommand remote
  "Use ssh to run a command remotely."
  [verbose ["-v" "--verbose"]
   :args
   command ["COMMAND" "Remote command to execute"]
   args ["ARGS" "Arguments to remote command"
         :optional true
         :repeatable true]]
     ...)
```

You might expect that `app-admin remote ls -lR` would work, but it will fail
with an error that `-lR is not recognized`.

You can always use `--` to split options from arguments, so `bb remote -- ls -lR` will work,
but is clumsy.

Instead, add `:in-order true` to the end of the interface, and any
unrecognized options will be parsed as positional arguments instead,
so `bb remote ls -lR` will work, and `-lR` will be provided as a string in the `args`
seq.

## Testing

Normally, the function defined by `defcommand` is passed a seq of strings, from
`*command-line-args*`; it then parses this into a map with keys :options and :arguments.

For testing purposes, you can bypass the parsing, and just pass a map to the function.

You may need to mock out `net.lewisship.cli-tools/print-summary` if your command
invokes it, as that relies on some additional non-documented keys to
be present in the command map.

Finally, validation errors normally print a command summary and then
call `System/exit`, which is problematic for tests;
`net.lewisship.cli-tools/set-prevent-exit!` can convert those cases to instead
throw an exception, which can be caught by tests.

## License

`io.github.hlship/cli-tools` is (c) 2022-present Howard M. Lewis Ship.

It is released under the terms of the Apache Software License, 2.0.