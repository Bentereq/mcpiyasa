package com.mcpiyasa.market;

import com.mcpiyasa.compat.Materials;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Bukkit oyuncu envanterini atomik islem koprusune uyarlar. */
public final class BukkitInventory implements InventoryBridge {
    private final Logger logger;

    public BukkitInventory(Logger logger) {
        if (logger == null) {
            throw new IllegalArgumentException("logger null olamaz");
        }
        this.logger = logger;
    }

    @Override
    public int count(UUID player, String itemId) {
        PlayerInventory inventory = inventory(player);
        Material material = Materials.resolve(itemId);
        return count(inventory, material);
    }

    /** GUI ve bridge'in ayni yalnizca-duz-item sayim semantigini kullanir. */
    public static int count(PlayerInventory inventory, Material material) {
        if (inventory == null || material == null) {
            return 0;
        }
        ItemStack[] contents = inventory.getStorageContents();
        if (contents == null) {
            return 0;
        }

        ItemStack probe = new ItemStack(material);
        int total = 0;
        for (ItemStack stack : contents) {
            if (stack != null && probe.isSimilar(stack)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    @Override
    public boolean hasModifiedStack(UUID player, String itemId) {
        return hasModifiedStack(
            inventory(player), Materials.resolve(itemId));
    }

    /**
     * Ayni materyalden olup probe ile {@code isSimilar} olmayan bir yigin
     * arar. Sayim ve odeme bu yiginlari gormez; oyuncuya nedenini
     * soyleyebilmek icin ayri bir sorgudur.
     */
    public static boolean hasModifiedStack(PlayerInventory inventory,
                                           Material material) {
        if (inventory == null || material == null) {
            return false;
        }
        ItemStack[] contents = inventory.getStorageContents();
        if (contents == null) {
            return false;
        }

        ItemStack probe = new ItemStack(material);
        for (ItemStack stack : contents) {
            if (stack != null && stack.getType() == material
                    && !probe.isSimilar(stack)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean remove(UUID player, String itemId, int amount) {
        PlayerInventory inventory = inventory(player);
        Material material = Materials.resolve(itemId);
        if (inventory == null || material == null || amount <= 0
                || count(inventory, material) < amount) {
            return false;
        }

        Map<Integer, ItemStack> leftovers = inventory.removeItem(
            new ItemStack(material, amount));
        if (leftovers.isEmpty()) {
            return true;
        }

        int removed = amount - totalAmount(leftovers);
        if (removed > 0) {
            try {
                if (!addStacks(inventory, new ItemStack(material), removed,
                        player, itemId)) {
                    logCompensationFailure(
                        player, itemId, removed, "remove-rollback", null);
                }
            } catch (RuntimeException | LinkageError failure) {
                logCompensationFailure(
                    player, itemId, removed, "remove-rollback", failure);
            }
        }
        return false;
    }

    @Override
    public int freeCapacity(UUID player, String itemId) {
        PlayerInventory inventory = inventory(player);
        Material material = Materials.resolve(itemId);
        if (inventory == null || material == null) {
            return 0;
        }

        return freeCapacity(inventory, material);
    }

    static int freeCapacity(PlayerInventory inventory, Material material) {
        if (inventory == null || material == null) {
            return 0;
        }

        ItemStack probe = new ItemStack(material);
        int maxStackSize = probe.getMaxStackSize();
        int capacity = 0;
        ItemStack[] contents = inventory.getStorageContents();
        if (contents == null) {
            return 0;
        }
        for (ItemStack stack : contents) {
            if (stack == null || stack.getType() == Material.AIR) {
                capacity += maxStackSize;
            } else if (probe.isSimilar(stack)) {
                capacity += Math.max(0, maxStackSize - stack.getAmount());
            }
        }
        return capacity;
    }

    @Override
    public boolean add(UUID player, String itemId, int amount) {
        PlayerInventory inventory = inventory(player);
        Material material = Materials.resolve(itemId);
        if (inventory == null || material == null || amount <= 0) {
            return false;
        }
        return addStacks(
            inventory, new ItemStack(material), amount, player, itemId);
    }

    boolean addStacks(PlayerInventory inventory, ItemStack probe, int amount,
                      UUID player, String itemId) {
        if (inventory == null || probe == null || amount <= 0) {
            return false;
        }
        int maxStackSize = probe.getMaxStackSize();
        if (maxStackSize <= 0) {
            throw new IllegalStateException("Item maxStackSize pozitif olmali");
        }

        int added = 0;
        try {
            int remaining = amount;
            while (remaining > 0) {
                int chunk = Math.min(maxStackSize, remaining);
                ItemStack stack = probe.clone();
                stack.setAmount(chunk);
                Map<Integer, ItemStack> leftovers = inventory.addItem(stack);
                int chunkAdded = chunk - totalAmount(leftovers);
                added += Math.max(0, chunkAdded);
                if (!leftovers.isEmpty()) {
                    rollbackAdded(
                        inventory, probe, added, player, itemId,
                        "add-rollback", null);
                    return false;
                }
                remaining -= chunk;
            }
            return true;
        } catch (RuntimeException | LinkageError failure) {
            if (added > 0) {
                rollbackAdded(
                    inventory, probe, added, player, itemId,
                    "add-exception-rollback", failure);
            }
            throw failure;
        }
    }

    private boolean rollbackAdded(PlayerInventory inventory,
                                  ItemStack probe,
                                  int amount,
                                  UUID player,
                                  String itemId,
                                  String phase,
                                  Throwable originalFailure) {
        try {
            if (removeAdded(inventory, probe, amount)) {
                return true;
            }
            logCompensationFailure(
                player, itemId, amount, phase, null);
        } catch (RuntimeException | LinkageError rollbackFailure) {
            if (originalFailure != null) {
                originalFailure.addSuppressed(rollbackFailure);
            }
            logCompensationFailure(
                player, itemId, amount, phase, rollbackFailure);
        }
        return false;
    }

    private static boolean removeAdded(PlayerInventory inventory,
                                       ItemStack probe,
                                       int amount) {
        int remaining = amount;
        int maxStackSize = probe.getMaxStackSize();
        while (remaining > 0) {
            int chunk = Math.min(maxStackSize, remaining);
            ItemStack stack = probe.clone();
            stack.setAmount(chunk);
            Map<Integer, ItemStack> leftovers = inventory.removeItem(stack);
            if (!leftovers.isEmpty()) {
                return false;
            }
            remaining -= chunk;
        }
        return true;
    }

    private static PlayerInventory inventory(UUID playerId) {
        Player player = playerId == null ? null : Bukkit.getPlayer(playerId);
        return player == null ? null : player.getInventory();
    }

    private static int totalAmount(Map<Integer, ItemStack> stacks) {
        int total = 0;
        if (stacks == null) {
            return total;
        }
        for (ItemStack stack : stacks.values()) {
            if (stack != null) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    private void logCompensationFailure(UUID player,
                                        String itemId,
                                        int amount,
                                        String phase,
                                        Throwable failure) {
        String message = "ENVANTER TELAFISI BASARISIZ"
            + " player=" + player
            + " itemId=" + itemId
            + " amount=" + amount
            + " phase=" + phase;
        if (failure == null) {
            logger.severe(message);
        } else {
            logger.log(Level.SEVERE, message, failure);
        }
    }
}
