# План миграции Aletheia на iOS

Размер Android-версии: ~72 000 строк Kotlin. Уже перенесено ~950 строк (13 файлов, без зависимостей от Android).

## Что заменить
| Android | iOS / общий код |
|---|---|
| Media3 / ExoPlayer, MediaSession | AVPlayer + MPNowPlayingInfoCenter (expect/actual в `iosMain`) |
| Свои `AudioProcessor` (8D, стили, реверб, бит) | AVAudioEngine, алгоритмы из `StyleDsp` переиспользуются |
| Room 2.6.1 | Room KMP (≥ 2.7) или SQLDelight |
| Coil 2 | Coil 3 (`coil-compose` KMP) |
| OkHttp | Ktor Client (Darwin) |
| Jsoup, Rhino, NewPipeExtractor | kSoup / Ksoup, собственный парсинг; извлечение потоков через свой сервер |
| yt-dlp / Python / ffmpeg / aria2c | недоступны на iOS; загрузку с платформ делать через сервер, либо не переносить |
| SettingsStore (SharedPreferences/DataStore) | multiplatform-settings или DataStore KMP |
| Виджеты, TileService, ярлыки | WidgetKit, App Shortcuts (на Swift) |
| MediaStore, SAF, файлы | NSFileManager, UIDocumentPicker |
| Гиро/датчики (HeadTracker, Gyro) | CoreMotion (expect/actual) |

## Порядок работ
1. Каркас и CI (готово).
2. Модели и чистая логика из `core`, `data` (много Android-зависимостей придётся выделять через `expect/actual`).
3. База данных и настройки.
4. Плеер и очередь (AVPlayer).
5. Экраны Compose: библиотека → плеер → плейлисты → настройки.
6. Эффекты и визуализации.
7. Онлайн-платформы (через сервер).
8. Подпись и публикация.

## Важно про App Store
Приложения, скачивающие контент с YouTube, SoundCloud, VK и т. п. в обход правил платформ, скорее всего,
не пройдут проверку Apple. Для личной установки это не мешает.
