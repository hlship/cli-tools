#compdef _{{tool}} {{tool}}

_{{tool}}() {
  local line state

  _arguments -C \
    "1: :->subs" \
    "*::arg:->args"

  echo _"{{tool}} state: $state" >> debug.log
  echo _"{{tool}} words[1]: $words[1]" >> debug.log

   case "$state" in
   subs)
     _values "{{tool}} command" {% for cmd in commands %} \
       "{{cmd.name}}[{{cmd.summary}}]" {% endfor %}
     ;;
   args)
     case $line[1] in {% for cmd in commands %}
       {{cmd.name}}) {{cmd.fn-name}} ;;
{% endfor %}
     esac
     ;;
   esac
}
