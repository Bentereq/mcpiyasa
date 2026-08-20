package com.mcpiyasa.gui;

import com.mcpiyasa.config.Messages;
import com.mcpiyasa.config.ParsedItems;
import com.mcpiyasa.config.PluginSettings;
import com.mcpiyasa.diag.SafeMode;
import com.mcpiyasa.market.MarketService;

/**
 * Admin menulerine her zaman CANLI (reload sonrasi yenilenmis) veri ve eylem
 * saglayan kararli kaynak. Eklenti (MCPiyasaPlugin) uygular; GuiListener reload
 * ile degistirilse de bu kaynak sabit kalir, boylece admin menuleri fiyat
 * yaziminin ardindan bayat parsedItems okumaz.
 */
public interface AdminGuiHost {
    ParsedItems items();

    PluginSettings settings();

    Messages messages();

    MarketService marketService();

    SafeMode safeMode();

    AdminGuiActions actions();
}
