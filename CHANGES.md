# v0.4 -- UNRELEASED

Added support for the :let keyword in the command interface.

Changed `defcommand` to create a variadic function.

Added checks that options and argument symbols are unique; now all parsed options
and positional arguments are provided in a single `:options` map, which simplifies
testing.

# v0.3 -- 11 Aug 2022

Fix logic for default command summary to just be first line of docstring.

# v0.2 -- 11 Aug 2022

Don't catch exceptions in `dispatch`.

Split the `dispatch` function into `dispatch` and `dispatch*`.

Added `locate-commands`, for when invoking `dispatch*` directly.

Detect command name conflicts when locating commands.

Added :summary <string> to interface.

# v0.1 -- 8 Aug 2022

Initial release.
