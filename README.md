# MCPiyasa

[English README](README_EN.md)

## 1. Hakkında

MCPiyasa, Minecraft sunucuları için fiyatları her alış ve satıştan sonra gerçek zamanlı değiştiren dinamik bir market eklentisidir; sanal stok, işlem yönü ve büyüklüğü, haftanın saatine göre hacim anormalliği, satıcı yoğunlaşması ve günlük öz-kalibrasyon olmak üzere 5 sinyali birlikte kullanır ve Spigot/Paper 1.16.5 → 1.21.4+ sürümlerini hedefler.

## 2. Özellikler

- Varsayılan erişim noktası olarak kategori ve item sayfalarından oluşan sandık GUI marketi.
- Spread, marjinal fiyatlama, orta fiyat bantları, sürekli toparlanma ve öz-kalibrasyon içeren çok sinyalli fiyat motoru.
- İsteğe bağlı komutla ticaret, `[Market]` tabelaları ve Citizens NPC erişimi.
- İsteğe bağlı 24 saatlik değişim yüzdesi, trend oku ve yükselen/düşen item görünümü.
- `MCPiyasaAPI`, üç özel Bukkit eventi ve PlaceholderAPI placeholder'ları.
- Açılış teşhisi, işlem hatası takibi ve ticareti kilitleyen güvenli mod.
- 10 dilde oyuncu mesajları (`dil` ayarı): Türkçe, İngilizce, Almanca, Fransızca, İspanyolca, Portekizce, Rusça, Lehçe, İtalyanca, Çince — her biri `messages_<dil>.yml` ile düzenlenebilir.
- Spigot ve Paper 1.16.5 → 1.21.4+ desteği; Java 8 bytecode, saf Bukkit API, NMS ve reflection yok.
- SQLite üzerinde fiyat durumu, işlem günlüğü, hacim profili ve günlük fiyat anlık görüntüsü kalıcılığı.
- Para ve item aktarımını doğrulayan, başarısız ikinci adımda ilk adımı geri almaya çalışan işlem akışı.

## 3. Kurulum

Gereksinimler:

- Spigot veya Paper 1.16.5 → 1.21.4+.
- **Vault ZORUNLUDUR.**
- Vault'a kayıt olan bir ekonomi sağlayıcısı **ZORUNLUDUR**; örneğin EssentialsX Economy.
- PlaceholderAPI yalnız placeholder'lar, Citizens yalnız NPC marketi için isteğe bağlıdır.

Kurulum adımları:

1. MCPiyasa jar dosyasını sunucunun `plugins/` klasörüne koyun.
2. Vault'u ve Vault uyumlu bir ekonomi eklentisini aynı klasöre kurun.
3. Sunucuyu başlatın. İlk açılışta `plugins/MCPiyasa/` altında `config.yml`, `items.yml`, `messages_tr.yml`, `messages_en.yml` ve SQLite veritabanı oluşturulur.
4. Ayarları düzenledikten sonra sunucuyu yeniden başlatın veya `/market admin reload` çalıştırın.

Vault yoksa `plugin.yml` içindeki zorunlu bağımlılık nedeniyle sunucu MCPiyasa'yı hiç yüklemez ve konsolda `Unknown dependency Vault` hatası görülür. Vault var fakat Vault'a kayıtlı bir ekonomi sağlayıcısı yoksa MCPiyasa güvenli modda açılır, ticareti kapatır ve konsola çerçeveli teşhis mesajı yazar.

## 4. Komutlar ve izinler

Ana komutun hazır alias'ları `/pazar` ve `/piyasa`dır. Item kimlikleri `IRON_INGOT` gibi Bukkit `Material` adlarıdır; büyük/küçük harf duyarsız kabul edilir.

