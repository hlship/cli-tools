# Caching

In order to operate, `dispatch` has to load all namespaces, to execute the `defcommand` macros in each,
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

By default, `dispatch` will store its cache in the `~/.cache/net.lewisship.cli-tools` directory; the environment variable
`CLI_TOOLS_CACHE_DIR` can override this default. (The `~/.cache` part is via
`babaska.fs/xdg-cache-home`). 

Alternately, you can also specify a java.nio.file.Path instance as the :cache-dir option passed
to [[dispatch]].

## Cache Keys

Inside `dispatch`, a cache key is a digest generated from:

- The paths of all libraries on the classpath
- The last-modified timestamp of all files on the classpath (i.e., the `src` directory)
- The last-modified timestamp of all files inside an _extra_ source directory
- A subset of the values in the `dispatch` options map

Once a key is generated, the fully expanded dispatch data is read from the corresponding file, if it exists.

If it does not exist, then the dispatch data is expanded and written to the file.

This means that just touching a source file will invalidate the cache.  Fortunately, it takes only milliseconds
to scan the files and create the cache key.

Old cache files are _not_deleted ... because we live in an age of near infinite disk space, right?

## Cache Dir Configuration

The cache can be disabled by setting the :cache-dir `dispatch` option to nil.  For small tools, the time
for Babashka to load namespaces may be smaller than the time to manage the cache.

The :cache-dir may also be a java.nio.file.Path instance for where the cache files should be stored.

## Extra Caching

The :source-dirs `dispatch` option may be a seq of path names; the files in these directories will be reflected in the cache digest key; this might be used by the :transform `dispatch` option if some commands are defined externally.
