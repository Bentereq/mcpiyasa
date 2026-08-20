# MCPiyasa

[Türkçe README](README.md)

## 1. About

MCPiyasa is a dynamic market plugin for Minecraft servers that changes prices in real time after every purchase and sale; it combines 5 signals—virtual stock, trade direction and size, volume anomalies by hour of the week, seller concentration, and daily self-calibration—and targets Spigot/Paper 1.16.5 → 1.21.4+.

## 2. Features

- A chest GUI market with category and item pages as the default access point.
- A multi-signal price engine with spread, marginal pricing, mid-price bands, continuous recovery, and self-calibration.
- Optional command trading, `[Market]` signs, and Citizens NPC access.
- Optional 24-hour change percentages, trend arrows, and gainers/losers view.
- `MCPiyasaAPI`, three custom Bukkit events, and PlaceholderAPI placeholders.
- Startup diagnostics, trade-error tracking, and a safe mode that locks trading.
- Player messages in 10 languages (`dil` setting): Turkish, English, German, French, Spanish, Portuguese, Russian, Polish, Italian, Chinese — each editable via `messages_<lang>.yml`.
- Spigot and Paper 1.16.5 → 1.21.4+ support; Java 8 bytecode, pure Bukkit API, no NMS or reflection.
- Persistence of price state, transaction logs, volume profiles, and daily price snapshots in SQLite.
- A transaction flow that validates money and item transfers and attempts to roll back the first step if the second fails.

## 3. Installation

Requirements:

- Spigot or Paper 1.16.5 → 1.21.4+.
- **Vault is REQUIRED.**
- An economy provider registered with Vault is **REQUIRED**; for example, EssentialsX Economy.
- PlaceholderAPI is optional and only needed for placeholders; Citizens is optional and only needed for the NPC market.

Installation steps:

1. Put the MCPiyasa jar in the server's `plugins/` directory.
2. Install Vault and a Vault-compatible economy plugin in the same directory.
3. Start the server. On first startup, `config.yml`, `items.yml`, `messages_tr.yml`, `messages_en.yml`, and the SQLite database are created under `plugins/MCPiyasa/`.
4. After editing settings, restart the server or run `/market admin reload`.

Without Vault, the server does not load MCPiyasa at all because `plugin.yml` declares it as a required dependency; the console reports `Unknown dependency Vault`. If Vault is installed but no economy provider is registered with it, MCPiyasa starts in safe mode, disables trading, and writes a framed diagnostic message to the console.

## 4. Commands and permissions

The bundled aliases for the main command are `/pazar` and `/piyasa`. Item IDs are Bukkit `Material` names such as `IRON_INGOT`; input is accepted case-insensitively.

| Command | Description | Permission | Additional condition |
|---|---|---|---|
| `/market` | Opens the main category GUI. | `mcpiyasa.use` | Players only |
| `/market fiyat <item>` | Shows the current one-item buy and sell prices. | `mcpiyasa.use` | Players only |
| `/market al <item> <amount>` | Buys the specified amount. | `mcpiyasa.use` | `ozellikler.komut-ticaret: true`; players only |
| `/market sat <item> <amount>` | Sells the specified amount. | `mcpiyasa.use` | `ozellikler.komut-ticaret: true`; players only |
| `/market admin` | Opens the **clickable admin menu** (the primary admin path). | `mcpiyasa.admin` | Players only; independent of `admin-komut`, always opens |
| `/market admin reload` | Reloads config, messages, item definitions, and services. | `mcpiyasa.admin` | Also available from the console; disabled when `ozellikler.admin-komut: false` |
| `/market admin durum` | Shows safe-mode reason, item/group/skipped counts, and the DB path. | `mcpiyasa.admin` | Also available from the console; disabled when `admin-komut: false` |
| `/market admin liste [category] [page]` | Lists loaded items grouped by category with their current buy/sell unit prices. | `mcpiyasa.admin` | Read-only (creates no trade); also available from the console; 20 items per page; disabled when `admin-komut: false` |
| `/market admin fiyat <item> <new-base-price>` | Updates the item's base price and replies with the old → new unit price. | `mcpiyasa.admin` | Same group-base contract as `itemekle`; works only on loaded items; disabled when `admin-komut: false` |
| `/market admin sifirla <item>` | Returns the item's group to base stock while retaining its current price elasticity. | `mcpiyasa.admin` | Also available from the console; disabled when `admin-komut: false` |
| `/market admin itemekle [<MATERIAL>] <base-price> [base-stock] [category]` | Updates/adds the item definition in `items.yml` and reloads. | `mcpiyasa.admin` | With no `MATERIAL`, the **item in the main hand** is used (player only); the group-base contract is below; disabled when `admin-komut: false` |
| `/market admin itemcikar <item>` | Removes the item from `items.yml` and reloads. | `mcpiyasa.admin` | Also available from the console; disabled when `admin-komut: false` |

