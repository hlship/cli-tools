# Testing
 
Even when writing special purpose commands, it is useful to be able to test your commands.

Working in a functional language such as Clojure makes testing much easier.

To facilitate testing, command functions can be invoked in two ways:

- By passing a var args of strings into the function
- By passing a single map of options and arguments

When passing strings, the internal command line parsing logic generated from the commands interface
will be engaged as normal.

For example, consider a command function:

```clojure
(defcommand sort-file
  "Sort the contents of a file."
  [descending? ["-d" "--descending" "Sort contents in decending order"]
   :args
   file-name ["FILE" "File to sort"]]
   ...)
```

This could be invoked, from a test, as `(sort-file "-d" "test-files/sort-test.txt")` or 
`(sort-file {:descending? true :file-name "test-files/sort-test.txt"})`.  The behavior inside the function is the same.

When invoking with a map, all normal validations are entirely bypassed, and it is up to you to set the keys
of the map to match the local symbols for the arguments and options.

For testing purposes, you can bypass the parsing and validation, and just pass a single map to the function.
The map must provide a keyword key for each option or positional argument; the keys match the option or argument symbols,
even for options that normally have a default value. All normal option or argument validation is skipped.

You may need to mock out `net.lewisship.cli-tools/print-errors` if your command
invokes it, as that relies on some internal state from undocumented dynamically-bound vars.

Fortunately, it is quite rare for a command to need to invoke this function.

When _not_ bypassing parsing and validation (that is, when testing by passing strings to the command function),
validation errors normally print a command summary and then call `net.lewisship.cli-tools/exit`, which in turn, invokes `System/exit`; this is obviously
problematic for tests, as the JVM will exit during test execution.

The function `net.lewisship.cli-tools/set-prevent-exit!` can convert those cases to instead
throw an exception, which can be caught by tests.

Further, application code should also invoke `net.lewisship.cli-tools/exit`
rather than `System/exit`, for the same reasons.