| Komut | Açıklama | Gereken izin | Ek koşul |
|---|---|---|---|
| `/market` | Ana kategori GUI'sini açar. | `mcpiyasa.use` | Yalnız oyuncu |
| `/market fiyat <item>` | Bir adet için güncel alış ve satış fiyatını gösterir. | `mcpiyasa.use` | Yalnız oyuncu |
| `/market al <item> <adet>` | Belirtilen adedi satın alır. | `mcpiyasa.use` | `ozellikler.komut-ticaret: true`; yalnız oyuncu |
| `/market sat <item> <adet>` | Belirtilen adedi satar. | `mcpiyasa.use` | `ozellikler.komut-ticaret: true`; yalnız oyuncu |
| `/market admin` | **Tıklanabilir yönetim menüsünü** açar (birincil admin yolu). | `mcpiyasa.admin` | Yalnız oyuncu; `admin-komut` ayarından bağımsız, her zaman açılır |
| `/market admin reload` | Config, mesajlar, item tanımları ve servisleri yeniden yükler. | `mcpiyasa.admin` | Konsoldan da kullanılabilir; `ozellikler.admin-komut: false` iken kapalı |
| `/market admin durum` | Güvenli mod nedeni, item/grup/atlanan sayıları ve DB yolunu gösterir. | `mcpiyasa.admin` | Konsoldan da kullanılabilir; `admin-komut: false` iken kapalı |
| `/market admin liste [kategori] [sayfa]` | Yüklü ürünleri kategoriye göre gruplayarak güncel birim alış/satış fiyatlarıyla listeler. | `mcpiyasa.admin` | Salt okunur (işlem yaratmaz); konsoldan da kullanılabilir; sayfa başına 20 ürün; `admin-komut: false` iken kapalı |
| `/market admin fiyat <item> <yeni-taban-fiyat>` | Ürünün taban fiyatını günceller ve eski → yeni birim fiyatı yanıtlar. | `mcpiyasa.admin` | `itemekle` ile aynı grup tabanı sözleşmesi; yalnız yüklü ürünlerde çalışır; `admin-komut: false` iken kapalı |
| `/market admin sifirla <item>` | Item'ın grubunu taban stoğa, fiyat esnekliğini mevcut değerde bırakarak döndürür. | `mcpiyasa.admin` | Konsoldan da kullanılabilir; `admin-komut: false` iken kapalı |
| `/market admin itemekle [<MATERIAL>] <taban-fiyat> [taban-stok] [kategori]` | Item tanımını `items.yml` içinde günceller/ekler ve yeniden yükler. | `mcpiyasa.admin` | `MATERIAL` yazılmazsa **ana eldeki item** kullanılır (yalnız oyuncu); grup tabanı sözleşmesi aşağıdadır; `admin-komut: false` iken kapalı |
| `/market admin itemcikar <item>` | Item'ı `items.yml` içinden çıkarır ve yeniden yükler. | `mcpiyasa.admin` | Konsoldan da kullanılabilir; `admin-komut: false` iken kapalı |

`ozellikler.admin-komut: false` yapıldığında yukarıdaki **metin** alt komutları devre dışı kalır ve `komut.admin-komut-kapali` ile oyuncuyu menüye yönlendirir; `/market admin` (argümansız) yine her zaman menüyü açar. `mcpiyasa.admin` her durumda gereklidir.

| İzin | Varsayılan | Kapsam |
|---|---|---|
| `mcpiyasa.use` | Herkes | GUI, fiyat sorgusu, açık olan komut ticareti ve tabela kullanımı |
| `mcpiyasa.admin` | OP | Admin komutları, market tabelası oluşturma ve güvenli mod giriş uyarısı |

Tek işlemdeki adet `motor.max-islem-adet` ile sınırlıdır. Komutla verilen geçersiz veya sınırı aşan adet reddedilir; GUI ve API kendi sözleşmelerine göre sınırı uygular.

### Tıklanabilir yönetim menüsü (birincil admin aracı)

`/market admin` (argümansız) **öz ve detaylı** bir yönetim menüsü açar; komut ezberlemeden fare ile yönetirsiniz. Tüm menü ve tıklamalar `mcpiyasa.admin` ile korunur (açılışta **ve** her tıklamada denetlenir) ve oyuncu menülerinin güvenlik kalıbını izler.

- **Ana ekran:** ürün/grup sayısı, güvenli mod ve `hassasiyet` değerini gösteren durum kitabı; **Yeniden Yükle** ve **Durum** düğmeleri; **Item Ekle (elindeki)** düğmesi (ana eldeki materyali varsayılan taban fiyatla ekler ve düzenleyicisini açar); düzenlenecek **kategoriler**.
- **Kategori ekranı:** o kategorinin item'larını sayfalı listeler; her ikon oyuncu menüsüyle aynı **NEUTRAL önizleme** yoluyla canlı alış/satış birim fiyatını gösterir (işlem yaratmaz). Item'a tıklayınca düzenleyici açılır.
- **Item düzenleyici (kademeli düğmeler — yazmaya gerek yok):** fiyat için `-%10 / -1 / +1 / +%10` ve `÷2 / ×2` düğmeleri (metin `fiyat` komutuyla **aynı** grup/bağımsız yazma yolunu kullanır); **alış/satış yönü** için yeşil/kırmızı aç-kapat; **fiyatı sıfırla** (shift+tık); **ürünü sil** (shift+tık, ikinci shift ile onay). Her düzenleme `items.yml` yazımı + reload'dan geçer, böylece reload'dan sonra da kalıcıdır.

### Market listesini hızlı düzenleme (metin komutları)

`itemekle` iki biçim kabul eder; ilk token sayı ise materyal **elinizdeki item'dan** alınır:

```text
/market admin itemekle 12.5                 # eldeki item, taban fiyat 12.5
/market admin itemekle 12.5 500 madenler    # eldeki item + taban stok + kategori
/market admin itemekle DIAMOND 12.5         # materyal adıyla
/market admin itemekle DIAMOND 12.5 nadir   # sayı olmayan son token kategoridir
/market admin fiyat DIAMOND 15              # yalnız fiyatı değiştir
/market admin liste madenler                # kategoriyi fiyatlarıyla gör
```

