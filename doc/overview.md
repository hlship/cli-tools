# cli-tools Overview

In `cli-tools`, a tool is a top-level command (usually, a Babashka script) that contains many
sub-commands.

At the core of `cli-tools` is [[defcommand]], a macro that defines a special kind of function, a
command function.

A namespace may define any number of commands, and commands may be drawn from multiple namespaces.

When you have many commands, you can create groups; a group is a containing of commands. Groups
can even contain nested groups.

The top level command script's job is to make the call to `dispatch`, passing the options that
describe all those namespaces and groups.  `cli-tools` will parse the command line arguments to find
the command to execute (possibly inside a group) and then will use the command's interface
to parse the command's specific options and positional parameters.  Only after all of that is successful
is the _code_ of the command executed, which is free to do whatever it needs to do.

Command names should be long and expressive; when parsing command and group names, `cli-tools` can recognize
command and group names from shorter prefixes, as long as a prefix uniquely identifies a command (or group);
when that isn't the case, `cli-tools` will prompt you with possible completions and a reminder on how to get more extensive help.


