{{command.fn-name}}() {

    echo "{{command.fn-name}} called" >> debug.log

    _arguments -S \
      "-x[do the X thing]" \
        '(-v --verbose)'{-v,--verbose}'[Increase verbosity]' \
        '(-c --catalog)'{-c,--catalog}'[File to process]:'
}

