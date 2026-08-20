package com.mcpiyasa.access;

import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.trait.Trait;
import org.bukkit.event.EventHandler;

/** Citizens NPC'lerine /trait mcpiyasa ile eklenen market trait'i. */
public final class NpcTrait extends Trait {
    public NpcTrait() {
        super("mcpiyasa");
    }

    @EventHandler(ignoreCancelled = true)
    public void onNpcRightClick(NPCRightClickEvent event) {
        if (event.getNPC() == getNPC()) {
            NpcHook.openMainMenuFor(event.getClicker());
        }
    }
}
