#compdef _{{tool}} {{tool}}

_{{tool}}() {
  local line state

  _arguments -C {% for opt in options %} \
    {{ opt }} {% endfor %} \
    "1: :->cmds" \
    "*::args:->args"

   case "$state" in
     cmds)
        _values "{{tool}} command" {% for cmd in commands %} \
            "{{cmd.name}}[{{cmd.title}}]" {% endfor %}
        ;;
     args)
       case $line[1] in {% for cmd in commands %}
         {{cmd.name}}) {{cmd.fn-name}} ;;
{% endfor %}
       esac
       ;;
   esac
}
