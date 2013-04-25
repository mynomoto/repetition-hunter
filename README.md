# Repetition Hunter

Do you repeat yourself in your code? This is for you. It finds
repetitions in your code.

Add `[repetition-hunter "0.1.0"]` to the `:dependencies` of your
`:user` profile.

## Usage

You should use it from the REPL:

    user=> (use 'repetition.hunter)
    nil
    user=> (require 'your.namespace)
    nil
    user=> (hunt 'your.namespace)
    ([(when-not
       (vector? x_3)
       (let
        [x_0
         (class x_3)
         x_4
         (format
          "\"%s\" expected %s %s, found %s %s"
          "sql-params"
          "vector"
          "[sql param*]"
          (x_1 x_0)
          (pr-str x_3))]
        (x_2 (x_5 x_4))))
      2]
     [[x_2 x_0 x_1 x_3] 3]
     [[x_0 x_2 x_1] 3]
     [(concat x_0 [:entities *as-str*]) 3]
     [(set-parameters x_0 x_2 x_1) 3]
     [(and (map? x_0) (:connection x_0)) 4]
     [(add-connection x_1 (x_2 x_0)) 4]
     [(add-connection x_0 x_1) 4])
    nil

Each vector has some code and a number. The number is how many times
the code is repeated. If it doesn't find repetitions, it prints `()`.

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

## License

Copyright © 2013 Marcelo Nomoto

Licensed under the EPL, the same as Clojure (see the file epl-v10.html).
