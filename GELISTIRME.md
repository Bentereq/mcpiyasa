# MCPiyasa Geliştirme Kılavuzu

Kaynak koda katkı vermek, item eklemek veya motoru değiştirmek isteyenler için.

## 1. Kaynaktan derleme

Gereksinim: **JDK 17+** ve Maven.

```text
mvn package
```

Derleme hedefi Java 8 bytecode'dur (`maven.compiler.release: 8`), JAR `target/` altında oluşur.

## 2. Paket mimarisi

| Paket | Sorumluluk |
|---|---|
| `engine` | Fiyat matematiği; saf Java, Bukkit importu yok. |
| `market` | İşlem yürütme, atomiklik ve iki dış bridge (ekonomi/envanter) arasındaki köprüler. |
| `storage` | SQLite üzerinde durum, işlem günlüğü, hacim profili ve snapshot kalıcılığı. |
| `gui` | Sandık tabanlı oyuncu ve admin menüleri. |
| `access` | Komut, tabela ve NPC erişim yüzeyleri. |
| `admin` | Admin komutlarının items.yml'e tek yazma noktası. |
| `api` | Dış eklentilere sunulan `MCPiyasaAPI`, event'ler ve PlaceholderAPI köprüsü. |
| `compat` | Spigot/Paper sürüm farklarını (tabela, materyal, zamanlayıcı) soyutlayan katman. |
| `config` | YAML yükleme, ayrıştırma ve doğrulama. |
| `diag` | Açılış teşhisi ve ticareti kilitleyen güvenli mod. |

## 3. Fiyat motoru özeti

Motor 5 sinyali birlikte kullanır: **stok eğrisi** (marjinal fiyatlama, spread, orta fiyat bantları), **sezonluk anormallik** (haftanın 168 saat-slotu için öğrenilen hacim profili), **HHI yoğunlaşma** (günlük satışın tek oyuncuda toplanma etkisi), **craft-akrabalık grubu** (nugget/külçe/blok gibi item'ların tek sanal stok havuzunu paylaşması) ve **öz-kalibrasyon** (gün sonu hacmine göre esnekliğin günlük ayarlanması). Deterministiktir — aynı girdi aynı sonucu üretir — ve `engine` paketi saf Java'dır, Bukkit'e bağımlı değildir.

## 4. KRİTİK KURAL — yeni item eklerken

Her dönüşüm/craft zinciri **arbitraja açık olabilir**: bir item'ı ucuza alıp craftlayıp pahalıya satmak mümkünse bu MCPiyasa'nın parasını sömürür. Yeni item eklerken şu üç kurala uyun:

1. **Her craft/dönüşüm zincirinde alış maliyeti > satış geliri olmalı** — hem taban stokta hem de fiyat bandının uç köşelerinde (girdiler taban fiyatta, çıktı tavan fiyatta). Aksi halde "ucuz gir → craftla → pahalı sat" açığı üretimde para sömürür.
2. **Craft-akrabalarını aynı gruba, ağırlıkla orantılı koyun** — nugget/külçe/blok gibi. Ayrı gruplara koymak akrabalar arası arbitraj yaratır.
3. **Çok girdili çıktılara `satis-acik: false` verin** (ör. `NETHERITE_INGOT`, `TORCH`, `BOOK`, `TNT`, `GOLDEN_APPLE`) — birden fazla ham maddeden üretilen bir item'ın satışı açıksa, ucuz girdilerin toplam maliyeti çıktının satış fiyatının altına düşebilir.

Bu, projenin **en kolay kırılan yeri**. Yeni bir item eklemeden önce onun her craft/smelt yolunu tek tek hesaplayıp doğrulayın.

## 5. Kalite kapısı

- Boot matrisi: `scripts/boot-matrix.ps1`, 4 Minecraft sürümünü (1.16.5, 1.18.2, 1.20.4, 1.21.4) gerçek sunucu jar'larıyla ayağa kaldırıp açılışı doğrular.
- `engine` paketi **saf kalmalı**: Bukkit importu yasak.
- `plugin.yml` geçerli YAML olmalı.
- `mvn package` temiz derlenmeli.

## 6. Katkı akışı

1. Fork/branch açın.
2. Değişikliğinizi kendi ortamınızda test edin (özellikle yeni item için bölüm 4'teki arbitraj kontrolü).
3. `mvn package` derlendikten sonra PR açın.

## 7. Lisans

Bkz. [LICENSE](LICENSE) (PolyForm Noncommercial 1.0.0). Ticari kullanım ayrı lisans gerektirir — bkz. `README.md`.