When `ozellikler.admin-komut: false`, the **text** subcommands above are disabled and reply with `komut.admin-komut-kapali`, directing the admin to the menu; `/market admin` (no args) still always opens the menu. `mcpiyasa.admin` is required in all cases.

| Permission | Default | Scope |
|---|---|---|
| `mcpiyasa.use` | Everyone | GUI, price lookup, enabled command trading, and sign use |
| `mcpiyasa.admin` | OP | Admin commands, market-sign creation, and safe-mode login warning |

The amount in a single trade is limited by `motor.max-islem-adet`. An invalid or over-limit amount supplied through a command is rejected; the GUI and API apply the limit according to their own contracts.

### Clickable admin menu (the primary admin tool)

`/market admin` (no args) opens a **concise yet detailed** admin menu so you can manage the market with the mouse, without memorizing commands. The whole menu and every click are guarded by `mcpiyasa.admin` (checked on open **and** on every click) and follow the same security pattern as the player menus.

- **Main screen:** a status book showing item/group counts, safe-mode state, and the `hassasiyet` value; **Reload** and **Status** buttons; an **Add Item (in hand)** button (adds the main-hand material at a default base price and opens its editor); and the **categories** to edit.
- **Category screen:** a paginated list of that category's items; each icon shows the live buy/sell unit price via the same **NEUTRAL preview** path the player GUI uses (no trade). Click an item to open the editor.
- **Item editor (stepped buttons — no typing):** price buttons `-10% / -1 / +1 / +10%` and `÷2 / ×2` (they use the **same** grouped/standalone write path as the text `fiyat` command); green/red **buy/sell direction** toggles; **reset price** (shift-click); **delete item** (shift-click, second shift to confirm). Every edit goes through the `items.yml` write + reload, so it survives a reload.

### Editing the market list quickly (text commands)

`itemekle` accepts two forms; when the first token is a number, the material is taken from **the item in your hand**:

```text
/market admin itemekle 12.5                 # held item, base price 12.5
/market admin itemekle 12.5 500 madenler    # held item + base stock + category
/market admin itemekle DIAMOND 12.5         # by material name
/market admin itemekle DIAMOND 12.5 nadir   # a non-numeric last token is the category
/market admin fiyat DIAMOND 15              # change only the price
/market admin liste madenler                # view a category with its prices
```

A token after the price is read as `taban-stok` when numeric and as `kategori` otherwise. If the given category does not exist in `items.yml`, its section is created too (icon: the added item, order: last + 1) and the reply says so; add `kategori.<id>` to `messages_tr.yml`/`messages_en.yml` for its display name, otherwise the GUI shows the raw ID. There can be at most 16 categories. Every form requires `mcpiyasa.admin`; `liste`, `fiyat`, `sifirla`, `itemcikar`, and `itemekle` with an explicit material also work from the console, while the held-item form requires a player.

### Which items does the market see?

Only **exact vanilla** items are seen by the market and can be sold; renamed, enchanted, damaged, or otherwise NBT/meta-bearing stacks are not counted and cannot be sold. When such a stack is in the inventory and a sale is attempted, the player receives a dedicated message stating the rule instead of "not enough items" (`islem.degistirilmis-item`). Buying always delivers a plain (meta-free) item.

## 5. Configuration guide

### `config.yml`

The market's player-visible name (menu title and chat prefix) defaults to `Market`; change it with the `prefix` and `guimenu-baslik` keys in `messages_tr.yml` / `messages_en.yml`.

The following table covers **every key** in the file.

