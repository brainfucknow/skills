Feature: Compiled skill links

  Background:
    Given a fixture repository
    And the file skills/engineering/alpha/notes.md has content # Notes\n
    And the file skills/engineering/alpha/docs/deep.md has content Up to [n](../notes.md).\n

  # Compiled skill links 1
  Scenario Outline: Compiled skill links 1 rewrites links to files of the same skill
    Given the skill alpha in bucket engineering has body <body>
    When I run compile-skills
    Then the file compiled/alpha.md includes <rewritten>
    And the file compiled/alpha.md has 0 occurrences of <body>

    Examples:
      | body                     | rewritten                    |
      | See [n](notes.md).       | See [n](#file-alphanotesmd).  |
      | See [n](./notes.md).     | See [n](#file-alphanotesmd).  |
      | See [n](notes.md#part).  | See [n](#file-alphanotesmd).  |
      | See [d](docs/deep.md).   | See [d](#file-alphadocsdeepmd). |
      | See [s](SKILL.md).       | See [s](#skill-alpha).        |

  # Compiled skill links 2
  Scenario Outline: Compiled skill links 2 leaves other links untouched
    Given the skill alpha in bucket engineering has body <body>
    When I run compile-skills
    Then the file compiled/alpha.md has 1 occurrences of <body>

    Examples:
      | body                                  |
      | See [o](../beta/SKILL.md).            |
      | See [w](https://example.com/notes.md). |
      | See [a](#assets).                     |
      | See [m](missing.md).                  |
      | ```\n[n](notes.md)\n```               |

  # Compiled skill links 3
  Scenario Outline: Compiled skill links 3 resolves links against the linking file's folder
    Given the skill alpha in bucket engineering has body <body>
    When I run compile-skills
    Then the file compiled/alpha.md includes <rewritten>

    Examples:
      | body   | rewritten                     |
      | Alpha. | Up to [n](#file-alphanotesmd). |
