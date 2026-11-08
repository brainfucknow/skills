Feature: Compile skills selection

  Background:
    Given a fixture repository

  # Compile skills selection 1
  Scenario Outline: Compile skills selection 1 compiles only skills outside misc and deprecated
    Given a skill <name> in bucket <bucket>
    When I run compile-skills
    Then the exit code is 0
    And the compiled file for <name> is <presence>

    Examples:
      | name  | bucket      | presence |
      | alpha | engineering | present  |
      | beta  | productivity | present |
      | gamma | in-progress | present  |
      | delta | misc        | absent   |
      | omega | deprecated  | absent   |

  # Compile skills selection 2
  Scenario Outline: Compile skills selection 2 lists exactly one compiled file per compiled skill
    Given a skill <first> in bucket engineering
    And a skill <second> in bucket in-progress
    And a skill <hidden> in bucket misc
    When I run compile-skills
    Then the directory compiled lists <listing>

    Examples:
      | first | second | hidden | listing            |
      | alpha | beta   | delta  | alpha.md, beta.md  |

  # Compile skills selection 3
  Scenario Outline: Compile skills selection 3 deletes compiled files with no skill
    Given a skill <name> in bucket engineering
    And the file <orphan> has content <content>
    When I run compile-skills
    Then the exit code is 0
    And the file <orphan> does not exist
    And the compiled file for <name> is present

    Examples:
      | name  | orphan          | content  |
      | alpha | compiled/gone.md | stale\n |

  # Compile skills selection 4
  Scenario Outline: Compile skills selection 4 is idempotent
    Given a skill <name> in bucket <bucket>
    When I run compile-skills
    Then running compile-skills again changes nothing

    Examples:
      | name  | bucket       |
      | alpha | productivity |

  # Compile skills selection 5
  Scenario: Compile skills selection 5 keeps the project compiled output current
    Given the project repository
    When I run compile-skills --check
    Then the exit code is 0
    And stderr is empty
