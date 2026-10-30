# Changelog

## 0.2 Hotfix 1 — 30 October 2026

- Restored System navigation, Diagnostics and the Game Resources page.
- Moved About inside System, below Game Resources.
- Matched added controls to the launcher and separated them across the existing pages.
- Removed the duplicate driver picker; imports and selection use Vulkan Drivers.
- Fixed graphics settings saving and misleading game-folder errors during setup.
- Kept old configuration text when its overrides cannot be applied.


## 0.2 — 29 October 2026

- Updated the runtime and PC-style trainer, with English menus and the transparent XHYN V logo.
- Added graphics, population, distance, FPS and texture-detail controls.
- Added a dxvk.conf editor/import and renderer-aware shader controls.
- Added experimental PanVK/Xclipse driver paths, separate driver checks and Turnip options.
- Improved targeted crash handling, timed capture and session reports.
- Added About beside System with Telegram, YouTube and GitHub links.
- Kept the original download page and components.

Development prerelease; device compatibility and gameplay still need testing.

## 1.7.4 — 28 October 2026

- Refreshed XHYN V branding with a transparent purple V logo.
- Updated launcher pages, dialogs, trainer text and control-layout descriptions.
- Removed the Chinese system-language requirement and selected English game text/fonts.
- Kept real file paths intact while translating surrounding interface text.
- Preserved graphics, touch/controller, resource-management and custom-radio options.
- Published our interface/language patches and build tools.

This edition uses a separate app package from older releases. Bring your own compatible GTA V files, including the required text and fonts.

## 0.1 — 16 October 2026

Initial release of XHYN V Reborn, based on the Chinese GTA V Android port.

- Translated launcher text, trainer labels and 89 touch layouts into English.
- Added linked menu and in-game language selection. English is used by default and for unsupported selections.
- Fixed the engine check that required Chinese system language, along with font and text-file selection.
- Added a DXVK configuration switch and editor. Your configuration is preserved when the switch is off.
- Added shader compiler thread and refresh-rate controls.
- Separated graphics caches by driver and renderer to avoid reusing incompatible caches.
- Improved startup errors, crash reports, restart and shutdown handling.
- Added diagnostic export and local performance recording.

Other game languages require their matching text and font files. Some menu translations are incomplete. GTA V Legacy 1.61 / build 2699 remains the supported game version.
