# 0.16.0 -- UNRELEASED

*BREAKING CHANGES*

* Groups are now defined in the options passed to `net.lewisship.cli-tools/dispatch`, not in
  namespace metadata
* Command names are matched as prefixes (not substrings)
* The tool's documentation must now be specified in top-level :doc option key (not from namespace meta-data)
* `net.lewisship.cli-tools`:
    * In a `defcommand`:
        * The :summary key has been renamed to :title
        * The :as keyword is no longer supported
    * `abort` has been stripped down, it no longer writes the tool name, command path, etc.
    * The two-arg variant of `print-errors` has been removed
    * `dispatch*` function arguments have changed
    * `expand-dispatch-options` has been removed
* When an arg is ambiguous during dispatch, the error text now says "could match" and uses "or" as the conjunction, e.g. "ex could match exhume or extract"
* Tool and command help is now printed to \*out\*, not \*err*\*
* The builtin `help` command now as an option, `-c` / `--commands`, that can be one of `none`, `root`, or `all`, and the `--flat` switch was removed.
  
*Changes*

* Groups may now be nested, to arbitrary depth
* You may now enter `-h` or `--help` after a group to get help for just that group
* Tool help output has been reordered, with top-level tool commands first (previously, those were in a "Builtin" group and listed last)
* Tool help now displays just root-level commands by default (add `--commands all` to list nested commands)
* When extracting the first sentence as the single-line index, embedded periods are no longer considered the end of the sentence
* `net.lewisship.cli-tools`:
    * New `command-path` function returns a composed string of the tool name and command path
    * `dispatch` function has new options:
        * :handler is a function to handle top-level tool options (then delegate to `dispatch*`)
        * :transformer provides a function to add additional commands and groups after namespaces are loaded
        * :source-dirs specifies extra directories to consider when caching
        * Can handle "messy" case where a command has the same name as a group
* Cache files are now stored in `~/.cache/net.lewisship.cli-tools` by default

# 0.15.1 -- 27 Jan 2025

Changes:

The `abort` function assumes an exit status of 1 if the first message passed is not numeric.
It also now prints the tool and command name in bold green (to be consistent with
the rest of the library).

The clj-kondo hook for the `defcommand` macro now produces slightly different code, that defeats
the clj-kondo static type analysis, preventing spurious warnings about vectors passed
to particular clojure.core functions.

Dependencies were not properly declared for Clojure applications that use cli-tools
(libraries that are bundled with Babashka were omitted).

A number of minor issues related to single-command tools (those that use a `defcommand`
to define their single main entrypoint, usually named `-main`) were resolved.

[Closed Issues](https://github.com/hlship/cli-tools/milestone/8?closed=1)

# 0.15 -- 24 Jan 2025

Added optional namespace `net.lewisship.cli-tools.completions`, which is considered _experimental_.
The added `completions` command generates zsh command completions for the tool.

Added new functions to `net.lewisship.cli-tools`:
* `abort` is used to terminate a tool with a status code and provide an error message to standard error

[Closed Issues](https://github.com/hlship/cli-tools/milestone/7?closed=1)

# 0.14 -- 27 Nov 2024

The `help` builtin command now includes an optional search term argument; if provided, only commands whose name
or command summary includes the search term (using a caseless match) are included in the output.

Added support for :command-ns meta-data on namespaces.

Added two optional namespaces (that appear as built-in commands):
* `net.lewisship.cli-tools.color` (prints a chart of foreground and background colors)
* `net.lewisship.cli-tools.job-status-demo` (runs a demo of the job status system)

[Closed Issues](https://github.com/hlship/cli-tools/milestone/6?closed=1)

# 0.13 -- 22 Sep 2024

*BREAKING CHANGES*

The `print-summary` command was removed, and replaced with `print-errors`.  When a command has input
errors, only a brief summary of the command name is printed (previously, it was the entire command summary
including options and arguments).

The tool and command name, when printed in summaries and errors, is now consistently in bold green.

[Closed Issues](https://github.com/hlship/cli-tools/milestone/5?closed=1)

# 0.12 -- 17 May 2024

Moves specs to their own namespace.

Ensure that most output (such as command summaries, prompts, help text)
goes to `*err*`, not `*out*`.

Added an on-disk caching mechanism to avoid loading most namespaces when dispatching to commands.

Added function `net.lewisship.cli-tools/ask`, which prompts the user
for input (such as "yes" or "no").
 
[Closed Issues](https://github.com/hlship/cli-tools/issues?q=is%3Aclosed+milestone%3A0.12)

# 0.11 -- 17 Nov 2023

Use the same separator (`,`, not `/`) for tool options as for command options.

Added `net.lewisship.cli-tools.job-status` namespace, which provides dynamic feedback
for multiple long-running jobs.

[Closed Issues](https://github.com/hlship/cli-tools/milestone/3?closed=1)

# 0.10 -- 20 Sep 2023

Added new function `select-option`, used to build the option spec for an option
selected from a list of possible values.

Improved the output of command mismatches (where the input is not sufficient to
uniquely identify a command), and removed the fuzzy match ("did you mean?")
that did not seem to provide any real benefit.

Tools that make use of `dispatch` now add two new leading switches:
* `-C`, `--color`: enables ANSI colors, even if otherwise disabled
* `-N`, `--no-color`: disables ANSI colors, even if otherwise enabled

Like `-h`, `--help`, these must precede any command names.

See [org.clj-commons/pretty](https://cljdoc.org/d/org.clj-commons/pretty/2.0/api/clj-commons.ansi#*color-enabled*) for details
on when ANSI is enabled or disabled normally.

[Closed Issues](https://github.com/hlship/cli-tools/issues?q=is%3Aclosed+milestone%3A0.10)


# 0.9 -- 11 Sep 2023

Testing was simplified; command functions can be tested by passing them a single map of option and
argument values (previously the values map was nested in another map).

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
