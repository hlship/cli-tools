#compdef _{{tool}} {{tool}}

_{{tool}}() {
  local line state
  local -a _subcommands

  _subcommands=( {% for cmd in commands %}
    '{{cmd.name}}:{{cmd.summary}}' {% endfor %}
  )

  _arguments -C \
    "1: :->subs" \
    "*::arg:->args"

  echo "_{{tool}} state: $state" >> debug.log
  echo "_{{tool}} line[1]: $line[1]" >> debug.log

   case "$state" in
     subs)
       _describe -t _subcommands '{{tool}} subcommands' _subcommands
       ;;
     args)
       case $line[1] in {% for cmd in commands %}
         {{cmd.name}}) {{cmd.fn-name}} ;;
{% endfor %}
       esac
       ;;
   esac
}
