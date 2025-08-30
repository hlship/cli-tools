# io.github.hlship/cli-tools

[![Clojars Project](https://img.shields.io/clojars/v/io.github.hlship/cli-tools.svg)](https://clojars.org/io.github.hlship/cli-tools)
<a href="https://babashka.org" rel="nofollow"><img src="https://github.com/babashka/babashka/raw/master/logo/badge.svg" alt="bb compatible" style="max-width: 100%;"></a>
[![cljdoc badge](https://cljdoc.org/badge/io.github.hlship/cli-tools)](https://cljdoc.org/d/io.github.hlship/cli-tools)

`cli-tools` is a complement to [Babashka](https://github.com/babashka/babashka) used to create tools
with sub-commands, much like [Babashka tasks](https://book.babashka.org/#tasks). It is effectively
a layer on top of [org.clojure/tools.cli](https://github.com/clojure/tools.cli).

`cli-tools` is more verbose than [babashka-cli](https://github.com/babashka/cli) and more opinionated.
At the core, you define local symbols and instructions for how those symbols map to command line options
or positional arguments; `cli-tools` takes care of the majority of command line parsing and validation
for you.

`cli-tools` is intended to create three types of command lines tools:
 
- A simple tool simply parses its command line arguments and executes some code using those arguments (think: `ls` or `cat`)
- A common tool is composed of multiple commands, across multiple namespaces. The first command line argument
  will select the specific sub-command to execute. (think: `git`)
- A complex tool organizes some commands into command groups that share an initial name (think `kubectl`)

For tools with multiple commands, `cli-tools` automatically adds 
a built-in `help` command to list out what commands are available, and
can even perform basic searches for commands.

For complex tools, what `cli-tools` offers is **discoverability**.  You define the switches, options, and arguments for each command, and each command gets a `--help` switch to print
out the command's summary. The  `help` command
that can list out all the commands available, neatly organized, and can even do a simple
search for you.  There's even (experimental) support for zsh completions for your tool and all of its categories, commands, and options.

This kind of discoverability is a big improvement over shell aliases, and one-off shell scripts that leave you guessing what they do and what arguments need to be passed in.

`cli-tools` also offers great **feedback**, using indentation, color, and careful attention
to detail, to provide tool users with consistent, readable, and friendly error messages, command summaries, and so forth.

`cli-tools` can work with Babashka, or with Clojure, but the near instantaneous startup time of Babashka is compelling
for the kind of low-ceremony tools that `cli-tools` is intended for.

Although `cli-tools` can be used to build shared tools, it is also fully intended for developers to create a personal
toolkit of commands specific to their individual workflows, as an alternative to a collection of shell aliases and one-off shell scripts.

Below is an example of the author's personal toolkit, `flow`:

![Example](images/example-usage.png)

A complete and open-source example is [dialog-tool](https://github.com/hlship/dialog-tool), which also shows how to organize 
a tool so that it can be installed as a Homebrew formula.

> Note that dialog-tool uses a slightly earlier version of cli-tools.

## Compatibility

`cli-tools` is compatible with Clojure 1.11 and above, and w/ Babashka.

## License

`io.github.hlship/cli-tools` is (c) 2022-present Howard M. Lewis Ship.

It is released under the terms of the Apache Software License, 2.0.