Fiyattan sonraki token sayıysa `taban-stok`, değilse `kategori` olarak okunur. Verilen kategori `items.yml` içinde yoksa bölümü de oluşturulur (ikon: eklenen item, sıra: sondaki + 1) ve yanıt bunu bildirir; görünen adı için `messages_tr.yml`/`messages_en.yml` dosyalarına `kategori.<id>` ekleyin, eklenmezse GUI ham kimliği gösterir. Kategori sayısı 16'yı geçemez. Bütün formlar `mcpiyasa.admin` ister; `liste`, `fiyat`, `sifirla`, `itemcikar` ve materyal adı yazılan `itemekle` konsoldan da çalışır, eldeki item biçimi oyuncu ister.

### Hangi item'lar market tarafından görülür?

Yalnız **birebir vanilya** item'lar market tarafından görülür ve satılabilir; yeniden adlandırılmış, büyülü, hasarlı ya da başka bir NBT/meta taşıyan yığınlar sayılmaz ve satılamaz. Böyle bir yığın envanterdeyken satış denenirse oyuncuya "yeterli item yok" yerine kuralı söyleyen ayrı bir mesaj gider (`islem.degistirilmis-item`). Satın alma her zaman düz (metasız) item teslim eder.

## 5. Yapılandırma rehberi

### `config.yml`

Marketin oyuncuya görünen adı (menü başlığı ve sohbet öneki) varsayılan olarak `Market`tir; `messages_tr.yml` / `messages_en.yml` içindeki `prefix` ve `guimenu-baslik` anahtarlarıyla değiştirilebilir.

Aşağıdaki tablo dosyadaki **her anahtarı** kapsar.

| Anahtar | Varsayılan | Açıklama |
|---|---:|---|
| `dil` | `tr` | Yüklenecek mesaj dili: `tr` veya `en` (`messages_<dil>.yml`). |
| `motor.spread` | `0.10` | Oyuncunun alış fiyatıyla satış fiyatı arasındaki oran; geçerli aralık `0.02..1`dir. Yaklaşık `%1.7` altı, çapraz ağırlıklı ürünlerde konvekslik arbitrajına izin verebildiği için kabul edilmez. |
| `motor.toparlanma-katsayisi` | `0.05` | Her toparlanma tikinde sanal stoğun taban stoğa doğru kapatacağı fark oranı; sonlu `0..1`, `0` toparlanmayı kapatır. |
| `motor.toparlanma-dakika` | `10` | Toparlanma tikinin kaç dakikada bir çalışacağı; en az `1`. |
| `motor.anormallik-ussu` | `0.5` | Saat profiline göre anormal hacmin işlem etkisini ne kadar güçlendireceği; sonlu ve `>=0`, `0` bu sinyali nötrler. |
| `motor.anormallik-bant` | `[0.5, 3.0]` | Tam iki sonlu pozitif sayı; alt değer üst değerden küçük olmalıdır. |
| `motor.hhi-etki` | `1.0` | Günlük satıcı yoğunlaşmasının işlem etkisine katkısı; sonlu ve `>=0`, `0` etkiyi kapatır. |
| `motor.esneklik-bant` | `[0.25, 1.5]` | Tam iki sonlu pozitif sayı; alt değer üst değerden küçük olmalıdır. |
| `motor.esneklik-taban` | `0.6` | Referans günlük hacimde kullanılacak başlangıç esnekliği; `esneklik-bant` içinde olmalıdır. |
| `motor.referans-gunluk-hacim` | `2000` | Gün sonu hacmine göre öz-kalibrasyonun karşılaştırma noktası; `0`dan büyük olmalıdır. |
| `motor.fiyat-bant` | `[0.25, 4.0]` | Item'a özel override yoksa orta fiyatın taban fiyatın kaç katı arasında kalacağı. |
| `motor.max-islem-adet` | `2304` | Tek işlemde uygulanabilecek azami item adedi; `1` ile `100000` arasında olmalıdır. Teklif döngüsü adet başına ana thread'de çalıştığı için üst sınır bilinçlidir. |
| `motor.varsayilan-taban-stok` | `20000` | `taban-stok` verilmeyen bağımsız item/gruplar için sonlu ve `>0` sanal stok varsayılanı. |
| `motor.hassasiyet` | `1.0` | Fiyatın işlem hacmine tepki **hızını** ölçeklendiren küresel çarpan; sonlu ve `>0`. **DÜŞÜK** değer fiyatı sakinleştirir/yavaşlatır (büyük/kalabalık sunucu), **YÜKSEK** değer hassaslaştırır/hızlandırır (küçük sunucu). Örnek: `0.3` çok kalabalık, `1.0` varsayılan, `2.0` küçük sunucu. Tüm grupları eşit ölçeklendirdiği için başlangıç fiyatlarını **değiştirmez** ve asla arbitraj açmaz; yalnızca tepki hızı değişir. Çalışan sunucuda değiştirmek eğriyi yeniden ölçekler; kayıtlı stok en iyisi sıfırlanır (temiz başlangıç veya `/market admin sifirla`). |
| `motor.profil-alpha` | `0.3` | Haftalık saat-slotu hacim profilinde yeni gözlemin EMA ağırlığı; sonlu `(0,1]`. |
| `motor.profil-isinma-slot` | `3` | Bir saat-slotunda anormallik hesabı açılmadan önce gereken gözlem sayısı; o zamana kadar sinyal nötrdür. |
| `ozellikler.kripto-gosterim` | `false` | 24 saatlik yüzdeyi, trend okunu ve yükselen/düşenler düğmesini gösterir. |
| `ozellikler.komut-ticaret` | `false` | `/market al` ve `/market sat` komutlarını açar. |
| `ozellikler.tabela-market` | `false` | `[Market]` tabela oluşturma ve tıklama dinleyicisini açar. |
| `ozellikler.npc-market` | `false` | Citizens mevcutsa `mcpiyasa` NPC trait'ini kaydeder. |
| `ozellikler.creative-ticaret` | `true` | Varsayılan olarak MCPiyasa creative moda **karışmaz**. `false` yapılırsa creative moddaki oyuncunun her market işlemi `islem.creative-kapali` ile reddedilir; karar sunucu sahibinindir. |
| `ozellikler.admin-komut` | `true` | `/market admin` **metin** alt komutlarını (`reload`, `durum`, `liste`, `fiyat`, `sifirla`, `itemekle`, `itemcikar`) açar. `false` yapılırsa bu alt komutlar `komut.admin-komut-kapali` ile reddedilir ve yönetici tıklanabilir menüye yönlendirilir; `/market admin` (argümansız) yine her zaman menüyü açar. |
| `guvenli-mod.zorla-calistir` | `false` | Teşhis başarısız olsa da ticarete izin verir. Veri/para riski sunucu yöneticisine aittir. |

