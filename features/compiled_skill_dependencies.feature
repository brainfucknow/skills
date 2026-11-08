Feature: Compiled skill dependencies

  Background:
    Given a fixture repository
    And a skill dep-a in bucket productivity
    And a skill dep-b in bucket in-progress
    And the skill dep-u in bucket engineering is user-invoked
    And a skill dep-m in bucket misc

  # Compiled skill dependencies 1
  Scenario Outline: Compiled skill dependencies 1 inlines only model-invoked compiled skills named in a Skill tool call
    Given the skill root in bucket engineering has body <instruction>
    When I run compile-skills
    Then the file compiled/root.md has <count> occurrences of <heading>
    And the file compiled/root.md has 1 occurrences of <instruction>

    Examples:
      | instruction                                          | heading                    | count |
      | Call the Skill tool with "dep-a".                    | # Skill: dep-a             | 1     |
      | Call the Skill tool with `dep-a` for style.          | # Skill: dep-a             | 1     |
      | call the Skill tool twice, for "dep-a" and "dep-b".  | # Skill: dep-b             | 1     |
      | Call the Skill tool with "dep-u".                    | # Skill: dep-u             | 0     |
      | Call the Skill tool with "dep-m".                    | # Skill: dep-m             | 0     |
      | Call the Skill tool with "the deletion test".        | # Skill: the deletion test | 0     |
      | Call the Skill tool. Then read "dep-a".              | # Skill: dep-a             | 0     |
      | Read "dep-a" first.                                  | # Skill: dep-a             | 0     |
      | Call the Skill tool with "root".                     | # Skill: root              | 1     |

  # Compiled skill dependencies 2
  Scenario Outline: Compiled skill dependencies 2 inlines transitively, once each, breadth first
    Given the skill root in bucket engineering has body <root_body>
    And the skill dep-a in bucket productivity has body <a_body>
    And the skill dep-b in bucket in-progress has body <b_body>
    And the skill dep-c in bucket productivity has body <c_body>
    When I run compile-skills
    Then in the file compiled/root.md, # Skill: root comes before # Skill: dep-a
    And in the file compiled/root.md, # Skill: dep-a comes before # Skill: dep-b
    And in the file compiled/root.md, # Skill: dep-b comes before # Skill: dep-c
    And the file compiled/root.md has 4 occurrences of # Skill:

    Examples:
      | root_body                                           | a_body                                              | b_body                             | c_body |
      | Call the Skill tool twice, for "dep-a" and "dep-b". | Call the Skill tool twice, for "dep-c" and "dep-b". | Call the Skill tool with "dep-a".  | # C\n  |

  # Compiled skill dependencies 3
  Scenario Outline: Compiled skill dependencies 3 gives a dependency section no frontmatter and its own anchors
    Given the skill root in bucket engineering has body Call the Skill tool with "dep-a".
    And the skill dep-a in bucket productivity has body <a_body>
    And the file skills/productivity/dep-a/notes.md has content # A notes\n
    When I run compile-skills
    Then the file compiled/root.md includes <section>
    And the file compiled/root.md has 0 occurrences of <hidden>

    Examples:
      | a_body             | section                                                                           | hidden                    |
      | See [n](notes.md). | # Skill: dep-a\n\nSee [n](#file-dep-anotesmd).\n\n## File: dep-a/notes.md\n\n# A notes | description: dep-a fixture. |

  # Compiled skill dependencies 4
  Scenario Outline: Compiled skill dependencies 4 inlines the project's real dependencies
    Given the project repository
    When I run compile-skill <skill>
    Then the exit code is 0
    And stdout includes <heading>

    Examples:
      | skill     | heading                  |
      | grill-me  | # Skill: grilling        |
      | wayfinder | # Skill: domain-modeling |
      | triage    | # Skill: domain-modeling |
      | implement | # Skill: tdd             |
