{{category.fn-name}}() {
  local curcontext="$curcontext" state line
  typeset -A opt_args

  _arguments -C \
      "1: :->subs" \
      "*::options->options"

  echo "{{category.fn-name}} state: $state" >> debug.log
  echo "{{category.fn-name}} word: $words[2]" >> debug.log

  case "$state" in
     subs)
        _values "{{tool}} {{category.name}} subcommand" {% for sub in category.subs %} \
          "{{sub.name}}[{{sub.summary}}]" {% endfor %}
      ;;
     options)
       case $words[2] in {% for sub in category.subs %}
         {{sub.name}}) {{sub.fn-name}} ;; {% endfor %}
       esac ;;
  esac
}


