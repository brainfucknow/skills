Feature: Compile skills check

  Background:
    Given a fixture repository
    And a skill alpha in bucket engineering
    And a skill beta in bucket productivity
    And I run compile-skills

  # Compile skills check 1
  Scenario: Compile skills check 1 passes when compiled output is current
    When I run compile-skills --check
    Then the exit code is 0
    And stderr is empty
    And stdout is empty

  # Compile skills check 2
  Scenario Outline: Compile skills check 2 names a stale compiled file and writes nothing
    Given the file <source> has content <edit>
    When I run compile-skills --check
    Then the exit code is 1
    And stderr includes <stale>
    And the compiled directory is unchanged since the last compile-skills run

    Examples:
      | source                                | edit              | stale              |
      | skills/engineering/alpha/notes.md     | Fresh note.\n     | compiled/alpha.md  |
      | skills/productivity/beta/SKILL.md     | ---\nname: beta\ndescription: Changed.\n---\n\n# beta\n | compiled/beta.md |

  # Compile skills check 3
  Scenario Outline: Compile skills check 3 names an extra compiled file
    Given the file <extra> has content <content>
    When I run compile-skills --check
    Then the exit code is 1
    And stderr includes <extra>
    And the file <extra> is exactly <content>

    Examples:
      | extra             | content   |
      | compiled/bogus.md | bogus\n   |

  # Compile skills check 4
  Scenario Outline: Compile skills check 4 names a missing compiled file
    Given the file <missing> is deleted
    When I run compile-skills --check
    Then the exit code is 1
    And stderr includes <missing>
    And the file <missing> does not exist
    And the file compiled/alpha.md is unchanged since the last compile-skills run

    Examples:
      | missing           |
      | compiled/beta.md  |

  # Compile skills check 5
  Scenario Outline: Compile skills check 5 passes again after recompiling
    Given the file <source> has content <edit>
    And I run compile-skills
    When I run compile-skills --check
    Then the exit code is 0

    Examples:
      | source                            | edit          |
      | skills/engineering/alpha/notes.md | Fresh note.\n |
