# Styling cli-tools

Beauty is in the eye of the beholder; `cli-tools` makes a default
set of choices for what kind of colors and fonts to use
for its output, but this can be overridden.

The `net.lewisship.cli-tools.styles` contains a dynamic var, `*default-styles*`, that is used by the rest of `cli-tools`
when
formatting output. By overriding or rebinding this var, the fonts can be overridden.

The most common to override are:

- :tool-name-label (default :bold.green) used when writing the name of the tool itself
- :command-path (default :bold.green) used when writing the command path

When overriding `*default-styles*`, you can just provide overrides of what's in `default-styles`; anything not found in
the dynamic var is then searched for in the non-dynamic var.