| Key | Default | Description |
|---|---:|---|
| `dil` | `tr` | Message language to load: `tr` or `en` (`messages_<dil>.yml`). |
| `motor.spread` | `0.10` | Ratio between the player's buy and sell prices; valid range is `0.02..1`. Values below roughly `1.7%` are rejected because they can permit cross-weight convexity arbitrage. |
| `motor.toparlanma-katsayisi` | `0.05` | Fraction of the gap closed on each recovery tick; finite `0..1`, and `0` disables recovery. |
| `motor.toparlanma-dakika` | `10` | Number of minutes between recovery ticks; at least `1`. |
| `motor.anormallik-ussu` | `0.5` | Abnormal-volume impact exponent; finite and `>=0`, and `0` neutralizes this signal. |
| `motor.anormallik-bant` | `[0.5, 3.0]` | Exactly two finite positive numbers, with the lower below the upper. |
| `motor.hhi-etki` | `1.0` | Daily seller-concentration contribution; finite and `>=0`, and `0` disables it. |
| `motor.esneklik-bant` | `[0.25, 1.5]` | Exactly two finite positive numbers, with the lower below the upper. |
| `motor.esneklik-taban` | `0.6` | Initial elasticity at the reference daily volume; must fall within `esneklik-bant`. |
| `motor.referans-gunluk-hacim` | `2000` | Comparison point for self-calibration at day close; must be greater than `0`. |
| `motor.fiyat-bant` | `[0.25, 4.0]` | Allowed multiples of the base price for the mid price when there is no item-specific override. |
| `motor.max-islem-adet` | `2304` | Maximum number of items applied in a single trade; must be between `1` and `100000`. The upper bound is deliberate because the quote loop runs per unit on the main thread. |
| `motor.varsayilan-taban-stok` | `20000` | Finite, `>0` default virtual stock for standalone items/groups that omit `taban-stok`. |
| `motor.hassasiyet` | `1.0` | Global multiplier scaling how strongly price reacts to trade volume (its reaction **speed**); finite and `>0`. A **LOW** value calms/slows price movement (big/crowded servers), a **HIGH** value makes it more sensitive/faster (small servers). Example: `0.3` very crowded, `1.0` default, `2.0` small server. It scales every group equally, so it leaves starting prices **unchanged** and never opens arbitrage; only the reaction speed changes. Changing it on a running server rescales the curve, so stored stock is best reset (a fresh start or `/market admin sifirla`). |
| `motor.profil-alpha` | `0.3` | EMA weight of a new weekly hourly-profile observation; finite `(0,1]`. |
| `motor.profil-isinma-slot` | `3` | Observations required in an hourly slot before anomaly calculation is enabled; the signal is neutral until then. |
| `ozellikler.kripto-gosterim` | `false` | Shows the 24-hour percentage, trend arrow, and gainers/losers button. |
| `ozellikler.komut-ticaret` | `false` | Enables `/market al` and `/market sat`. |
| `ozellikler.tabela-market` | `false` | Enables `[Market]` sign creation and click handling. |
| `ozellikler.npc-market` | `false` | Registers the `mcpiyasa` NPC trait when Citizens is present. |
| `ozellikler.creative-ticaret` | `true` | By default MCPiyasa **does not interfere** with creative mode. Set it to `false` and every market trade from a creative-mode player is rejected with `islem.creative-kapali`; the decision belongs to the server owner. |
| `ozellikler.admin-komut` | `true` | Enables the `/market admin` **text** subcommands (`reload`, `durum`, `liste`, `fiyat`, `sifirla`, `itemekle`, `itemcikar`). When set to `false`, those subcommands are rejected with `komut.admin-komut-kapali` and the admin is directed to the clickable menu; `/market admin` (no args) still always opens the menu. |
| `guvenli-mod.zorla-calistir` | `false` | Allows trading despite failed diagnostics. The owner accepts the data/money risk. |

### `items.yml`

The bundled catalog has **151 YAML item keys**, counted with `rg '^  [A-Z0-9_]+:' src/main/resources/items.yml`; `Material` values unavailable on an older server can still be skipped at runtime. Prices follow **acquisition difficulty per unit** in vanilla survival, and crop comments distinguish gross yield from net yield after replanting. Deterministic crafting/processing relatives share recipe-mass weights in one group; multi-input outputs that could close a market loop have selling disabled by default. Non-renewable items such as Elytra and the dragon egg are deliberately excluded.

