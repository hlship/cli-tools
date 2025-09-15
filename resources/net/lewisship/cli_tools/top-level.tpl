#compdef _{{tool}} {{tool}}

_{{tool}}() {
  local line state

  _arguments -C \
    "1: :->cmds" \
    "*::arg:->args"

   case "$state" in
     cmds)
        _values "{{tool}} command" {% for cmd in commands | sort-by:name %} \
            "{{cmd.name}}[{{cmd.summary}}]" {% endfor %}
        ;;
     args)
       case $line[1] in {% for cmd in commands | sort-by:name %}
         {{cmd.name}}) {{cmd.fn-name}} ;;
{% endfor %}
       esac
       ;;
   esac
}

