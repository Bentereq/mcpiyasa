# MCPiyasa test rehberi

Bu klasörde iki test akışı vardır:

- `boot-matrix.ps1`, Paper 1.16.5/1.18.2/1.20.4/1.21.4 sürümlerini Vault var fakat ekonomi sağlayıcısı yokken sırayla açar. Bu durumda MCPiyasa'nın güvenli moda geçmesi beklenen ve başarılı davranıştır.
- Aşağıdaki elle smoke testi, Paper 1.21.4 + Vault + EssentialsX ile gerçek ekonomi ve oyuncu akışını kontrol eder.

## Otomatik boot matrisi

Depo kökünde önce güncel JAR'ı üretin, sonra matrisi çalıştırın:

```powershell
bash mvnj -q package
powershell -ExecutionPolicy Bypass -File scripts/boot-matrix.ps1
```

Betik gereken Temurin JDK sürümlerini varsayılan olarak `%USERPROFILE%\.jdks` altında arar ve eksik olanları indirir; `USERPROFILE` burada yalnız örnek/varsayılan konumdur. Paper ve Vault indirmeleri `scripts/cache/` altında tutulur. `scripts/run/`, loglarla birlikte tam sunucu dizinlerini koruduğu için toplam boyutu yüzlerce MB olabilir; test artefaktlarını temizlemek için `Remove-Item -LiteralPath scripts/run -Recurse -Force` çalıştırabilirsiniz. Sunucular aynı anda değil, sırayla çalışır. Her sunucu 120 saniye içinde hazır olmazsa durdurulur; normal kapanış için stdin'e `stop` gönderilir, gerekirse process tree zorla kapatılır.

Özel Java yürütülebilirleri veya daha küçük bir sürüm kümesi de verilebilir:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/boot-matrix.ps1 `
  -Java17 "C:\Java\jdk-17\bin\java.exe" `
  -Versions 1.18.2,1.20.4
```

Sonuç tablosunda dört sürümün de `boot`, `mcpiyasa` ve `ekonomi-teshis` sütunları `PASS`, `hata` sütunu `YOK` olmalıdır. `ekonomi-teshis`, Vault mevcutken ekonomi sağlayıcısının bulunamadığını bildiren uyarıyı ölçer. JDK indirilemezse ilgili sürüm açıkça `SKIP` görünür ve diğer sürümler devam eder; ancak release kapısında herhangi bir `SKIP` veya `FAIL` çıkış kodunu `1` yapar. Bu otomatik akış yalnız **4/4 Paper boot** kanıtıdır; gerçek BUY/SELL/reload/restart ekonomi kapısı aşağıdaki **Paper 1.21.4 manuel smoke** testidir. Spigot desteği Bukkit/Spigot API uyumluluğu varsayımına dayanır, bu betik Spigot sunucusu çalıştırmaz.

## 1.21.4 elle smoke kontrolü

> **Port uyarısı:** Varsayılan Minecraft portu `25565`i kullanmayın. `25904` gibi boş bir loopback portu seçin ve başlamadan önce portun başka bir süreç tarafından kullanılmadığını kontrol edin.

### 1. Sunucuyu hazırlama

1. Temiz bir Paper 1.21.4 dizini oluşturun. `eula.txt` içine `eula=true` yazın.
2. `server.properties` içinde en az şu değerleri ayarlayın:

   ```properties
   server-ip=127.0.0.1
   server-port=25904
   online-mode=false
   ```

3. `plugins/` altına güncel `target/MCPiyasa-0.1.0-SNAPSHOT.jar`, Vault 1.7.3 ve 1.21.4 ile uyumlu güncel EssentialsX JAR'ını koyun.
4. Sunucuyu JDK 21 ve sınırlı bellekle açın:

   ```powershell
   & "$env:USERPROFILE\.jdks\jdk-21.0.12+8\bin\java.exe" -Xms256M -Xmx1024M -jar paper.jar --nogui
   ```

5. Konsolda `Done`, Vault/EssentialsX enable ve `MCPiyasa etkin.` satırlarını doğrulayın. Ekonomi teşhisi başarısız veya güvenli mod aktif olmamalıdır.
6. İstemciden `127.0.0.1:25904` adresine girin. Konsoldan test oyuncusuna OP ve bakiye verin: `op <oyuncu>` ve `eco give <oyuncu> 10000`.

### 2. GUI, alış ve satış

1. Oyuncuyla `/market` çalıştırın; ana menünün açıldığını ve kategori ikonlarının tıklanabildiğini doğrulayın.
2. Bir kategoriden `IRON_INGOT` öğesini açın. Alış/satış fiyatlarının ve adet kontrollerinin görünür olduğunu doğrulayın.
3. Alış kontrolünden bir miktar satın alın. Bakiyenin azalması, eşyanın envantere gelmesi ve başarı mesajı beklenir.
4. Satış kontrolünden aldığınız eşyanın bir kısmını satın. Bakiyenin artması, eşyanın eksilmesi ve başarı mesajı beklenir.
5. İsteğe bağlı komut akışı için `plugins/MCPiyasa/config.yml` içinde `ozellikler.komut-ticaret: true` yapıp yeniden başlatın; `/market al IRON_INGOT 1` ve `/market sat IRON_INGOT 1` komutlarını da doğrulayın.

### 3. Yeniden başlatma ve kalıcılık

1. İşlemden sonra `/market fiyat IRON_INGOT` çıktısını veya GUI fiyatını kaydedin.
2. Konsola `stop` yazarak temiz kapatın; Java prosesini doğrudan öldürmeyin.
3. Aynı dizinden tekrar başlatın ve oyuncu yeniden girdikten sonra aynı fiyatı kontrol edin.
4. Fiyatın işlem sonrası değeriyle korunması beklenir. Başlangıç taban fiyatına dönmesi veya SQLite hatası görülmesi FAIL'dir.

### 4. Kripto görünümü

1. Sunucuyu durdurun. `plugins/MCPiyasa/config.yml` içinde `ozellikler.kripto-gosterim: true` yapın ve yeniden başlatın.
2. `/market` ana menüsünde yükselenler/düşenler girişinin görünür olduğunu doğrulayın.
3. Bir kategori ve öğe ekranını açın. Lore içinde trend oku ile `24s değişim:` satırının görünmesi beklenir; geçmiş yetersizse `0.00%` kabul edilir.
4. Özelliği tekrar `false` yapıp yeniden başlattığınızda kripto girişinin ve `24s değişim:` satırının kaybolduğunu doğrulayın.

Her adımda konsolda `SEVERE`/`ERROR` ile birlikte `com.mcpiyasa` görülürse veya `at com.mcpiyasa.` ile başlayan bir stack frame oluşursa smoke testi FAIL sayılır. Release doğrulaması yalnız **4/4 boot + 1.21.4 manuel/bot ekonomi smoke** birlikte geçtiğinde tamamdır. Test bitince konsoldan `stop` gönderin ve seçtiğiniz portun serbest kaldığını doğrulayın.
