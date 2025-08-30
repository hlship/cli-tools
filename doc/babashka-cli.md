# Babashka CLI

`cli-tools` also recognizes command functions using [Babashka CLI](https://github.com/babashka/cli).

Functions in a namespace with the :org.babashka/cli metadata are recognized as command functions.

The value for this metadata is a map, possibly an empty map.
 
The :spec key of the map, if present, is used to parse command line arguments into options.  These are passed into the function.

## Limitations

Unlike when using `defcommand`, there is no automatic support for a `--help` option.  That is left up to the individual command to implement.

When generating completions, the options for the function are omitted; completion will just allow selecting the command. This may be addressed in the future.

`cli-tools` will capture exceptions thrown when parsing command line arguments into options and will report them.

## Extra Options

Beyond the normal options for the :org.babashka/cli metadata, `cli-tools` supports two additional keys.

* :title

Provides a short description of the command used in summaries; when not provided, this is extracted
from the first sentence of the function's docstring.

* :command

The name of the command to use instead of the default, which is the name of the function.