- `kategoriler.<id>.ikon` is the GUI icon's `Material` name; `sira` controls category order.
- `gruplar.<id>.taban-fiyat` and `taban-stok` define the foundation of the single market shared by related items.
- A grouped item uses `grup`, `agirlik`, and an optional `kategori`. For example, with ingot weight `1` and block weight `9`, both affect the same virtual stock pool.
- A standalone item requires `taban-fiyat`; if `taban-stok` is absent, `motor.varsayilan-taban-stok` is used and the item creates an independent group under its own ID.
- Item-level `min-fiyat` and `max-fiyat` values replace `motor.fiyat-bant` with absolute **mid-price** bounds; values must be finite and positive, and together must satisfy `min-fiyat < max-fiyat`.
- Item-level `alis-acik` and `satis-acik` keys enable or disable the respective market direction and default to `true`. Only YAML booleans `true`/`false` are accepted; an item with a malformed string or numeric value is safely skipped.
- The item-level `aktif` key fully disables a product **without deleting it**; it defaults to `true`. A product with `aktif: false` is **invisible and untradeable** on every player surface (category GUI, `/market fiyat` and tab-completion, sign right-click, movers, PlaceholderAPI), treated like an unknown item; a player who reaches it anyway gets the `islem.urun-devre-disi` message. Admin surfaces still see it so it can be re-enabled. Like the direction flags only YAML booleans are accepted; a malformed value safely skips the item. An admin toggles it with the **Disable Item / Enable Item** button in the admin GUI or the `/market admin aktif <item> <ac|kapa>` command.
- `kategori` controls GUI placement. A valid item without a category is loaded but does not appear in a category GUI.
- `Material` names unavailable in the running Minecraft version are skipped and logged automatically; other valid items continue to work.
- Keys that are not items or cannot stack (`AIR`, `WATER`, `LAVA`, …) are rejected with a diagnostic line; they would break the delivery path and drop the plugin into safe mode.
- Adding damageable materials (`DIAMOND_PICKAXE`) or materials whose default stack carries meta (`POTION`, `ENCHANTED_BOOK`, …) is **allowed; it is your call**. An INFO line is logged at startup: only exactly identical (undamaged / meta-free) stacks are seen by the market, and buying delivers that plain variant.

Example:

This example is illustrative; the shipped `items.yml` selectively uses absolute bounds for the few items that need them to preserve the recipe invariant.

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

## 6. How does the price engine work?

Five behaviors in owner-friendly language, with no formulas required:

1. **Immediate response to every trade:** As players buy, virtual stock falls and the next price rises; as they sell, stock grows and the next price falls. A large order prices its own units step by step, so an entire stack cannot be sold at the old peak price.
2. **Strong response to abnormal volume:** The engine learns normal volume for 168 hour-of-week slots. High activity at an unusual hour has a stronger effect, while volume that is normal for that hour is treated more calmly. The hourly profile remains neutral until it has warmed up.
3. **Strong response to single-player concentration:** When daily sales are concentrated among a few players, market impact increases. **There is no concentration penalty in a one-seller market; the anomaly signal catches that scenario.** Prices are not player-specific; everyone sees the same market.
4. **Related items are one market:** Craft relatives such as nuggets, ingots, and blocks enter the same virtual stock pool with their weights, making cross-form arbitrage much harder.
5. **There is NO nightly reset; recovery is continuous:** Prices do not reset at midnight. Virtual stock moves gradually toward its base at the configured interval, leaving no single reset moment to wait for and exploit. A day change only runs profile, concentration, calibration, and snapshot work.

`motor.fiyat-bant` and item overrides bound the **mid price**. Spread is applied after that clamp; for example, a `40.00` upper mid bound with `0.10` spread can produce a player buy price of `42.00` and a sell price of `38.00`. This preserves the buy/sell spread and is not a band violation.

## 7. Compatibility

Compatibility with every plugin cannot be guaranteed. MCPiyasa uses standard surfaces—Vault, PlaceholderAPI, Bukkit events, its own `InventoryHolder`, and the fixed command aliases in `plugin.yml`—and enters safe mode with a reason when it detects a problem.

MCPiyasa uses only the Bukkit API, with no NMS or reflection. PlaceholderAPI and Citizens are soft dependencies. Items from newer versions that do not exist on the running server are skipped instead of breaking startup. Market GUIs are identified through MCPiyasa's own `InventoryHolder` type; sign interaction does not override another protection plugin when it denies block use. The bundled command aliases in `plugin.yml` are `pazar` and `piyasa`.

| Platform | Verification level |
|---|---|
| Paper 1.16.5 / 1.18.2 / 1.20.4 / 1.21.4 | Automated **4/4 boot matrix**; economy BUY/SELL/reload/restart is manually/bot-smoked on Paper 1.21.4. |
| Spigot 1.16.5 → 1.21.4+ | Supported by Bukkit/Spigot API compatibility assumption; the automated matrix is not Spigot boot evidence. |

## 8. Safe mode

