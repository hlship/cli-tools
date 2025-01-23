{{category.fn-name}}() {
  local state line

  _arguments -C \
      "1: :->cmds" \
      "*::arg:->args"

  case "$state" in
     cmds)
       _values "{{tool}} {{category.name}} subcommands" {% for sub in category.subs %} \
         "{{sub.name}}[{{sub.summary}}]" {% endfor %}
       ;;
     args)
       case $line[1] in {% for sub in category.subs %}
         {{sub.name}}) {{sub.fn-name}} ;; {% endfor %}
       esac
       ;;
  esac
}


