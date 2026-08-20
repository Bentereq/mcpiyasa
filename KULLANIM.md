# MCPiyasa Kullanım Kılavuzu

Sunucu sahibi ve oyuncular için: komutlar, admin GUI, `config.yml` ayarları, `items.yml` düzenleme ve bilinen sınırlar.

## 1. Komutlar

Ana komut `/market`; alias'ları `/pazar` ve `/piyasa`. Item kimlikleri Bukkit `Material` adıdır (`IRON_INGOT`), büyük/küçük harf duyarsız.

| Komut | Ne yapar | İzin | Not |
|---|---|---|---|
| `/market` | Ana kategori GUI'sini açar. | `mcpiyasa.use` | Yalnız oyuncu |
| `/market fiyat <item>` | 1 adet için güncel alış/satış fiyatını gösterir. | `mcpiyasa.use` | Yalnız oyuncu |
| `/market al <item> <adet>` | Belirtilen adedi satın alır. | `mcpiyasa.use` | `ozellikler.komut-ticaret: true` gerekir; yalnız oyuncu |
| `/market sat <item> <adet>` | Belirtilen adedi satar. | `mcpiyasa.use` | `ozellikler.komut-ticaret: true` gerekir; yalnız oyuncu |
| `/market admin` | Tıklanabilir yönetim menüsünü açar. | `mcpiyasa.admin` | Yalnız oyuncu; `admin-komut` ayarından bağımsız her zaman çalışır |
| `/market admin reload` | Config, mesajlar, item tanımları ve servisleri yeniden yükler. | `mcpiyasa.admin` | Konsoldan da çalışır |
| `/market admin durum` | Güvenli mod nedeni, item/grup/atlanan sayıları, DB yolu. | `mcpiyasa.admin` | Konsoldan da çalışır |
| `/market admin liste [kategori] [sayfa]` | Ürünleri kategoriye göre, güncel birim fiyatlarıyla listeler. | `mcpiyasa.admin` | Salt okunur; sayfa başına 20 ürün; konsoldan da çalışır |
| `/market admin fiyat <item> <yeni-taban-fiyat>` | Ürünün taban fiyatını değiştirir, eski→yeni birim fiyatı bildirir. | `mcpiyasa.admin` | Yalnız yüklü ürünlerde çalışır |
| `/market admin sifirla <item>` | İtem'in grubunu taban stoğa döndürür (esneklik korunur). | `mcpiyasa.admin` | Konsoldan da çalışır |
| `/market admin itemekle [<MATERIAL>] <taban-fiyat> [taban-stok] [kategori]` | Item tanımını `items.yml`'e ekler/günceller, yeniden yükler. | `mcpiyasa.admin` | `MATERIAL` verilmezse **ana eldeki item** kullanılır (yalnız oyuncu) |
| `/market admin itemcikar <item>` | Item'ı `items.yml`'den çıkarır, yeniden yükler. | `mcpiyasa.admin` | Konsoldan da çalışır |
| `/market admin aktif <item> <ac|kapa>` | Ürünü silmeden markette devre dışı bırakır/açar. | `mcpiyasa.admin` | Konsoldan da çalışır |

`ozellikler.admin-komut: false` yapıldığında yukarıdaki **metin** alt komutları (`reload`, `durum`, `liste`, `fiyat`, `sifirla`, `itemekle`, `itemcikar`, `aktif`) kapanır ve oyuncu tıklanabilir menüye yönlendirilir; `/market admin` (argümansız) yine her zaman menüyü açar. `mcpiyasa.admin` her durumda zorunludur.

Tek işlemdeki adet `motor.max-islem-adet` ile sınırlıdır; komuttan geçersiz/aşan adet reddedilir.

## 2. Yönetici GUI (`/market admin`)

Metin komutu ezberlemeden fare ile yönetim; birincil admin aracı budur.

**Ana ekran**
- Durum kitabı: ürün/grup sayısı, güvenli mod, `hassasiyet` değeri.
- **Yeniden Yükle**, **Durum** düğmeleri.
- **Item Ekle (elindeki)**: ana eldeki materyali varsayılan taban fiyatla ekler ve düzenleyicisini açar.
- Düzenlenecek kategoriler.

