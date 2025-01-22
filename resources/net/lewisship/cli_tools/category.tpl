{{category.fn-name}}() {
  local state line

  _arguments -C \
      "1: :->cmds" \
      "*::arg:->args"

  echo "{{category.fn-name}} state: $state" >> debug.log
  echo "{{category.fn-name}} line[1]: $line[1]" >> debug.log

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


