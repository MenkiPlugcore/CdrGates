# CdrGates

Standalone mini plugin untuk Paper 1.21.x yang memindahkan fitur `CdrGates v2.1.0` dari Skript ke Java native.

## Fitur

- Editor gate dengan block tool (`BEDROCK` default)
- Animasi buka bawah → atas dan tutup atas → bawah
- Material dasar gate + paint CLOSED/OPEN per block
- Click trigger: right / left / both
- Automatic proximity sensor, termasuk banyak sensor untuk satu gate
- Access mode: `none`, `item`, `key`
- Secure Gate Key dan Master Key memakai PersistentDataContainer (PDC)
- Revoke secure key lama dengan rotasi secret
- Data persisten di `plugins/CdrGates/data.yml`
- Tidak membutuhkan Skript, SkBee, atau SkQuery

## Requirement

- Paper 1.21.x
- Java 21

## Build

```bash
mvn clean package
```

Hasil build: `target/CdrGates-1.0.0.jar`.

## Command utama

Gunakan `/gatehelp` untuk daftar lengkap command.

| Command | Fungsi |
|---|---|
| `/gate <nama>` | Masuk editor gate |
| `/gate` | Keluar editor |
| `/togglegate <nama>` | Toggle gate |
| `/gatetype <gate> <block>` | Ganti material dasar |
| `/clickgate <right/left/both> <gate>` | Pasang trigger pada block yang dilihat |
| `/gateauto <gate> <radius>` | Buat sensor otomatis |
| `/gateaccess <gate> <none/item/key> [item]` | Atur access mode |
| `/gatekey give <player> <gate> [jumlah]` | Beri secure gate key |
| `/gatekey master <player>` | Beri master key |
| `/gatekey revoke <gate>` | Invalidasi gate key lama |

## Catatan migrasi

Versi Java menyimpan data sendiri di `data.yml`. Variabel Skript `{cdrgates::*}` tidak diimpor otomatis pada v1.0.0.
