{{category.fn-name}}() {
  local state line
  local -a _subcommands

  _subcommands=( {% for sub in category.subs %}
    '{{sub.name}}:{{sub.summary}}' {% endfor %}
  )

  _arguments -C \
      "1: :->subs" \
      "*::arg->args"

  echo "{{category.fn-name}} state: $state" >> debug.log
  echo "{{category.fn-name}} line[1]: $line[1]" >> debug.log

  case "$state" in
     subs)
       _describe -t _subcommands '{{tool}} {{category.name}} subcommands' _subcommands
       ;;
     args)
       case $line[1] in {% for sub in category.subs %}
         {{sub.name}}) {{sub.fn-name}} ;; {% endfor %}
       esac
       ;;
  esac
}


