# Repetition Hunter
Do you repeat yourself in your code? This is for you. It finds
repetitions in your code.

Add `[repetition-hunter "1.1.0"]` to the `:dependencies` of your
`:user` profile.

It works with Clojure version 1.10.3 and up.

## Usage
### Lein plugin
[Andrés Gómez Urquiza](https://github.com/nez) created a lein plugin that you can find at
[https://github.com/fractalLabs/lein-repetition-hunter](https://github.com/fractalLabs/lein-repetition-hunter)

Thanks @nez!

### File-based analysis (new)
The project now parses source files through [edamame](https://github.com/borkdude/edamame), which makes the analysis path friendly to `.clj`, `.cljs`, and `.cljc` files.

```clojure
(require 'repetition.hunter)

;; Analyze a single file
(repetition.hunter/hunt-path "src/my_app/core.cljs")

;; Analyze multiple files
(repetition.hunter/hunt-paths ["src/my_app/core.clj"
                                "src/my_app/ui.cljs"])
```

### Namespace-based analysis (existing)
You can use it from the REPL:

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

* Add some tests.

## Changelog
* v1.1.0
  * Switch parser from `clojure.core/read` to edamame for better location metadata and dialect support
  * Add `hunt-path` and `hunt-paths` for file-based analysis of `.clj`, `.cljs`, `.cljc` files
  * Add static symbol analysis — no longer requires `require`/`ns-resolve` for path-based analysis
  * Add `repetition.hunter.parse` namespace for configurable dialect-aware parsing
  * Add `repetition.hunter.source` namespace for source-unit abstraction

* v1.0.0
  * Add/Fix search files in directories outside src/. Now it tries to find in every classpath directory.
  * Fix ClassNotFoundException when file contains namespace-like symbols (thank you [tsholmes](https://github.com/tsholmes)).

* v0.3.1
  * Fix NPE when using with clojure 1.4.0

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

Copyright © 2013-2016 Marcelo Nomoto

Licensed under the EPL, the same as Clojure (see the file epl-v10.html).