**Kategori ekranı**
- Kategorideki item'ları sayfalı listeler.
- Her ikon canlı alış/satış birim fiyatını gösterir (NEUTRAL önizleme — işlem yaratmaz).
- İtem'a tıklayınca düzenleyici açılır.

**Item düzenleyici** — yazmaya gerek yok, hepsi tıklamayla:
- Fiyat adımları: `-%10`, `-1`, `+1`, `+%10`, `÷2`, `×2`. Metin `fiyat` komutuyla aynı yazma yolunu kullanır.
- **Alış aç/kapa** ve **Satış aç/kapa**: yeşil/kırmızı toggle.
- **Fiyatı sıfırla**: shift+tık.
- **Devre Dışı Bırak / Aktifleştir**: toggle, ürünü silmeden markette görünmez yapar.
- **Sil**: shift+tık ile onay ister, ikinci shift+tık siler.

Her düzenleme `items.yml`'e yazılıp reload'dan geçer; sunucu yeniden başlasa da kalıcıdır. Tüm menü ve her tıklama `mcpiyasa.admin` ile korunur (açılışta ve her tıklamada denetlenir).

Menülerde yalnız tek/shift sol-sağ tık işlem üretir; çift tık, sayı tuşu, off-hand takası, orta tık ve atma yok sayılır.

## 3. `config.yml` ayarları

Marketin oyuncuya görünen adı (menü başlığı ve sohbet öneki) varsayılan olarak `Market`tir; `messages_tr.yml` / `messages_en.yml` içindeki `prefix` ve `guimenu-baslik` anahtarlarıyla değiştirilebilir.

| Anahtar | Varsayılan | Ne işe yarar |
|---|---:|---|
| `dil` | `tr` | Mesaj dili: `tr` veya `en`. |
| `motor.spread` | `0.10` | Alış/satış fiyatı arasındaki oran. Aralık `0.02..1`; ~`%1.7` altı çapraz-ağırlık konvekslik arbitrajı açabilir. |
| `motor.toparlanma-katsayisi` | `0.05` | Her toparlanma tikinde sanal stoğun tabana yaklaşma oranı; `0` kapalı. |
| `motor.toparlanma-dakika` | `10` | Toparlanma tikleri arası dakika (en az 1). |
| `motor.anormallik-ussu` | `0.5` | Saat profiline göre anormal hacmin etkisi; `0` bu sinyali nötrler. |
| `motor.anormallik-bant` | `[0.5, 3.0]` | Alt < üst, iki pozitif sayı. |
| `motor.hhi-etki` | `1.0` | Satıcı yoğunlaşmasının fiyata katkısı; `0` kapalı. |
| `motor.esneklik-bant` | `[0.25, 1.5]` | Alt < üst, iki pozitif sayı. |
| `motor.esneklik-taban` | `0.6` | Referans hacimdeki başlangıç esnekliği; bant içinde olmalı. |
| `motor.referans-gunluk-hacim` | `2000` | Öz-kalibrasyon karşılaştırma noktası (birim/gün). |
| `motor.fiyat-bant` | `[0.25, 4.0]` | Override yoksa orta fiyatın taban fiyata göre kalabileceği bant. |
| `motor.max-islem-adet` | `2304` | Tek işlemdeki azami adet (`1..100000`). |
| `motor.varsayilan-taban-stok` | `20000` | `taban-stok` verilmeyen item/grup için varsayılan sanal stok. |
| `motor.hassasiyet` | `1.0` | Fiyatın işlem hacmine tepki **hızını** ölçekleyen küresel çarpan. **Düşür** → kalabalık sunucuda fiyat sakin/yavaş oynar (ör. `0.3`). **Yükselt** → küçük sunucuda az işlemle bile fiyat hareket eder (ör. `2.0`). Tüm grupları eşit ölçeklediği için başlangıç fiyatlarını değiştirmez, arbitraj açmaz — yalnız hızı değiştirir. **Çalışan sunucuda değiştirmek eğriyi yeniden ölçekler**; kayıtlı stoğun sıfırlanması önerilir (temiz başlangıç veya `/market admin sifirla`). |
| `motor.profil-alpha` | `0.3` | Haftalık saat-slotu hacim profilinde yeni gözlemin EMA ağırlığı. |
| `motor.profil-isinma-slot` | `3` | Bir saat-slotunda anormallik hesabı açılmadan önce gereken gözlem sayısı. |
| `ozellikler.kripto-gosterim` | `false` | 24s değişim yüzdesi, trend oku, yükselen/düşenler ekranı. |
| `ozellikler.komut-ticaret` | `false` | `/market al` ve `/market sat` komutlarını açar. |
| `ozellikler.tabela-market` | `false` | `[Market]` tabelası oluşturma ve tıklama. |
| `ozellikler.npc-market` | `false` | Citizens kuruluysa NPC market erişimi. |
| `ozellikler.creative-ticaret` | `true` | Varsayılan creative moda karışmaz; `false` yapılırsa creative moddaki işlemler reddedilir. |
| `ozellikler.admin-komut` | `true` | `/market admin` metin alt komutları. `false` ise sadece tıklanabilir menü kullanılır. |
| `guvenli-mod.zorla-calistir` | `false` | Teşhis başarısız olsa da ticareti zorlar. Risk sunucu yöneticisine aittir. |

