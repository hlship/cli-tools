## Linting

The `defcommand`  macro is complex and will confuse [clj-kondo](https://github.com/clj-kondo/clj-kondo) out of the box, but we provide
hooks to allow clj-kondo to reasonably lint it.

The hooks are provided with config path `io.github.hlship/cli-tools`.
