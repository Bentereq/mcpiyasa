# MCPiyasa API Kılavuzu

Diğer eklentilerden MCPiyasa fiyatlarına ve ticaretine erişmek için: `MCPiyasaAPI`, Bukkit event'leri, PlaceholderAPI.

## 1. Servise erişim

MCPiyasa'yı doğrudan plugin sınıfına cast etmeyin; Bukkit `ServicesManager` üzerinden alın:

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
        return; // MCPiyasa yuklu degil veya servis henuz kayitli degil
    }

    BigDecimal price = api.getPrice(Material.IRON_INGOT);
    PriceQuoteDto quote = api.getQuote(Material.IRON_INGOT, 64, TradeSide.SELL);
    TradeResultDto result = api.trade(player, Material.IRON_INGOT, 64, TradeSide.SELL);
    List<double[]> history = api.getPriceHistory(Material.IRON_INGOT, 7);
    double dailyVolume = api.getDailyVolume(Material.IRON_INGOT);
}
```

## 2. Metotlar

```java
PriceQuoteDto getQuote(Material item, int amount, TradeSide side);
BigDecimal getPrice(Material item);
TradeResultDto trade(Player player, Material item, int amount, TradeSide side);
List<double[]> getPriceHistory(Material item, int days);
double getDailyVolume(Material item);
```

### `getQuote(item, amount, side)`

Item için değişmez bir fiyat teklifi döner. `PriceQuoteDto` alanları: `itemId`, `amount`, `total`, `unitAvg` — `total` para biriminde, `unitAvg` para/item biriminde, ikisi de iki ondalıklı.

- `amount`, `motor.max-islem-adet`'i aşıyorsa azami değere kırpılır; DTO'daki `amount` uygulanan (kırpılmış) adettir.
- Yalnız Bukkit **main thread**'de çağrılabilir.
- `IllegalArgumentException`: item null/bilinmiyorsa, `side` null ise veya kırpma sonrası adet pozitif değilse.
- `IllegalStateException("yon kapali")`: istenen yön (`alis-acik`/`satis-acik`) kapalıysa.
- Ayrıca main thread dışında veya API runtime'ı kullanılamaz durumdayken `IllegalStateException`.

### `getPrice(item)`

Item'ın güncel orta fiyatını (para/item, iki ondalıklı `BigDecimal`) döner. Yalnız main thread; item null/bilinmiyorsa `IllegalArgumentException`, runtime kullanılamaz durumdaysa `IllegalStateException`.

### `trade(player, item, amount, side)`

İşlemi yalnız main thread'de yürütür. `amount` aynı şekilde azami değere kırpılır. `TradeResultDto` alanları: `success`, `outcome`, `total`.

- `total`: teklif oluşmuşsa başarısız sonuçlarda da teklifin para tutarıdır; teklif oluşmadan reddedilen sonuçlarda `0.00`'dır — **başarısız sonuç para aktarıldığı anlamına gelmez**.
- Bilinmeyen item: exception fırlatmaz, `outcome = BILINMEYEN_ITEM` döner.
- Plugin runtime'ı kullanılamaz durumdaysa (ör. güvenli mod): para/envantere dokunmadan `outcome = UNAVAILABLE` döner.
- Main thread dışında çağrılırsa `IllegalStateException`.

### `getPriceHistory(item, days)` ve `getDailyVolume(item)`

DB tabanlıdır, **main thread dışından da çağrılabilir**; çağıran thread sorgu bitene kadar bloklanır.

- `getPriceHistory`: son `days` yerel takvim günü için eskiden yeniye `{gunSirasi, mid}` çiftleri (`double[]`). `gunSirasi` boyutsuz, sıfırdan başlar; `mid` para/item birimindedir. Snapshot olmayan gün son bilinen değeri taşır; henüz değer yoksa atlanır. `days <= 0` ise boş liste.
- `getDailyVolume`: sunucunun yerel gece yarısından itibaren tamamlanmış BUY+SELL işlemlerindeki **ham item adedi** toplamı; grup/oyuncu/ağırlık ile ölçeklenmez.
- İkisi de: item null/bilinmiyorsa `IllegalArgumentException`; takvim tarihi geçersizse veya API runtime'ı kullanılamaz durumdaysa `IllegalStateException`.

## 3. Bukkit event'leri

### `MarketPreTradeEvent` — `Cancellable`, işlem uygulanmadan önce

```java
public MarketPreTradeEvent(Player player, String itemId, int amount,
                           TradeSide side, double totalPrice);
Player getPlayer();
String getItemId();
int getAmount();
TradeSide getSide();
double getTotalPrice();
boolean isCancelled();
void setCancelled(boolean cancelled);
```

Yalnız iptal edilebilir; miktar veya fiyat mutasyonu **sunmaz**. İptal edilirse para/item aktarımı yapılmaz.

### `MarketTradeEvent` — yalnız başarıyla tamamlanan işlemden sonra

```java
public MarketTradeEvent(Player player, String itemId, int amount,
                        TradeSide side, double totalPrice, TradeOutcome outcome);
Player getPlayer();
String getItemId();
int getAmount();
TradeSide getSide();
double getTotalPrice();
TradeOutcome getOutcome();
```

### `MarketPriceChangeEvent` — başarılı işlem orta fiyatı gerçekten değiştirdiyse

```java
public MarketPriceChangeEvent(String itemId, double oldMid, double newMid);
String getItemId();
double getOldMid();
double getNewMid();
```

### Dinleyici örneği

```java
import com.mcpiyasa.api.events.MarketPreTradeEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class TradeGuard implements Listener {
    @EventHandler
    public void onPreTrade(MarketPreTradeEvent event) {
        if (event.getTotalPrice() > 100000.0) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("Tek islemde bu tutar cok yuksek.");
        }
    }
}
```

## 4. PlaceholderAPI

PlaceholderAPI kuruluysa expansion otomatik kaydolur; PAPI erişimi `ozellikler.kripto-gosterim` ayarından bağımsızdır.

| Placeholder | Döner |
|---|---|
| `%mcpiyasa_price_<ITEM_ID>%` | Orta fiyat, iki ondalık |
| `%mcpiyasa_buy_<ITEM_ID>%` | Birim alış fiyatı, iki ondalık |
| `%mcpiyasa_sell_<ITEM_ID>%` | Birim satış fiyatı, iki ondalık |
| `%mcpiyasa_change24h_<ITEM_ID>%` | 24s değişim yüzdesi, işaretli tek ondalık (ör. `+3.2`, `-1.0`) |

`<ITEM_ID>` yerine yüklü, büyük harfli item kimliği (`IRON_INGOT`). Örnek: `%mcpiyasa_buy_IRON_INGOT%`. Bilinmeyen placeholder veya item **boş metin** döner — hata fırlatmaz.

Fiyat kaynağı bir snapshot'tır ve 100 tikte bir yenilenir; placeholder değeri en çok ~5 saniye eski olabilir.

## 5. Soft-depend notu

PlaceholderAPI ve Citizens `softdepend`'dir. İkisi de kurulu olmasa bile `MCPiyasaAPI` normal çalışır — bağımlılık yalnız placeholder expansion'ı ve NPC trait'i içindir, servis kaydı ve ticaret akışı bunlara bağlı değildir.
