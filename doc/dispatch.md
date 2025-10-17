# Dispatch

The `net.lewisship.cli-tools/dispatch` function is the prime entrypoint for both Babashka and Clojure tools.

`dispatch` is provided with an options map for your tool that describes how `cli-tools` will operate; 
`dispatch` reads the namespaces defined in the options to build
up an index of all the commands and command groups in your tool.

The `dispatch` function then starts to consume command line arguments, identifying groups and sub-groups until
it reaches leaf nodes, the commands; the command is passed the remaining command line arguments.

The individual command function parses those arguments using the `defcommand` interface, parsing the values
into options and positional arguments as defined by the `defcommand` macro; these
are ultimately exposed as local symbols that your command's code can act on.



```clojure
(dispatch 
  {:namespaces [...]
   :doc "String that defines the tool, used in tool-level help."
   :groups {"my-group" {:title "One line title describing the group."
                        :doc "Longer description of the group, used in group-level help."
                        :namespaces [...]
                        :groups {...}]}]})
```

The top-level `:namespaces` is used to define any top-level commands.
Each namespace is a symbol for the namespace.

This is optional and defaults to an empty list.

The namespace `net.lewiship.cli-tools.builtins` is always prefixed to the provided list; this provides
the tool-level `help` command.

You may also specify groups; groups are containers of sub-commands, and may themselves contain sub-groups.

The group map is keyed on a group name, and its value defines the group; like the top-level map,
a group will contain keys for :namespaces, :title, :doc, and :groups (for more deeply nested groups).

The group's :title is used in the tool help summary.
 
![Top Level Help](images/top-level-help.png)                              

In the above example, `colors`, `completions`, `help`, `job-status-demo`, and `kill-port` are commands.
The remaining items are groups (shown in italics); the text shown is the :title for the group.

## Defaults
              
The :doc and :title group options may be omitted.

If :doc is omitted, then `dispatch` will search the group's namespaces, and use the first namespace docstring
it finds.

If :title is omitted, then the first sentence of the :doc key, up to (but not including) 
the first `.`, will be used as the title.

## Callbacks

Certain options define callbacks that will be invoked during dispatch.  These are optional.

Any return value from the callbacks is ignored.

The :pre-dispatch callback is invoked once all command data has been assembled (possibly, by loading from cache).
It is passed the full dispatch options. 

After :pre-dispatch, `dispatch` will start to consume command line arguments to identify the specific command function
to invoke.

The :pre-invoke callback is invoked once a specific command function is identified.
The callback is invoked immediately before the command function is invoked; it is passed the 
command map, and a seq of remaining arguments (that will then be passed to the command function).
