# Caching

In order to operate, [[net.lewisship.cli-tools/dispatch]] has to load all namespaces, to execute the `defcommand` macros in each,
and collect meta-data from all the namespaces and command functions.  Thanks to Babashka, this is extremely fast,
but is made faster using caching.

`dispatch` builds a cache based on the options passed to it, and the contents of the classpath; it can then
load the data it needs to operate from the cache, if such data is present.

When executing from the cache, `dispatch` will ultimately load only a single namespace,
to invoke the single command function.  This allows a complex tool, one with potentially hundreds of commands, to
still execute the body
of the `defcommand` within milliseconds.

This may have an even more significant impact for a tool that is built on top of Clojure, rather than Babashka.
Our mockup of 1500 commands across 250 namespaces executes twice as fast using the cache (approximately
8 seconds with the cache, vs. 17 seconds without).

Babashka is amazingly fast for these purposes; the same test executes in 0.23 seconds.

By default, `dispatch` will store its cache in the `~/.cli-tools-cache` directory; the environment variable
`CLI_TOOLS_CACHE_DIR` can override this default. 

Alternately, you can also specify a path as the :cache-dir option.

