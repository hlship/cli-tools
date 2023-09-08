# 0.9 -- UNRELEASED

Testing was simplified; command functions can be tested by passing them a single map of option and
argument values (previously values map was nested in another map).

Namespaces now represent _categories_ of related commands; this changes the output from the `help` command.
Further, categories may also be command groups, which applies a prefix to all commands within the category
(this is useful with tools that define large numbers of subcommands).
 
[Closed Issues](https://github.com/hlship/cli-tools/issues?q=is%3Aclosed+milestone%3A0.9)

# 0.8 -- 5 Jul 2023

Switched from io.aviso/pretty to org.clj-commons/pretty.

# 0.7 -- 26 May 2023

Make use of more fonts, and bump io.aviso/pretty to version 1.4.2.

# 0.6 -- 7 Oct 2022

Exposed a `best-match` function which uses the same match logic as
the dispatch function.

Added default for :tool-name option (to `dispatch`), determined from the `babashka.file` system property.

Added :validate directive to command interface, allowing for validations that involve multiple options.

# 0.5 -- 29 Aug 2022

Add public `exit` function as a testable way to call `System/exit`.

Now highlights the tool name and command name in bold, in error messages.

Now suggests the most likely command name when the given name doesn't match exactly one
command.

# 0.4 -- 18 Aug 2022

Added support for the :let keyword in the command interface.

Changed `defcommand` to create a variadic function.

Added checks that options and argument symbols are unique; now all parsed options
and positional arguments are provided in a single `:options` map, which simplifies
testing.

# 0.3 -- 11 Aug 2022

Fix logic for default command summary to just be first line of docstring.

# 0.2 -- 11 Aug 2022

Don't catch exceptions in `dispatch`.

Split the `dispatch` function into `dispatch` and `dispatch*`.

Added `locate-commands`, for when invoking `dispatch*` directly.

Detect command name conflicts when locating commands.

Added :summary <string> to interface.

# 0.1 -- 8 Aug 2022

Initial release.
