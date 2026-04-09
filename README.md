# OpenWay VPN

Android VPN клиент на базе Xray/VLESS с красивым Material 3 UI.

## Возможности

- 🔌 **VLESS подключение** — полный VLESS/Xray туннель через `VpnService`
- 🎨 **Анимированный UI** — Jetpack Compose, Material 3, кастомный шрифт Benzin
- 🌐 **Мультиязычность** — Русский 🇷🇺 / English 🇺🇸
- 📊 **Мониторинг** — качество соединения и таймер работы
- ⚡ **Quick Settings Tile** — быстрое вкл/выкл из шторки
- 🗂 **Управление профилями** — импорт из буфера/deep-link, удаление, переключение
- 🎆 **Интерактивный фон** — частицы с эффектом кометы при подключении
- 📱 **Обработка ошибок** — приложение не крашится при сбоях Xray/tun2socks

## Технологии

| Компонент | Технология |
|---|---|
| UI | Jetpack Compose, Material 3 |
| Мин. SDK | 31 (Android 12) |
| Язык | Kotlin 2.0.21 |
| VPN Engine | Xray Core + hev-socks5-tunnel |
| Архитектура | VpnService + Foreground Service |

## Сборка

```bash
./gradlew assembleDebug
```

APK появится в `app/build/outputs/apk/debug/app-debug.apk`

## Структура

```
app/src/main/java/com/omix/openwayvpn/
├── MainActivity.kt           # Точка входа (40 строк)
├── LanguageManager.kt        # Управление языком (RU/EN)
├── ui/
│   ├── AppRoot.kt            # Навигация и анимации экранов
│   ├── VpnScreen.kt          # Главный экран VPN
│   ├── ProfilesScreen.kt     # Экран профилей
│   ├── SettingsScreen.kt     # Настройки (язык, about)
│   ├── AboutScreen.kt        # О приложении
│   ├── AppNavigation.kt      # Enums, утилиты, Preview
│   ├── TestScreen.kt         # Демо анимации (TODO)
│   ├── FloatingParticlesBackground.kt  # Фон с частицами
│   └── theme/                # Тема, цвета, типографика
└── vpn/
    ├── MyVpnService.kt       # VpnService + диагностика
    ├── XrayRuntime.kt        # Управление Xray процессом
    ├── Tun2SocksRuntime.kt   # Управление tun2socks
    ├── XrayConfigFactory.kt  # Генератор JSON конфига
    ├── VlessUriParser.kt     # Парсер VLESS URI
    ├── VlessProfile.kt       # Модель профиля
    ├── VlessProfileStore.kt  # Хранение профилей
    └── VpnQuickSettingsTile.kt  # Quick Settings Tile
```

## TODO

- [ ] Поддержка других протоколов (VMess, Trojan, Shadowsocks)
- [ ] Шаринг профилей через Share Intent
- [ ] Редактирование имён профилей
- [ ] Светлая тема
- [ ] Шифрование хранения профилей (EncryptedSharedPreferences)