Startup diagnostics check the following:

- Is an economy provider registered with Vault?
- Is the `plugins/MCPiyasa/` database directory writable?
- Was at least one valid market item loaded?
- Does the `/market` command actually belong to MCPiyasa?

Startup problems such as failure to load config, messages, or item files; failure to restore price state/volume profiles; or service wiring errors also activate safe mode. During operation, three economy errors within five minutes place trading in safe mode; a successful trade clears the error streak.

Safe mode does not disable the plugin: price display and diagnostics remain available, trading returns a maintenance response, failed checks are written to the console with a reason and fix, and users with `mcpiyasa.admin` are warned when they join. `/market admin durum` shows the stored reason.

`guvenli-mod.zorla-calistir: true` bypasses the safe-mode trading lock. Money/item consistency cannot be guaranteed when an economy provider, writable storage, or a valid item definition is missing. Use this setting temporarily only when you understand the diagnostic reason and consciously accept the risk.

## 9. Developer API

### Obtaining the API through ServicesManager

Obtain the MCPiyasa API through Bukkit's `ServicesManager` instead of casting the plugin class:

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

Actual API signatures:

```java
PriceQuoteDto getQuote(Material item, int amount, TradeSide side);
BigDecimal getPrice(Material item);
TradeResultDto trade(Player player, Material item, int amount, TradeSide side);
List<double[]> getPriceHistory(Material item, int days);
double getDailyVolume(Material item);
```

`getQuote(...)`, `getPrice(...)`, and `trade(...)` may only be called on the Bukkit main thread. `getQuote(...)` throws `IllegalStateException("yon kapali")` when the requested item direction is closed. DB-backed `getPriceHistory(...)` and `getDailyVolume(...)` may be called from other threads and block the caller until the query completes. `getQuote(...)` and `trade(...)` clamp the maximum trade amount to `motor.max-islem-adet`. `PriceQuoteDto` exposes `itemId`, `amount`, `total`, and `unitAvg`; `TradeResultDto` exposes `success`, `outcome`, and `total`. Money fields are two-decimal `BigDecimal` values. Each history `double[]` has the form `{dayIndex, mid}`; daily volume is the raw BUY+SELL item count.

### Bukkit events

Actual public constructor/getter signatures of the event classes:

```java
// Before a trade is applied; Cancellable.
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
// Only after a successfully completed trade.
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
// If a successful trade actually changed the mid price.
public MarketPriceChangeEvent(String itemId, double oldMid, double newMid);
public String getItemId();
public double getOldMid();
public double getNewMid();
```

`MarketPreTradeEvent` is cancel-only; it does not expose amount or price mutation. If cancelled, no money/item transfer occurs. `MarketTradeEvent` fires after success, and `MarketPriceChangeEvent` fires when a successful trade changes the traded item's mid price.

### PlaceholderAPI

The expansion registers automatically when PlaceholderAPI is installed. Replace `<ITEM_ID>` with a loaded, uppercase item ID such as `IRON_INGOT`:

```text
%mcpiyasa_price_<ITEM_ID>%
%mcpiyasa_buy_<ITEM_ID>%
%mcpiyasa_sell_<ITEM_ID>%
%mcpiyasa_change24h_<ITEM_ID>%
```

Example: `%mcpiyasa_buy_IRON_INGOT%`. Prices use two decimal places; 24-hour change is signed with one decimal place. An unknown placeholder or item returns an empty string. PAPI access is independent of the `kripto-gosterim` setting.

## 10. Runtime contracts and boundaries

- If `/market admin itemekle` or `/market admin fiyat` targets an existing group member, it divides the requested item price by `agirlik` and changes the **group's `taban-fiyat`**; supplied stock is multiplied by the weight and written to the group's `taban-stok`. Every relative in that group is affected. Both commands share a single write path.
- Open market menus are closed when the plugin is disabled (`/stop`, PlugMan, a failed reload rollback); otherwise the named menu icons could stay in a player's inventory once the listener is gone.
- Only single/shift left and right clicks act on menu buttons. Double-click, number keys, off-hand swap, middle-click, and drops are cancelled and ignored, which prevents a double-clicked "Sell All" from running twice.
- Citizens trait registration is a one-way latch for the process lifetime. After enabling `npc-market`, changing `true→false` requires a server restart to remove the registration.
- On a very early bootstrap crash, only console output is guaranteed. Admin join warnings are available after enough runtime wiring succeeds; an online-admin broadcast is not guaranteed for every early failure.
- Successful-trade persistence uses an async queue. Normal shutdown flushes it; an abrupt process/host crash can lose **multiple queued records**. The first storage write failure closes trading through safe mode, while later/queued writes continue to be attempted for recovery.
- PlaceholderAPI price snapshots refresh every 100 ticks, so placeholder prices can be about **5 seconds** stale.
- The sign market supports main-hand right-click interaction. Adventure/spectator sign interaction is outside the supported boundary.

