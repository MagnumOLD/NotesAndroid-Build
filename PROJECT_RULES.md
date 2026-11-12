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
- A Purchase opens its own editor, separate from the Task editor. Its nested entries are goods: show a checkbox on the right immediately before the drag handle; checked goods use the completed state and struck-through text. Purchase parent uses the same two-state completion control as a Task; nested goods do not expose task-style status menus.
- When creating a new parent Task or Purchase, focus the title field in the top bar and show the keyboard immediately; existing records must open without automatic keyboard. Keep child-task and good autofocus behavior unchanged.
- In Task detail, the «Добавить задачу» FAB creates a child task directly on one tap; do not show a one-item intermediate menu. Open the new child editor with the text field focused and the keyboard shown. In Purchase detail, «Добавить товар» behaves the same way for a new good. If the user exits without entering content, discard the empty child/good. On the main screen, a short tap on the add button immediately creates and opens a regular Note; a long press opens a compact menu containing only Task and Purchase. A long press must never trigger Note creation on release. Preserve the button's visual footprint and existing position.
- The task title is derived from its first line; do not add a separate title field unless explicitly requested.
- Keep the interface minimalist and consistent with the existing app.
- Position the main-screen add FAB (creates Note, Task, or Purchase) at `Modifier.offset(y = (-65).dp)` as the current target for a 2340 px-tall reference screen; this raises its center by approximately 105 px from the previous -30 dp placement. Do not change task/purchase detail-screen FAB offsets as part of this rule.
- Main-list cards for all three record types share one structure: multiline content/title at the upper left, drag handle at the upper right aligned with the first text line, and a metadata row below. Task and purchase status appears on the left; ordinary notes do not show a redundant «Заметка» label. Creation date stays right-aligned for all record types.
- Main-list card height is content-driven with a compact minimum height; do not truncate a long first line to one visual line.
- Render the main-card header through one shared layout so text, pin marker, and drag handle keep identical geometry for notes, tasks, purchases, and the temporary group placeholder. Measure each rendered card and keep the grouped-drag placeholder at the grabbed card's measured height; do not substitute a fixed height for content-driven cards.
- The main-card title text remains an interactive target: a short tap opens/toggles the record and a long press enters selection, including after the shared header is extracted. The temporary group placeholder has no title interaction.
- Keep main-card foreground and selected-background calculations centralized so regular cards and the grouped-drag placeholder use the same color rules.
- Show the main-card status on the left as plain text with no background, pill, border, or extra padding. Keep the existing status text size/color and the creation date unchanged.
- Format the creation date on the right dynamically: `Сегодня` for the current local calendar day, `Вчера` for the previous local calendar day, `Чт. 11.11` for other dates in the current year, and `Чт. 11.11.2025` for dates in another year. Calculate the label from the creation timestamp and device-local calendar whenever the card is rendered; never persist relative labels as text. Keep record type/status on the left.
- Keep the drag handle in the same top-right position for every main-list card, including cards with multiline text and the temporary grouped-drag placeholder. Vertically center the visible handle on the first 22.dp title line (not in a 40.dp-tall box that places it too low); center the 40.dp touch target on that same visual position. Preserve the shared 20.dp right content guide, 10.dp title-to-handle gap, stable-key drag hit target, and adjacent-swap behavior. Do not apply this main-card geometry change to nested task or purchase rows. Its two lines remain 16 dp long with the existing spacing and animation, but their stroke thickness is 0.8 dp (20% thinner than the previous 1 dp). The handle uses the exact foreground color of its row text (including completed child text and group labels), with 50% alpha at rest and 96% while pressed. Do not replace it with fixed gray colors; this keeps the handle in the same color family as the card text across base, selected, and colored cards.

## Layout and spacing system

- Use the shared `AppSpacing` tokens for page guides and recurring layout gaps; do not introduce arbitrary per-screen horizontal margins.
- The primary content guide is 20.dp from each screen edge. Main-list card text, creation-date metadata, editor content, archive/trash content, and settings-list visible content align to this guide.
- Material3 `ListItem` has its own 16.dp content inset. Settings rows add a 4.dp outer inset so their visible text/icons align to the 20.dp page guide; keep section dividers full-width.
- Main-list drag handles are a deliberate exception: preserve their current touch/visual position independently from the content guide. The first card row reserves the additional 8.dp needed for the handle, while the metadata row reaches the 20.dp right guide.
- Navigation controls keep their standard touch targets and toolbar insets. Do not force a back-button title to the content guide; its position after the navigation control is intentional.
- Keep component geometry (button sizes, touch targets, card dimensions, icon sizes) separate from spacing tokens. Do not normalize vertical spacing or component sizes merely to make numeric values identical.
- Before adding a new spacing value, check whether an existing semantic token already expresses that role. Use literal dp only for component geometry or a documented one-off.

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

## Editor autofocus rules

- Opening an existing task subitem or purchase item must never show the keyboard automatically.
- Only the exact child record created by the current add action may receive autofocus; tapping an existing child clears that creation-only autofocus state.
- New top-level tasks and purchases use one shared parent-header component built on the same standard-height Material3 TopAppBar as the note editor (one row; do not add a second header row). Keep the existing Back and overflow actions. The title is a single-line controlled string-based BasicTextField; it must never increase header height. The parent completion control is one CheckCircle outline icon with exactly two visual states: neutral gray when not completed and muted green (#83A98C) when completed. Tapping it toggles only WAITING/COMPLETED; completed titles are struck through. Do not show a status chip, status text, date, or animation in this header. Keep existing date/deadline behavior elsewhere in record details unchanged. Do not mirror the title in a second local TextFieldValue state or synchronize duplicate text copies. Autofocus only after the field has completed layout. For a newly created parent Task or Purchase, consume the autofocus request immediately after its first focus/keyboard presentation; retain that consumed state in the parent editor while child screens are opened so returning from a child never reopens the keyboard. New child-task and good autofocus remains independent and unchanged. Use the Done IME action to dismiss the keyboard. Existing records must not autofocus.
- Persist notes, collections, archive, and trash from a single snapshotFlow with a short debounce; avoid serializing the full JSON data set for every keystroke. Keep the existing JSON schema and keys compatible.
- A parent Task or Purchase has exactly one title editor: the top-bar field. Do not expose a second `titleText` editor in the body or a toggle that reveals a duplicate field. Keep optional title/description behavior for child records unchanged.
- Existing notes, tasks, purchases, and child records must not inherit autofocus from a previous creation action.
