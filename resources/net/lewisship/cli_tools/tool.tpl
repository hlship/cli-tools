#compdef {{tool}}

_{{tool}}() {
  local line state

  _arguments -C \
    "1: :->toplevel" \
    "*::arg:->args"

   case "$state" in
   toplevel)
     _values "{{tool}} command" {% for cmd in commands %} \
       "{{cmd.name}}[{{cmd.summary}}]" {% endfor %}
     ;;
   args)
     case $line[1] in
{% for cmd in commands %}{% if not cmd.leaf? %}
       {{cmd.name}})  {{cmd.fn-name}} ;;
{% endif %}{% endfor %}
     esac
     ;;
   esac
}

{% for cmd in commands %}
{% if not cmd.leaf? %}
{{cmd.fn-name}}() {
  local state
  _arguments -C \
      "1: :->subcommands" \
      "*::args->args"

  case "$state" in
      subcommands)
        _values "{{tool}} {{cmd.name}}" {% for sub in cmd.subcommands %} \
          "{{sub.name}}[{{sub.summary}}]" {% endfor %}
      ;;
  esac
}
{% endif %}
{% endfor %}
