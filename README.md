# Repetition Hunter

Do you repeat yourself in your code? This is for you. It finds
repetitions in your code.

Add `[repetition-hunter "0.3.0"]` to the `:dependencies` of your
`:user` profile.

## Usage

You should use it from the REPL:

    user=> (use 'repetition.hunter)
    nil
    user=> (require 'your.namespace)
    nil
    user=> (hunt 'your.namespace)
    2 repetitions of complexity 5

    Line 474 - your.namespace:
    (or (modifiers->str mname) (name mname))

    Line 479 - your.namespace:
    (or (modifiers->str m) (name m))

    ======================================================================

    3 repetitions of complexity 5

    Line 50 - your.namespace:
    (str "(" (first t) ")")

    Line 294 - your.namespace:
    (str "(" (first f) ")")

    Line 360 - your.namespace:
    (str "(" (first c) ")")

    ======================================================================

    2 repetitions of complexity 7

    Line 162 - your.namespace:
    (str/join ", " (map (partial identifier->str db) column))

    Line 170 - your.namespace:
    (str/join ", " (map (partial identifier->str db) column))

    ======================================================================
    nil

Each repetition is presented with a header showing the number of repetitions
and their complexity. Complexity is the count of flatten the form
`(count (flatten (form)))`. It is sorted by default by complexity, from less
complex to more complex.

Now it also support multiple namespaces. Require them all and pass a list to
hunt:

    user=> (hunt '(your.namespace1 your.namespace2 your.namespace3))

You can also sort by repetitions using the optional parameter `:repetition`
like this:

    user=> (hunt 'your.namespace :sort :repetition)

There are filters:

    user=> (hunt 'your.namespace :filter {:min-repetition 2
                                          :min-complexity 5
                                          :remove-flat true})

The filters default to :min-repetition 2, :min-complexity 3 and :remove-flat false
Remove flat is a filter to remove flat forms.

After the header the repeated code is shown with the line number and namespace.

If it doesn't find repetitions it doesn't print anything.

That's it. Now go refactor your code.

## Acknowledgments

Thanks to [Tom Crayford](https://github.com/tcrayford) for pointing me
to his abandoned [umbrella](https://github.com/tcrayford/umbrella) and
[Phil Hagelberg](https://github.com/technomancy) for helping me on #clojure.

## Bugs and Enhancements

Please open issues and send pull requests.

## TODO

* Make it work from the command line.
* ~~Add option to only output structures with other colls inside.~~
* ~~Add filters to low count or complexity.~~
* ~~Show the original code and location on file.~~
* ~~Add support to multiple namespaces.~~
* Add some tests.

## Changelog

* v0.3.0
  * Add multiple namespaces support
  * Add filters :min-repetition, :min-complexity, :remove-flat
  * Indented code in results

* v0.2.0
  * Add line numbers
  * Show original forms
  * Better output format
  * Has sort order
  * Improve var detection

## License

Copyright © 2013 Marcelo Nomoto

Licensed under the EPL, the same as Clojure (see the file epl-v10.html).
