# NotesAndroid — project rules

## Source of truth and branches

- The public repository `MagnumOLD/NotesAndroid-Build`, branch `main`, is the only active source of truth.
- Make all active development directly on `main`.
- Do not depend on or synchronize from the private `MagnumOLD/NotesAndroid` repository.
- APK builds are triggered from pushes to this public `main` branch.

## Product model

- The main list contains three record types: Note, Task, and Purchase.
- A Note is freeform text, edited like a plain text note.
- A Task opens its own detail screen.
- A Task contains a flat list of multiline task entries, not nested cards or a universal block model.
- Each task entry has its own status. Completed entries are shown with struck-through text.
- A Purchase opens its own editor, separate from the Task editor. Its nested entries are goods: show a checkbox on the right immediately before the drag handle; checked goods use the completed state and struck-through text. Purchase parent retains its own manual status; nested goods do not expose task-style status menus.
- The task title is derived from its first line; do not add a separate title field unless explicitly requested.
- Keep the interface minimalist and consistent with the existing app.
- Main-list cards for all three record types share one structure: multiline content/title at the upper left, drag handle at the upper right aligned with the first text line, and a metadata row below with note type or task/purchase status on the left and creation date on the right.
- Main-list card height is content-driven with a compact minimum height; do not truncate a long first line to one visual line.
- Format the creation date on the right as a Russian weekday and abbreviated month: `Чт, 12 нояб.` for the current year and `Чт, 12 нояб. 2025` for another year. Keep record type/status on the left.
- Keep the drag handle in the same top-right position for every card, including cards with multiline text and the temporary grouped-drag placeholder. Preserve the existing stable-key drag hit target and adjacent-swap behavior. Keep its geometry unchanged; use a subdued tint adapted to the actual rendered card background (#555555 when the card text is light, #AAAAAA when the card text is dark); preserve the existing theme-based tint for nested task/purchase entries.

## Change discipline

- Do not start implementation until the user explicitly says «Приступай».
- First clarify or show design alternatives when the requested behavior or appearance is not settled.
- Implement only the approved scope. Do not redesign adjacent screens or refactor unrelated code.
- Before editing, inspect the current implementation and the target branch head.
- Prefer small, focused commits with descriptive messages.
- Before committing, review the changed-file list and the diff.
- Preserve existing user-approved behavior unless the task explicitly changes it.

## Verification and APK

- Build the debug APK independently from unit tests so a test failure does not prevent APK creation.
- Run unit tests as a separate job and retain their reports.
- Keep build logs as downloadable workflow artifacts.
- Cancel obsolete runs for the same branch when a newer commit arrives.
- Report build success only after the workflow succeeds and the APK artifact is present.
- Report test status separately from APK build status.
- Never present an old APK as the build for a newer commit.

## Communication

For each implementation task, report:
1. What was changed.
2. Which branch and commit contain the changes.
3. Which checks were actually run and their results.
4. Whether a fresh APK artifact is available.

If a check cannot be run or its result cannot be verified, say so plainly.
