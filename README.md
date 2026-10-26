# bdai — On-Device AI Birthday Companion (`se.oscarb.bdai`)

A slim, Gemini-inspired Android app powered by an **on-device AI agent (Gemma 4 E2B via LiteRT-LM)** that manages a local family & friends birthday calendar and syncs with a home NAS (`api.php` + `birthdays.json`) over local Wi-Fi.

## Features
- **100% On-Device AI Agent (`Gemma 4 E2B` / `LiteRT-LM`)**:
  - Runs locally on GPU (`Backend.GPU()`) with native tool calling (`search_people`, `get_upcoming_birthdays`, `add_or_update_person`, `add_child`, `delete_person`).
  - **Zero mental date math**: Kotlin calculates exact `age_now`, `turning_age`, `next_birthday`, and `days_until_birthday` so the model never hallucinates ages.
  - **Smart parent/child disambiguation**: If two friends share the same name (e.g., two parents named "Alex"), each parent holds their own nested `children` list and the tools disambiguate automatically.
- **Bilingual (Swedish & English)**:
  - Understands natural voice and text in Swedish and English (e.g., *"Lägg till Emma i födelsedagskalendern"*, *"Hur gamla är Simons barn?"*, *"How old is Bob again?"*).
  - Full UI translations in English (`values/strings.xml`) and Swedish (`values-sv/strings.xml`).
- **Gemini-Inspired Single-Screen UI**:
  - Material 3 Light & Dark theme following the system.
  - Quick-action suggestion pills above the chat bar.
  - Native **Microphone button** (`SpeechRecognizer`) for hands-free voice queries and updates.
  - **People & Upcoming Sheet** for quick browsing or manual edits.
  - **Settings & Sync Dialog** to configure the local NAS endpoint (`http://<nas-ip>/birthday-calendar/api.php`) and toggle daily birthday push notifications (`WorkManager`).

## Slim `birthdays.json` Schema
```json
[
  {
    "name": "Simon",
    "birthday": "1988-04-15",
    "friendOf": "Alex",
    "partner": {
      "name": "Emma",
      "birthday": "1990-08-22"
    },
    "children": [
      {
        "name": "Adam",
        "birthday": "2018-06-10"
      },
      {
        "name": "Elsa",
        "birthday": "2021-02-14"
      }
    ]
  }
]
```