### `items.yml`

Dağıtımla gelen katalogda **151 YAML item anahtarı** vardır; sayı `rg '^  [A-Z0-9_]+:' src/main/resources/items.yml` yöntemiyle üretilir ve eski Minecraft sürümünde çözülemeyen `Material`lar çalışma zamanında atlanabilir. Fiyatlar vanilla survival'daki **birim başına edinim zorluğuna** göre hazırlanmıştır. Hasat yorumları brüt ürün ile yeniden dikim sonrası net ürünü ayırır. Deterministik craft/işleme akrabaları tarif kütlesi ağırlıklarıyla aynı grubu paylaşır; çok girdili ve kapalı çevrim riski taşıyan çıktıların varsayılan satış yönü kapalıdır. Yenilenemeyen Elytra ve ejderha yumurtası bilerek listede değildir.

- `kategoriler.<id>.ikon` GUI ikonunun `Material` adıdır; `sira` kategori sırasını belirler.
- `gruplar.<id>.taban-fiyat` ve `taban-stok`, akraba item'ların paylaştığı tek piyasanın temelini tanımlar.
- Gruplu item `grup`, `agirlik` ve isteğe bağlı `kategori` kullanır. Örneğin külçe ağırlığı `1`, blok ağırlığı `9` olduğunda ikisi aynı sanal stok havuzunu etkiler.
- Grupsuz item için `taban-fiyat` zorunludur; `taban-stok` yoksa `motor.varsayilan-taban-stok` kullanılır ve item kendi kimliğiyle bağımsız bir grup oluşturur.
- Item'a `min-fiyat` ve `max-fiyat` verilirse global `motor.fiyat-bant` yerine bu mutlak **orta fiyat** sınırları uygulanır; değerler sonlu ve pozitiftir, ikisi birlikteyse `min-fiyat < max-fiyat` olmalıdır.
- Item bazındaki `alis-acik` ve `satis-acik` anahtarları ilgili market yönünü açar/kapatır; varsayılanları `true`dur. Yalnız YAML boolean `true`/`false` kabul edilir; yazı veya sayı gibi bozuk değer taşıyan item güvenli biçimde atlanır.
- Item bazındaki `aktif` anahtarı ürünü **silmeden** tümüyle devre dışı bırakır; varsayılanı `true`dur. `aktif: false` olan ürün her oyuncu yüzeyinde (kategori GUI'si, `/market fiyat` ve tab-tamamlama, tabela sağ-tık, hareketliler, PlaceholderAPI) bilinmeyen ürün gibi **görünmez ve alınıp satılamaz**; oyuncu ürüne bir yolla ulaşırsa `islem.urun-devre-disi` mesajını alır. Yönetici yüzeyleri ürünü yeniden açmak için görmeye devam eder. Yön bayrakları gibi yalnız YAML boolean kabul edilir; bozuk değer taşıyan item güvenli biçimde atlanır. Yönetici, ürünü admin GUI'sindeki **Devre Dışı Bırak / Aktifleştir** düğmesiyle ya da `/market admin aktif <item> <ac|kapa>` komutuyla açıp kapatır.
- `kategori` GUI yerleşimini belirler. Kategorisiz geçerli item yüklenir ancak kategori GUI'sinde görünmez.
- Çalışan Minecraft sürümünde bulunmayan `Material` adları otomatik atlanır ve loglanır; diğer geçerli item'lar çalışmaya devam eder.
- Item olmayan veya yığınlanamayan `Material` anahtarları (`AIR`, `WATER`, `LAVA` …) reddedilir ve teşhis satırı yazılır; bunlar teslimat yolunu bozup eklentiyi güvenli moda düşürürdü.
- Hasar alabilen (`DIAMOND_PICKAXE`) veya varsayılan yığını meta taşıyan (`POTION`, `ENCHANTED_BOOK` …) materyalleri eklemek **serbesttir; karar sizindir**. Bu durumda açılışta bir INFO satırı yazılır: yalnız birebir aynı (sıfır hasarlı / metasız) yığınlar market tarafından görülür, satın alma da o düz varyantı teslim eder.

Örnek:

Bu örnek yalnız açıklama amaçlıdır; dağıtımla gelen `items.yml`, tarif invariantı için gereken birkaç item'da mutlak bant sınırlarını seçici olarak kullanır.

```yaml
gruplar:
  demir: { taban-fiyat: 10.0, taban-stok: 15000 }

itemler:
  IRON_INGOT:
    grup: demir
    agirlik: 1
    kategori: madenler
    min-fiyat: 3.00
    max-fiyat: 35.00
  IRON_BLOCK: { grup: demir, agirlik: 9, kategori: madenler }
  OBSIDIAN: { taban-fiyat: 8.0, taban-stok: 5000, kategori: madenler }
```

## 6. Fiyat motoru nasıl çalışır?

Formüle ihtiyaç duymadan sunucu yöneticisi gözüyle beş davranış:

1. **Her işlemde anlık tepki:** Oyuncular satın aldıkça sanal stok azalır ve sonraki fiyat yükselir; sattıkça stok artar ve sonraki fiyat düşer. Büyük işlem kendi içindeki birimleri de adım adım fiyatladığı için bütün yığını eski tepe fiyattan satamaz.
2. **Anormal hacme sert tepki:** Motor haftanın 168 saat-slotu için normal hacmi öğrenir. Alışılmadık bir saatte gelen yüksek tempo daha sert etki eder; o saatte zaten normal olan yoğunluk daha sakin karşılanır. Isınma verisi oluşana kadar bu saat profili nötrdür.
3. **Tek oyuncuda yoğunlaşmaya sert tepki:** Günlük satışın az sayıda oyuncuda toplanması piyasa etkisini artırır. **Tek satıcılı pazarda yoğunlaşma cezası yoktur; o senaryoyu anormallik sinyali yakalar.** Fiyat oyuncuya özel değildir; herkes aynı piyasayı görür.
4. **Akraba item'lar tek piyasadır:** Nugget, külçe ve blok gibi craft-akrabaları ağırlıklarıyla aynı sanal stok havuzuna girer; bir formu ucuzlatıp diğer forma çevirerek arbitraj üretmek zorlaşır.
5. **Gece sıfırlaması YOK; toparlanma süreklidir:** Fiyatlar gece yarısında sıfırlanmaz. Sanal stok ayarlanan aralıklarla yavaşça tabana yaklaşır; beklenip sömürülecek tek bir sıfırlama anı oluşmaz. Gün değişimi yalnız profil, yoğunlaşma, kalibrasyon ve anlık görüntü işlerini yürütür.

`motor.fiyat-bant` ve item override'ları **orta fiyat bandıdır**. Spread bu kelepçeden sonra uygulanır; örneğin orta fiyat üst sınırda `40.00` ve spread `0.10` ise oyuncunun alış fiyatı `42.00`, satış fiyatı `38.00` olabilir. Bu davranış bandın aşılması değil, alış/satış farkının korunmasıdır.

## 7. Uyumluluk

Her eklentiyle uyumluluk garanti edilemez; MCPiyasa Vault, PlaceholderAPI, Bukkit event'leri, kendi `InventoryHolder`'ı ve `plugin.yml` içindeki sabit komut alias'ları gibi standart yüzeyleri kullanır. Sorun tespit edilirse eklenti kendini güvenli moda alır ve sebebini söyler.

MCPiyasa yalnız Bukkit API kullanır; NMS ve reflection kullanmaz. PlaceholderAPI ve Citizens soft-dependency'dir. Çalışan sürümde bulunmayan yeni item'lar yüklemeyi bozmak yerine atlanır. Market GUI'leri kendi `InventoryHolder` tipiyle ayırt edilir; tabela etkileşimi başka bir koruma eklentisi blok kullanımını reddettiyse bu kararı geçersiz kılmaz. Dağıtımla gelen komut alias'ları `plugin.yml` içinde `pazar` ve `piyasa`dır.

| Platform | Doğrulama düzeyi |
|---|---|
| Paper 1.16.5 / 1.18.2 / 1.20.4 / 1.21.4 | Otomatik **4/4 boot matrisi**; ekonomi BUY/SELL/reload/restart akışı Paper 1.21.4'te manuel/bot smoke ile doğrulanır. |
| Spigot 1.16.5 → 1.21.4+ | Saf Bukkit/Spigot API uyumluluğu varsayımıyla desteklenir; otomatik matris Spigot boot kanıtı değildir. |

## 8. Güvenli mod

Açılış teşhisi şu kontrolleri yapar:

- Vault'a kayıtlı bir ekonomi sağlayıcısı var mı?
- `plugins/MCPiyasa/` veritabanı dizini yazılabilir mi?
- En az bir geçerli market item'ı yüklendi mi?
- `/market` komutu gerçekten MCPiyasa'ya mı ait?

Config, mesaj veya item dosyasının yüklenememesi; fiyat durumu/hacim profilinin geri getirilememesi; servis kablolama hatası gibi açılış sorunları da güvenli modu etkinleştirir. Çalışma sırasında beş dakika içinde üç ekonomi hatası oluşursa ticaret güvenli moda alınır; başarılı bir işlem hata serisini temizler.

Güvenli mod plugin'i kapatmaz: fiyat görüntüleme ve teşhis erişilebilir kalır, ticaret “bakımda” yanıtı verir, başarısız kontroller sebep ve çözümle konsola yazılır, `mcpiyasa.admin` yetkilileri girişte uyarılır. `/market admin durum` kayıtlı nedeni gösterir.

`guvenli-mod.zorla-calistir: true`, güvenli mod kilidini aşarak ticareti zorlar. Özellikle ekonomi sağlayıcısı, yazılabilir depolama veya geçerli item tanımı yokken para-item tutarlılığı garanti edilemez. Bu ayarı yalnız teşhis sebebini anlayıp riski bilinçli olarak kabul ediyorsanız geçici kullanın.

## 9. Geliştirici API'si

### ServicesManager ile API alma

MCPiyasa API'sini doğrudan plugin sınıfına cast etmek yerine Bukkit `ServicesManager` üzerinden alın:

```java
import com.mcpiyasa.api.MCPiyasaAPI;
import com.mcpiyasa.api.PriceQuoteDto;
import com.mcpiyasa.api.TradeResultDto;
import com.mcpiyasa.engine.TradeSide;
import java.math.BigDecimal;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

void useMCPiyasa(Player player) {
    RegisteredServiceProvider<MCPiyasaAPI> registration =
        Bukkit.getServicesManager().getRegistration(MCPiyasaAPI.class);
    MCPiyasaAPI api = registration == null ? null : registration.getProvider();
    if (api == null) {
        return;
    }

    BigDecimal price = api.getPrice(Material.IRON_INGOT);
    PriceQuoteDto quote = api.getQuote(Material.IRON_INGOT, 64, TradeSide.SELL);
    TradeResultDto result = api.trade(player, Material.IRON_INGOT, 64, TradeSide.SELL);
    List<double[]> history = api.getPriceHistory(Material.IRON_INGOT, 7);
    double dailyVolume = api.getDailyVolume(Material.IRON_INGOT);
}
```

Gerçek API imzaları:

```java
PriceQuoteDto getQuote(Material item, int amount, TradeSide side);
BigDecimal getPrice(Material item);
TradeResultDto trade(Player player, Material item, int amount, TradeSide side);
List<double[]> getPriceHistory(Material item, int days);
double getDailyVolume(Material item);
```

`getQuote(...)`, `getPrice(...)` ve `trade(...)` yalnız Bukkit ana thread'inde çağrılabilir. `getQuote(...)`, istenen item yönü kapalıysa `IllegalStateException("yon kapali")` fırlatır. `getPriceHistory(...)` ile `getDailyVolume(...)` DB tabanlıdır, başka thread'lerden çağrılabilir ve sorgu bitene kadar çağıranı bloklar. `getQuote(...)` ve `trade(...)`, azami işlem adedini `motor.max-islem-adet` değerine kırpar. `PriceQuoteDto` alanları `itemId`, `amount`, `total`, `unitAvg`; `TradeResultDto` alanları `success`, `outcome`, `total`dır. Para alanları iki ondalıklı `BigDecimal`dır. Geçmişteki her `double[]`, `{gunSirasi, mid}` biçimindedir; günlük hacim ham BUY+SELL item adedidir.

### Bukkit event'leri

Event sınıflarının gerçek public yapıcı/getter imzaları:

```java
// İşlem uygulanmadan önce; Cancellable.
public MarketPreTradeEvent(Player player, String itemId, int amount,
                           TradeSide side, double totalPrice);
public Player getPlayer();
public String getItemId();
public int getAmount();
public TradeSide getSide();
public double getTotalPrice();
public boolean isCancelled();
public void setCancelled(boolean cancelled);
```

```java
// Yalnız başarıyla tamamlanan işlemden sonra.
public MarketTradeEvent(Player player, String itemId, int amount,
                        TradeSide side, double totalPrice,
                        TradeOutcome outcome);
public Player getPlayer();
public String getItemId();
public int getAmount();
public TradeSide getSide();
public double getTotalPrice();
public TradeOutcome getOutcome();
```

```java
// Başarılı işlem orta fiyatı gerçekten değiştirdiyse.
public MarketPriceChangeEvent(String itemId, double oldMid, double newMid);
public String getItemId();
public double getOldMid();
public double getNewMid();
```

`MarketPreTradeEvent` yalnız iptal edilebilir; miktar veya fiyat mutasyonu sunmaz. İptal edilirse para/item aktarımı yapılmaz. `MarketTradeEvent` başarıdan sonra, `MarketPriceChangeEvent` ise başarılı ticarette işlem item'ının eski ve yeni orta fiyatı farklıysa yayınlanır.

### PlaceholderAPI

PlaceholderAPI kuruluysa expansion otomatik kaydolur. `<ITEM_ID>` yerine `IRON_INGOT` gibi yüklü, büyük harfli item kimliği yazın:

```text
%mcpiyasa_price_<ITEM_ID>%
%mcpiyasa_buy_<ITEM_ID>%
%mcpiyasa_sell_<ITEM_ID>%
%mcpiyasa_change24h_<ITEM_ID>%
```

Örnek: `%mcpiyasa_buy_IRON_INGOT%`. Fiyatlar iki ondalık, 24 saatlik değişim işaretli tek ondalık döner. Bilinmeyen placeholder veya item boş metin döndürür. PAPI erişimi `kripto-gosterim` ayarından bağımsızdır.

## 10. Çalışma zamanı sözleşmeleri ve sınırlar

- `/market admin itemekle` ve `/market admin fiyat`, item zaten bir gruba üyeyse istenen item fiyatını `agirlik` değerine bölerek **grubun `taban-fiyat`ını** değiştirir; verilen stok da ağırlıkla çarpılarak grubun `taban-stok`una yazılır. Bu nedenle aynı gruptaki bütün akrabalar etkilenir. İki komut tek yazma yolunu paylaşır.
- Eklenti devre dışı bırakılırken (`/stop`, PlugMan, başarısız reload geri alması) açık market menüleri kapatılır; aksi halde menüdeki isimli ikonlar dinleyici gidince oyuncuda kalabilirdi.
- Menü butonlarında yalnız tek/shift sol ve sağ tık işlem üretir. Çift tık, sayı tuşu, off-hand takası, orta tık ve atma iptal edilip yok sayılır; bu, çift tıklanan "Tümünü Sat"ın iki kez çalışmasını engeller.
- Citizens trait kaydı süreç ömrü boyunca tek yönlü bir latch'tir. `npc-market: true` sonrası `false` değerinin trait kaydını kaldırması için sunucuyu yeniden başlatın.
- Çok erken bootstrap çöküşünde yalnız konsol kaydı garanti edilir; çalışma zamanı bağlanabildiyse admin join uyarısı da verilir, çevrimiçi admin bildirimi her erken hata için garanti değildir.
- Başarılı işlem kalıcılığı asenkron kuyruğa gider. Normal kapanış kuyruğu flush eder; ani süreç/host çöküşünde kuyruktaki **birden çok kayıt** kaybolabilir. İlk storage yazma hatası ticareti güvenli modla kapatır, fakat kurtarma şansı için sonraki/kuyruktaki yazımlar denenmeye devam eder.
- PlaceholderAPI fiyat snapshot'ı 100 tikte bir yenilenir; placeholder fiyatları en çok yaklaşık **5 saniye** eski olabilir.
- Tabela marketi ana el sağ tıklama akışını destekler. Adventure veya spectator modundaki tabela etkileşimi destek sınırının dışındadır.

## 11. Kaynaktan derleme

Kaynaktan derleme için **JDK 17+** ve Maven gerekir:

```text
mvn package
```

Java 8 bytecode hedefli JAR `target/` altında üretilir.

## 12. Lisans ve Ticari Kullanım

Bu yazılım [**PolyForm Noncommercial 1.0.0**](LICENSE) lisansı ile korunmaktadır.

- 💻 **Kişisel, hobi, akademik ve ticari olmayan topluluk kullanımı:** ÜCRETSİZ.
- 🛠️ **Geliştirme, hata düzeltme, katkı:** serbest (fork/PR).
- 🏢 **Ticari / gelir getiren kullanım** (para kazandıran sunucular, şirketler): **ticari lisans zorunludur.**

### Ticari Lisans (tek seferlik ödeme)

Ticari kullanım için tek seferlik bir ücretle süresiz ticari lisans alınır:

👉 **Discord üzerinden `lebent` nickine ulaşın** — anlaşma sonrası satın alma
bağlantısı (Shopier) iletilir.

Ödeme sonrası, ticari projelerinizde PolyForm kısıtlamaları olmadan kullanma hakkı
veren bir lisans belgesi verilir. Ticari lisans; bug düzeltmeleri, güncellemeler ve
destek erişimini de kapsar. Neye izin verilip verilmediğinin sade özeti için
[LICENSE](LICENSE) dosyasının sonuna bakın.

## 12. SSS

### Fiyat neden düştü veya neden çıkmıyor?

Satış sanal stoğu büyüttüğü için fiyatı düşürür; aynı gruptaki akraba item satışları da aynı piyasayı etkiler. Büyük/anormal hacim ve yoğunlaşmış satış bu etkiyi büyütebilir. Orta fiyat ayrıca item'ın alt/üst bandında takılmış olabilir. Alım ters yönde etki eder; sürekli toparlanma fiyatı sıfırlamaz, yalnız taban seviyesine doğru kademeli taşır. Gerekirse `/market admin durum` ile durumu inceleyin ve `/market admin sifirla <item>` ile ilgili grubu taban stoğa döndürün.

### Yeniden adlandırılmış / büyülü item neden satılmıyor?

Bilinçlidir. Market yalnız birebir vanilya yığınları görür; yeniden adlandırılmış, büyülü veya hasarlı item satılamaz ve envanter sayımına girmez. Oyuncu bu durumda `islem.degistirilmis-item` mesajını alır. Kural, isimli GUI ikonlarının veya özel eşyaların nakde çevrilmesini engeller.

### Creative moddaki oyuncular market kullanabilir mi?

Varsayılan olarak evet; MCPiyasa creative moda karışmaz. Kapatmak isterseniz `config.yml` içinde `ozellikler.creative-ticaret: false` yapıp `/market admin reload` çalıştırın; o andan itibaren creative moddaki her işlem reddedilir.

### Eski Minecraft sürümünde “item atlandı” logu normal mi?

Evet. `items.yml`, yeni sürümlerdeki bazı item'ları da taşır. Örneğin 1.16.5'in bilmediği bir `Material` o sunucuda atlanır ve loglanır; bu hata değildir, tanınan item'lar normal çalışır. Yazım hataları da aynı atlananlar listesine girebildiği için beklemediğiniz bir kimliği kontrol edin.

### NPC marketi nasıl kurulur?

Citizens'ı kurun, `ozellikler.npc-market: true` yapın ve MCPiyasa'yı yeniden yükleyin/başlatın. Citizens ile oluşturduğunuz NPC'yi seçtikten sonra:

```text
/trait mcpiyasa
```

Oyuncu bu NPC'ye sağ tıkladığında ana market GUI'si açılır.

### Market tabelasının formatı nedir?

Önce `ozellikler.tabela-market: true` yapın. Yalnız `mcpiyasa.admin` yetkilisi şu ilk iki satırla tabela oluşturabilir; üçüncü ve dördüncü satırı MCPiyasa doldurur:

```text
[Market]
IRON_INGOT
```

Kullanım için `mcpiyasa.use` gerekir. Sağ tıklamada alış/satış fiyatları anlık tekliflerle tazelenir ve item menüsü açılır. Bir koruma eklentisi blok kullanımını reddederse MCPiyasa bunu aşmaz.

### Kripto gösterimi nasıl açılır?

`config.yml` içinde `ozellikler.kripto-gosterim: true` yapıp `/market admin reload` çalıştırın. Kategori/item ekranlarında 24 saatlik yüzde ve trend oku, ana menüde yükselen/düşenler erişimi görünür.

### Komut ticareti nasıl açılır?

`config.yml` içinde `ozellikler.komut-ticaret: true` yapıp `/market admin reload` çalıştırın. Ardından `/market al <item> <adet>` ve `/market sat <item> <adet>` kullanılabilir.

### Taze sunucuda yükselenler/düşenler neden `0.00%` gösteriyor?

Normaldir. 24 saatlik karşılaştırma için önceki bir günlük fiyat snapshot'ı gerekir. İlk gün kapanmadan karşılaştırma tabanı yoktur; bu durumda değişim kodun iki ondalıklı biçimiyle bilinçli olarak `0.00%` gösterilir.

### Restart'ta gün içi hacim neden sıfırlanıyor?

Bu bilinçlidir. Motorun bellek-içi gün içi hacmi, satıcı yoğunlaşması ve günlük kalibrasyon EMA tohumu restart'ta geri yüklenmez; ilk yeni gün kapanışı EMA'yı yeniden tohumlar. Fiyat/grup durumu, haftalık saat profilleri, işlem günlüğü ve alınmış günlük snapshot'lar kalıcıdır; API'nin işlem günlüğünden hesapladığı günlük ham hacim bundan ayrıdır.
