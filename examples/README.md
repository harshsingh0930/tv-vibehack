# Examples

One folder per TV model, each with a `debloat.txt` listing the packages that are safe to disable
on that firmware, grouped and commented. These are starting points: comment out anything you use.
Entries marked **device notes only** document initial testing; they do not yet provide a debloat list.

| Folder | TV | OS | Notes |
|---|---|---|---|
| `xiaomi-mi-tv-4a/` | Xiaomi Mi TV 4A (MiTV-MOOQ1) | Android TV 10 | PatchWall, Mi analytics, SmartShare, MiLink |
| [`redmi-mitv-axfr0/`](redmi-mitv-axfr0/) | Redmi MiTV-AXFR0 | Android TV 11 | Device notes only; multi-day debloat validation pending |

To add your TV, see [CONTRIBUTING.md](../CONTRIBUTING.md). The quickest way to get a list is to
run the prompt in [PROMPT.md](../PROMPT.md) and copy the packages your assistant disabled.
