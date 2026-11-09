package ru.revvo.arena;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.black_ixx.playerpoints.PlayerPoints;
import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ArenaPlugin extends JavaPlugin implements Listener, CommandExecutor {

    private static final int SLOT = 13;

    private PlayerPointsAPI points;
    private File dataFile;
    private YamlConfiguration data;

    private static class ArenaHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();

        if (!Bukkit.getPluginManager().isPluginEnabled("PlayerPoints")) {
            getLogger().severe("PlayerPoints не найден! Плагин Arena отключён.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        points = PlayerPoints.getInstance().getAPI();

        dataFile = new File(getDataFolder(), "data.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);

        getCommand("arena").setExecutor(this);
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    private boolean isOwner(UUID id) {
        return data.getBoolean("owners." + id, false);
    }

    private void setOwner(UUID id) {
        data.set("owners." + id, true);
        try {
            data.save(dataFile);
        } catch (IOException e) {
            getLogger().severe("Не удалось сохранить data.yml: " + e.getMessage());
        }
    }

    private Component c(String text) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(text)
                .decoration(TextDecoration.ITALIC, false);
    }

    private String msg(String key, int balance) {
        return getConfig().getString("messages." + key, "")
                .replace("{price}", String.valueOf(getConfig().getInt("price", 129)))
                .replace("{balance}", String.valueOf(balance));
    }

    private void openMenu(Player p) {
        UUID id = p.getUniqueId();
        int price = getConfig().getInt("price", 129);
        int balance = points.look(id);
        boolean owner = isOwner(id);

        Inventory inv = Bukkit.createInventory(new ArenaHolder(), 27,
                c(getConfig().getString("menu-title", "&6&lАрена")));

        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fm = filler.getItemMeta();
        fm.displayName(Component.text(" "));
        filler.setItemMeta(fm);
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, filler);
        }

        ItemStack item = new ItemStack(owner ? Material.ENDER_PEARL : Material.DIAMOND_SWORD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(c(getConfig().getString(owner ? "bought-name" : "not-bought-name", "&aАрена")));
        List<Component> lore = new ArrayList<>();
        for (String line : getConfig().getStringList(owner ? "bought-lore" : "not-bought-lore")) {
            lore.add(c(line.replace("{price}", String.valueOf(price))
                    .replace("{balance}", String.valueOf(balance))));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        inv.setItem(SLOT, item);

        p.openInventory(inv);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player p) {
            openMenu(p);
        } else {
            sender.sendMessage("Только для игроков.");
        }
        return true;
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof ArenaHolder) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof ArenaHolder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        if (e.getRawSlot() != SLOT) return;

        UUID id = p.getUniqueId();

        if (isOwner(id)) {
            p.closeInventory();
            p.sendMessage(c(msg("teleport", points.look(id))));
            p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
            String cmd = getConfig().getString("teleport-command", "warp arena {player}")
                    .replace("{player}", p.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            return;
        }

        int price = getConfig().getInt("price", 129);
        int balance = points.look(id);
        if (balance < price) {
            p.sendMessage(c(msg("not-enough", balance)));
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        if (!points.take(id, price)) {
            p.sendMessage(c(msg("error", balance)));
            return;
        }
        setOwner(id);
        p.sendMessage(c(msg("bought", balance - price)));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        openMenu(p);
    }
}