## 11. Build from source

Building from source requires **JDK 17+** and Maven:

```text
mvn package
```

The Java 8-bytecode JAR is produced under `target/`.

## 12. License and Commercial Use

This software is licensed under [**PolyForm Noncommercial 1.0.0**](LICENSE).

- 💻 **Personal, hobby, academic and noncommercial community use:** FREE.
- 🛠️ **Development, bug fixes, contributions:** allowed (fork/PR).
- 🏢 **Commercial / revenue-generating use** (paid servers, companies): a **commercial license is required.**

### Commercial License (one-time payment)

Commercial use is covered by a perpetual commercial license for a one-time fee:

👉 **Contact `lebent` on Discord** — after agreeing on terms you'll receive a
purchase link (Shopier).

After payment you receive a license document granting the right to use the software
in commercial projects without the PolyForm restrictions. The commercial license also
covers bug fixes, updates, and support access. For a plain-language summary of what
is and isn't allowed, see the bottom of the [LICENSE](LICENSE) file.

## 12. FAQ

### Why did the price fall, or why is it not rising?

A sale increases virtual stock and therefore lowers the price; sales of related items in the same group affect that market too. Large/abnormal volume and concentrated selling can amplify the effect. The mid price may also be pinned at an item floor or ceiling. Purchases move it in the opposite direction; continuous recovery does not reset the price, but moves it gradually toward its base. If necessary, inspect the state with `/market admin durum` and return the relevant group to base stock with `/market admin sifirla <item>`.

### Why can a renamed or enchanted item not be sold?

This is deliberate. The market only sees exact vanilla stacks; renamed, enchanted, or damaged items cannot be sold and are not counted in the inventory. The player receives the `islem.degistirilmis-item` message. The rule prevents named GUI icons or custom gear from being converted to cash.

### Can players in creative mode use the market?

By default yes; MCPiyasa does not interfere with creative mode. To disable it, set `ozellikler.creative-ticaret: false` in `config.yml` and run `/market admin reload`; every trade from a creative-mode player is then rejected.

### Is an “item skipped” log normal on an older Minecraft version?

Yes. `items.yml` also contains some items from newer releases. A `Material` unknown to 1.16.5, for example, is skipped and logged on that server; this is not an error, and recognized items continue to work. Typos can enter the same skipped list, so investigate any ID you did not expect.

### How do I set up an NPC market?

Install Citizens, set `ozellikler.npc-market: true`, then reload/start MCPiyasa. Select the NPC you created with Citizens and run:

```text
/trait mcpiyasa
```

Right-clicking that NPC opens the main market GUI.

### What is the market sign format?

First set `ozellikler.tabela-market: true`. Only a user with `mcpiyasa.admin` can create a sign with these first two lines; MCPiyasa fills the third and fourth lines:

```text
[Market]
IRON_INGOT
```

Use requires `mcpiyasa.use`. On right-click, buy/sell prices refresh from live quotes and the item menu opens. MCPiyasa does not bypass a protection plugin that denies block use.

### How do I enable the crypto display?

Set `ozellikler.kripto-gosterim: true` in `config.yml` and run `/market admin reload`. Category/item screens then show a 24-hour percentage and trend arrow, and the main menu exposes the gainers/losers view.

### How do I enable command trading?

Set `ozellikler.komut-ticaret: true` in `config.yml` and run `/market admin reload`. `/market al <item> <amount>` and `/market sat <item> <amount>` then become available.

### Why do gainers/losers show `0.00%` on a fresh server?

This is normal. A previous daily price snapshot is required for a 24-hour comparison. There is no comparison baseline before the first day closes, so change deliberately uses the code's two-decimal `0.00%` format.

### Why does intraday volume reset on restart?

This is intentional. The engine's in-memory intraday volume, seller concentration, and daily calibration EMA seed are not restored on restart; the first new day close seeds the EMA again. Price/group state, weekly hourly profiles, transaction logs, and existing daily snapshots are persistent; the API's raw daily volume calculated from the transaction log is separate.
