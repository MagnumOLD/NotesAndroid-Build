# NotesAndroid Build

Публичная сборочная копия проекта NotesAndroid.

## Автоматическая синхронизация исходников

Сборочная копия автоматически проверяет приватную ветку `MagnumOLD/NotesAndroid:proto` каждые 10 минут. После обнаружения новых изменений файлы синхронизируются в `main`, после чего запускается сборка APK.

Для доступа к приватному репозиторию один раз добавь секрет:

1. Открой [Fine-grained personal access tokens](https://github.com/settings/personal-access-tokens).
2. Создай токен с доступом **только** к репозиторию `MagnumOLD/NotesAndroid`.
3. В Repository permissions выставь **Contents: Read-only**. Другие разрешения не нужны.
4. Открой [Secrets and variables → Actions](https://github.com/MagnumOLD/NotesAndroid-Build/settings/secrets/actions) в этом сборочном репозитории.
5. Создай Repository secret с именем `SOURCE_REPO_TOKEN` и вставь туда токен.
6. После сохранения открой Actions → **Sync private proto to build mirror** → Run workflow, чтобы выполнить первую синхронизацию сразу.

Токен хранится в GitHub Secrets и не попадает в исходники или логи workflow. Не отправляй его в чат и не добавляй в файлы проекта.

## Получение APK

1. Открой вкладку **Actions**.
2. Выбери последний успешный запуск **Build Android APK**.
3. Внизу страницы скачай артефакт **NotesAndroid-debug-apk**.
4. Распакуй ZIP и установи `app-debug.apk`.

Это отладочная сборка (debug), не релизная версия для Google Play.

## Важно

- Приватный репозиторий `MagnumOLD/NotesAndroid` — источник истины.
- Рабочая ветка — `proto`. Ветки `main` и `stable` приватного репозитория не изменяются.
- Публичный репозиторий используется только как зеркало для сборки.
- Не добавляйте в репозиторий пароли, токены, ключи подписи или персональные данные.
