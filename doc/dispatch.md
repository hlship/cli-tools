# Dispatch

The [[net.lewisship.cli-tools/dispatch]] function is the prime entrypoint for both Babashka and Clojure tools.

`dispatch` is provided with a descriptor for your tool; it reads the namespaces defined in the descriptor to build
up an index of all the commands and categories in your tool.

The `dispatch` function then starts to consume command line arguments, identifying groups and sub-groups until
it reaches leaf nodes, the commands; the command is passed the remaining command line arguments which it can parse
into options and positional arguments as defined by the [[net.lewisship.cli-tools/defcommand]] macro; these
are ultimately exposed as local symbols that your command's code can act on.

```clojure
{:namespaces [...]
 :tool-doc "String that defines the tool, used in tool-level help."
 :cache-dir "..."
 :groups [{:title "One line title describing the group."
           :doc "Longer description of the group, used in group-level help."
           :command "..."    ; command prefix for any commands within the group
           :namespaces [...]
           :groups [...]}]}
```

The top-level `:namespaces` is used to define any top-level ("Built-in") namespaces.
Each namespace is a symbol for the namespace.

This is optional and defaults to an empty list.

The namespace `net.lewiship.cli-tools.builtins` is always prefixed to the provided list; this provides
the tool-level `help` command.

In addition to the built-in commands, you may provide groups.

For each group, you may specify any number of namespaces for the commands within that group.

A group without a :command attribute is a category; the commands within the group are presented together, separately from the builtin commands (or other categories).  When a group has a :command attribute,
the commands defined by that group are available as sub-commands.

A group may have sub-groups to any arbitrary depth, though more than two layers would be unusual.

## Example

TBD

## Defaults

The default group :doc is the first non-empty docstring of any namespace in the namespace list.

The default :title is the first sentence in the group's doc (up to, but not including, the period).

## Caching

In order to operate, `dispatch` has to load all namespaces, to execute the `defcommand` macros in each,
and collect meta-data from all the namespaces and command functions.  Thanks to Babashka, this is extremely fast, 
but is made faster using caching.

`dispatch` builds a cache based on the options passed to it, and the contents of the classpath; it can then
load the data it needs to operate from the cache, if such data is present.

When executing from the cache, `dispatch` will ultimately load only a single command namespace,
to invoke the single command function.  This allows a complex tool, one with potentially hundreds of commands, to 
still execute the body
of the `defcommand` within milliseconds.

This may have an even more significant impact for a tool that is built on top of Clojure, rather than Babashka.
Our mockup of 1500 commands across 250 namespaces executes twice as fast using the cache (approximately 
8 seconds with the cache, vs. 17 seconds without).

Babashka is amazingly fast for these purposes; the same test executes in 0.23 seconds.

By default, `dispatch` will store its cache in the `~/.cli-tools-cache` directory; the environment variable
`CLI_TOOLS_CACHE_DIR` can override this default. This can also be specified as the top-level :cache-dir key.