Ayar değiştirdikten sonra `/market admin reload` çalıştırın.

## 4. `items.yml` düzenleme

- `gruplar.<id>`: akraba item'ların (nugget/külçe/blok gibi) paylaştığı `taban-fiyat` ve `taban-stok`.
- Gruplu item: `grup`, `agirlik`, isteğe bağlı `kategori`. Ağırlık tarifteki ham madde kütlesidir (külçe `1`, blok `9` gibi).
- Grupsuz item: `taban-fiyat` zorunlu; `taban-stok` verilmezse `motor.varsayilan-taban-stok` kullanılır.
- `min-fiyat` / `max-fiyat`: item'a özel mutlak orta fiyat sınırı, global `motor.fiyat-bant`'ı ezer.
- `alis-acik` / `satis-acik`: yön aç/kapa, varsayılan `true`. Yalnız YAML boolean kabul edilir.
- `aktif`: `false` yapılırsa ürün her oyuncu yüzeyinde bilinmeyen ürün gibi davranır (silinmez).
- `kategori`: GUI yerleşimi; kategorisiz item yüklenir ama kategori GUI'sinde görünmez.

Owner dosyayı elle düzenleyip `/market admin reload` çalıştırabilir, ama fiyat/yön/aktiflik değişiklikleri için **admin GUI daha az hata yapar**: gruplu item'ların taban/ağırlık matematiğini otomatik uygular, yeni kategori açar, reload'u kendisi tetikler.

## 5. Bilinen sınırlar — dürüst notlar

- **Duplicate/kopya item'lar markete girebilir** — bu sunucunuzun dupe açığıyla ilgili bir sorundur, plugin bunu NBT'den ayırt edemez. Plugin yalnızca fiyatı arz/talebe göre ayarlar; kopya item üretimini engellemek sunucu yöneticisinin işidir.
- **İlk gün fiyat göstergeleri taban değerde takılı görünebilir**: 24 saatlik değişim (kripto-gösterim açıksa) önceki günün snapshot'ına ihtiyaç duyar; ilk gün kapanmadan karşılaştırma tabanı yoktur, `0.00%` gösterilir. Fiyatın kendisi işlemlerle anında değişir; sabit görünen yalnız değişim yüzdesidir.
- **Restart'ta gün-içi sinyal sıfırlanır**: motorun bellek-içi gün-içi hacmi, satıcı yoğunlaşması ve günlük kalibrasyon EMA tohumu kalıcı değildir; ilk yeni gün kapanışında yeniden tohumlanır. Fiyat/grup durumu, haftalık saat profilleri ve işlem günlüğü kalıcıdır, bundan etkilenmez.
- **Vault + güncel bir ekonomi eklentisi (ör. EssentialsX Economy) zorunlu**. Vault yoksa sunucu MCPiyasa'yı hiç yüklemez. Vault var ama kayıtlı ekonomi sağlayıcısı yoksa MCPiyasa güvenli moda düşer, ticareti kapatır.
- Yalnız birebir vanilya item'lar satılabilir; yeniden adlandırılmış, büyülü veya hasarlı yığınlar market tarafından görülmez.
