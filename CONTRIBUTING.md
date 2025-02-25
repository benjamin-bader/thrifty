Contributing to Thrifty
=======================

The Thrifty team welcomes contributions of all kinds, from simple bug reports through documentation, test cases,
bugfixes, and features.

DOs and DON'Ts
--------------

* DO follow our coding style (as described below)
* DO give priority to the current style of the project or file you're changing even if it diverges from the general guidelines.
* DO include tests when adding new features. When fixing bugs, start with adding a test that highlights how the current behavior is broken.
* DO keep the discussions focused. When a new or related topic comes up it's often better to create new issue than to side track the discussion.
* DO run all Gradle verification tasks (`./gradlew check`) before submitting a pull request

* DO NOT send PRs for style changes.
* DON'T surprise us with big pull requests. Instead, file an issue and start a discussion so we can agree on a direction before you invest a large amount of time.
* DON'T commit code that you didn't write. If you find code that you think is a good fit, file an issue and start a discussion before proceeding.
* DON'T submit PRs that alter licensing related files or headers. If you believe there's a problem with them, file an issue and we'll be happy to discuss it.


Coding Style
------------

We use [Spotless](https://github.com/diffplug/spotless) to automatically apply and enforce code styles.  For Java code, we use [palantir-java-format](https://github.com/palantir/palantir-java-format); for Kotlin, [ktfmt](https://github.com/facebook/ktfmt).

Code that does not pass CI, whether for functional or style reasons, will not be merged.  Style choices not covered by Spotless are left to your discretion, but please try to favor matching the style of existing code.

Workflow
--------

We love Github issues!  Before working on any new features, please open an issue so that we can agree on the
direction, and hopefully avoid investing a lot of time on a feature that might need reworking.

Small pull requests for things like typos, bugfixes, etc are always welcome.

Please note that we will not accept pull requests for style changes.
