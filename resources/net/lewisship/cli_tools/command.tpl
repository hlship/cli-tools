{{command.fn-name}}() {
    _arguments -s {% for opt in command.options %} \
      {{ opt }} {% endfor %}
}

