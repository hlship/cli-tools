{{command.fn-name}}() {

    echo "{{command.fn-name}} called" >> debug.log

    _arguments -s {% for opt in command.options %} \
      {{ opt }} {% endfor %}
}

