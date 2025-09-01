# cli-tools Overview

In `cli-tools`, a tool is a top-level command (usually, a Babashka script) that contains many
sub-commands.

At the core of `cli-tools` are two central components:

* The [defcommand](defcommand.md) macro defined a special kind of function, a command function
* The [dispatch](dispatch.md) function is the main entrypoint; it processes command line arguments and ultimately invokes the correct command function

A namespace may define any number of commands, and commands may be drawn from multiple namespaces.

When you have many commands, you can create groups; a group is a container of commands. Groups
can even contain nested groups.

The [top level command script](script.md)'s job is to make the call to `dispatch`, passing the options that
describe all those namespaces and groups.

Command names should be long and expressive; when parsing command and group names, `dispatch` can recognize
command and group names from shorter prefixes, as long as a prefix uniquely identifies a command (or group);
when that isn't the case, `dispatch` will prompt you with possible completions and a reminder on how to get more extensive help.
