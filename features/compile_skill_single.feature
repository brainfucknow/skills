Feature: Compile one skill

  Background:
    Given a fixture repository
    And a skill alpha in bucket engineering
    And a skill delta in bucket misc
    And a skill omega in bucket deprecated

  # Compile one skill 1
  Scenario Outline: Compile one skill 1 prints the same content compile-skills writes
    Given the file <extra> has content <content>
    And I run compile-skills
    When I run compile-skill alpha
    Then the exit code is 0
    And stdout is exactly the file compiled/alpha.md

    Examples:
      | extra                             | content          |
      | skills/engineering/alpha/notes.md | # Notes\n\nHi.\n |

  # Compile one skill 2
  Scenario Outline: Compile one skill 2 refuses unknown and uncompiled skills
    When I run compile-skill <name>
    Then the exit code is 1
    And stdout is empty
    And stderr includes <name>

    Examples:
      | name  |
      | nope  |
      | delta |
      | omega |

  # Compile one skill 3
  Scenario Outline: Compile one skill 3 prints usage without a name
    When I run compile-skill
    Then the exit code is 1
    And stdout is empty
    And stderr includes <usage>

    Examples:
      | usage                    |
      | Usage: bb compile-skill  |

  # Compile one skill 4
  Scenario Outline: Compile one skill 4 refuses duplicate skill names
    Given a skill alpha in bucket <other>
    When I run <command>
    Then the exit code is 1
    And stderr includes skills/engineering/alpha
    And stderr includes <duplicate>
    And the directory compiled lists <listing>

    Examples:
      | other        | command                 | duplicate              | listing |
      | in-progress  | compile-skills          | skills/in-progress/alpha | none  |
      | productivity | compile-skills --check  | skills/productivity/alpha | none |
      | productivity | compile-skill alpha     | skills/productivity/alpha | none |
