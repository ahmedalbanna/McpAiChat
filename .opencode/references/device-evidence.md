# Device evidence report

The output contract for the `device-debugger` agent. Every claim about the phone carries its source; a claim without a source is a guess and does not belong in the report.

## Sections

| Section              | Content                                                                                      |
| -------------------- | -------------------------------------------------------------------------------------------- |
| **Build**            | Working-tree state, Gradle result, APK path                                                  |
| **Install & launch** | Device serial, install result, the activity that was started                                 |
| **Steps**            | Numbered; each step names the command and the screen text it produced                        |
| **Evidence**         | The decisive artefact: UI-dump excerpt, logcat lines with timestamp and tag, screenshot path |
| **Cause**            | The mechanism, one paragraph, anchored to a `file:line` or a named platform rule             |
| **Fix**              | The change, and the device check that proves it                                              |
| **Verdict**          | `fixed` / `not fixed` / `blocked on <what>`, plus what stayed unverified                     |

## Claim levels

- **observed** — the device showed it (screen text, log line, screenshot).
- **derived** — it follows from the code plus an observation (the code path that produced the log).
- **inferred** — reasoning without a device observation. Say so, and say what would confirm it.

## Rules

- Quote screen text **verbatim**. Paraphrase only inside the cause.
- Log excerpts keep their timestamp and tag.
- A screenshot evidences the screen's state, never the cause.
- Write "not observed" rather than "works" when the device never showed it.
- A command that exited 0 is not evidence that the feature works; the screen is.
- Negative results count: "the tap at (x,y) did nothing; the field still held the text" is evidence.
