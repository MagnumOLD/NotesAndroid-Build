# NotesAndroid — project rules

## Source of truth and branches

- The private repository `MagnumOLD/NotesAndroid` is the source of truth.
- Do all active development on `proto`.
- Do not modify `main` or `stable` unless the user explicitly asks.
- The public `NotesAndroid-Build` repository is a build mirror, not the canonical source.
- Never silently copy unrelated or unreviewed changes between repositories.

## Product model

- The main list contains only two record types: Note and Task.
- A Note is freeform text, edited like a plain text note.
- A Task opens its own detail screen.
- A Task contains a flat list of multiline task entries, not nested cards or a universal block model.
- Each task entry has its own status. Completed entries are shown with struck-through text.
- The task title is derived from its first line; do not add a separate title field unless explicitly requested.
- Keep the interface minimalist and consistent with the existing app.

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
