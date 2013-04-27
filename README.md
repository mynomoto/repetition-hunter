# Repetition Hunter

Do you repeat yourself in your code? This is for you. It finds
repetitions in your code.

Add `[repetition-hunter "0.2.0"]` to the `:dependencies` of your
`:user` profile.

## Usage

You should use it from the REPL:

    user=> (use 'repetition.hunter)
    nil
    user=> (require 'your.namespace)
    nil
    user=> (hunt 'your.namespace)
    2 repetitions of complexity 9

    On line 575:
    (format
    "\"%s\" expected %s %s, found %s %s"
    "sql-params"
    "vector"
    "[sql param*]"
    (.getName sql-params-class)
    (pr-str sql-params))

    On line 937:
    (format
    "\"%s\" expected %s %s, found %s %s"
    "sql-params"
    "vector"
    "[sql param*]"
    (.getName sql-params-class)
    (pr-str sql-params))

    ======================================================================

    2 repetitions of complexity 11

    On line 587:
    (cond
    sql-is-first
    (rest sql-params)
    options-are-first
    (rest (rest sql-params))
    :else
    (rest sql-params))

    On line 949:
    (cond
    sql-is-first
    (rest sql-params)
    options-are-first
    (rest (rest sql-params))
    :else
    (rest sql-params))

    ======================================================================

    2 repetitions of complexity 13

    On line 573:
    [sql-params-class
    (class sql-params)
    msg
    (format
      "\"%s\" expected %s %s, found %s %s"
      "sql-params"
      "vector"
      "[sql param*]"
      (.getName sql-params-class)
      (pr-str sql-params))]

    On line 935:
    [sql-params-class
    (class sql-params)
    msg
    (format
      "\"%s\" expected %s %s, found %s %s"
      "sql-params"
      "vector"
      "[sql param*]"
      (.getName sql-params-class)
      (pr-str sql-params))]

    ======================================================================

    nil

Each repetition is presented with a header showing the number of repetitions
and their complexity. Complexity is the count of flatten the form
`(count (flatten (form)))`. It is sorted by default by complexity, from less
complex to more complex. You can also sort by repetitons using the optional
parameter `:repetition` like this:

    user=> (hunt 'your.namespace :repetition)

After the header the repeated code is shown with the line number.
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
* Show the original code and location on file.

## Changelog

* v0.2.0
  * Add line numbers
  * Show original forms
  * Better output format
  * Has sort order

## License

Copyright © 2013 Marcelo Nomoto

Licensed under the EPL, the same as Clojure (see the file epl-v10.html).
