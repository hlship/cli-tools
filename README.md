# io.github.hlship/bb-tasks

`bb-tasks` is a complement to [Babashka](https://github.com/babashka/babashka) that makes it easier
to provide [tasks](https://book.babashka.org/#tasks) that feature a full command line interface.

`bb-tasks` is a more complete, but more verbose, approach than leveraging the in-built support
for [babashka-cli](https://github.com/babashka/cli).

## deftask

The core utility is the function `net.lewisship.bb.tasks/deftask`, which defines a task in
terms of a command-line interface, and a body that acts on the data collected from the command line.

The interface defines options as well as positional arguments, and does so while mapping that values for
those options and arguments to local symbols.

An example to begin; let's say you are creating a Babaska task for configuring some part of your application.
You need to know a URL to update, and a set of key/value pairs to configure.  Let's throw in a `--verbose`
option just for kicks.

**scripts/tasks.clj**:

```
(ns tasks
  "Tasks specific to this poroject"
  (:require [net.lewisship.bb.tasks :refer [deftask]]))

(deftask configure
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
  (prn :verbose verbose :host host :key-values key-values))

```

The meat of this `configure` task has been replaced with a call to `prn`, but
the important part is the interface,, which takes the place of an ordinary
parameters declaration.

Initially, the interface is about options, and we define an option, `--verbose`, a flag.
Inside the body, the value will be bound to local symbol `verbose`.

`deftask` always adds a `-h` / `--help` flag, and implements it; the body does not get
executed if help is requested, or if there's any kind of validation error processing 
command line arguments.

The task function must be invoked from the project's `bb.edn`:

```
{:deps
 {io.github.hlship/bb-tasks {:mvn/version "<version>"}
 :paths ["scripts"]
 :tasks
 {:requires ([tasks :as t])
  configure {:doc "Configures the system with keys and values"
             :task (t/configure)}}}
```

For `bb`, the `scripts` directory, containing the `tasks.clj` source, is added.

> There's some unwanted duplication here, as the :doc should ideally come from the first
> line of the function's docstring.


`deftask` provides several different arities for the function; this is discussed below.

With this in place, we can run `bb configure` through its paces:

```
> bb configure -h
Usage: bb configure [OPTIONS] HOST DATA+
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
> bb configure example.org
Usage: bb configure [OPTIONS] HOST DATA+
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

Unless there are errors, the body of the task is invoked:

```
> bb configure http://example.org/conf pages=10 skip=true
:verbose nil :host "http://example.org/conf" :key-values {:pages "10", :skip "true"}
> 
```

The body here just prints out the values passed in.

## Positional Arguments

The way positional arguments are defined is designed to be similar to how
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

## deftest extras

### :as \<symbol\>

Inside the interface, you can request the _task map_ using `:as`.
This map captures information about the task, command line arguments,
and any parsed information; it is used when invoking `net.lewisship.bb.tasks/print-summary`, 
which a task may wish to do to present errors to the user.

### Arities

`deftask` defines a function with three arities:

- 0: Referenced from `bb.edn`, delegates to the 2-arity, passing the
     current Babashka task and command line arguments.
- 2: Parses command line arguments and then either reports errors, or passes the task map
     to the 1-arity
- 1: Destructures the task map to local symbols and implements the body of the function

When writing tests for your tasks, you may want to directly invoke the 1-arity version,
passing a map with keys :options and :arguments.

## License

`bb-tasks` is (c) 2022-present Howard M. Lewis Ship.

It is released under the terms of the Apache Software License, 2.0.