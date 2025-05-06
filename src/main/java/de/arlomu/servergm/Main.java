package de.arlomu.servergm;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public final class Main extends JavaPlugin implements CommandExecutor, TabCompleter, Listener {

    private String prefix, msgSelf, msgOther, msgPlayerNotFound, msgInvalidGamemode,
            msgNoPermission, msgMustSpecifyPlayer, msgCommandDisabled, msgReloadSuccess, msgReloadNoPermission;

    private final Map<String, Boolean> enabledCommands = new HashMap<>();
    private boolean f3f4SwitcherEnabled;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfig();
        getServer().getPluginManager().registerEvents(this, this);

        String[] cmds = {"gm", "gmc", "gms", "gma", "gmsp", "gmreload", "gamemode", "crea", "surv", "adven", "spec"};
        for (String cmd : cmds) {
            PluginCommand command = getCommand(cmd);
            if (command != null) {
                command.setExecutor(this);
                command.setTabCompleter(this);
            }
        }
    }

    private void loadConfig() {
        prefix = color(getConfig().getString("prefix", ""));
        msgSelf = color(getConfig().getString("messages.self"));
        msgOther = color(getConfig().getString("messages.other"));
        msgPlayerNotFound = color(getConfig().getString("messages.player_not_found"));
        msgInvalidGamemode = color(getConfig().getString("messages.invalid_gamemode"));
        msgNoPermission = color(getConfig().getString("messages.no_permission"));
        msgMustSpecifyPlayer = color(getConfig().getString("messages.must_specify_player"));
        msgCommandDisabled = color(getConfig().getString("messages.command_disabled"));
        msgReloadSuccess = color(getConfig().getString("messages.reload_success"));
        msgReloadNoPermission = color(getConfig().getString("messages.reload_no_permission"));

        enabledCommands.clear();
        for (String cmd : getConfig().getConfigurationSection("enabled_commands").getKeys(false)) {
            enabledCommands.put(cmd, getConfig().getBoolean("enabled_commands." + cmd, true));
        }

        f3f4SwitcherEnabled = getConfig().getBoolean("f3_f4_switcher_enabled", true);
    }

    private String color(String input) {
        return ChatColor.translateAlternateColorCodes('&', input);
    }

    private boolean isEnabled(String command) {
        return enabledCommands.getOrDefault(command, false);
    }

    private void send(CommandSender sender, String message) {
        sender.sendMessage(prefix + message);
    }

    private GameMode parseGamemode(String arg) {
        return switch (arg.toLowerCase()) {
            case "0", "s", "survival" -> GameMode.SURVIVAL;
            case "1", "c", "creative" -> GameMode.CREATIVE;
            case "2", "a", "adventure" -> GameMode.ADVENTURE;
            case "3", "sp", "spectator" -> GameMode.SPECTATOR;
            default -> null;
        };
    }

    private void setGamemode(CommandSender sender, GameMode mode, Player target, boolean isSelf, String command) {
        String perm = "servergm." + command + "." + (isSelf ? "self" : "other");
        if (!sender.hasPermission(perm) && !sender.hasPermission("servergm.all")) {
            send(sender, msgNoPermission);
            return;
        }

        target.setGameMode(mode);
        String modeName = mode.name().toLowerCase();
        if (isSelf) {
            send(sender, msgSelf.replace("%gamemode%", modeName));
        } else {
            send(sender, msgOther.replace("%spieler%", target.getName()).replace("%gamemode%", modeName));
            send(target, msgSelf.replace("%gamemode%", modeName));
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String command = cmd.getName().toLowerCase();

        if (command.equals("gmreload")) {
            if (!sender.hasPermission("servergm.reload")) {
                send(sender, msgReloadNoPermission);
                return true;
            }
            reloadConfig();
            loadConfig();
            send(sender, msgReloadSuccess);
            return true;
        }

        if (!isEnabled(command)) {
            send(sender, msgCommandDisabled);
            return true;
        }

        Player player = sender instanceof Player ? (Player) sender : null;
        Player target = player;
        boolean isSelf = true;
        GameMode mode;

        if (command.equals("gm") || command.equals("gamemode") || command.equals("gammemode")) {
            if (args.length < 1) {
                send(sender, msgInvalidGamemode);
                return true;
            }
            mode = parseGamemode(args[0]);
            if (mode == null) {
                send(sender, msgInvalidGamemode);
                return true;
            }
            if (args.length >= 2) {
                target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    send(sender, msgPlayerNotFound);
                    return true;
                }
                isSelf = sender instanceof Player && ((Player) sender).equals(target);
            } else if (!(sender instanceof Player)) {
                send(sender, msgMustSpecifyPlayer);
                return true;
            }
        } else {
            mode = switch (command) {
                case "gmc", "crea" -> GameMode.CREATIVE;
                case "gms", "surv" -> GameMode.SURVIVAL;
                case "gma", "adven" -> GameMode.ADVENTURE;
                case "gmsp", "spec" -> GameMode.SPECTATOR;
                default -> null;
            };

            if (args.length >= 1) {
                target = Bukkit.getPlayer(args[0]);
                if (target == null) {
                    send(sender, msgPlayerNotFound);
                    return true;
                }
                isSelf = sender instanceof Player && ((Player) sender).equals(target);
            }
        }

        setGamemode(sender, mode, target, isSelf, command);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        String command = cmd.getName().toLowerCase();
        if (!isEnabled(command)) return Collections.emptyList();

        if (command.equals("gm") || command.equals("gamemode") || command.equals("gammemode")) {
            if (args.length == 1) return Arrays.asList("0", "1", "2", "3", "s", "c", "a", "sp");
            if (args.length == 2) return null;
        } else if (args.length == 1) return null;

        return Collections.emptyList();
    }

    @EventHandler
    public void onGamemodeChange(PlayerGameModeChangeEvent event) {
        if (!f3f4SwitcherEnabled) return;

        Player player = event.getPlayer();
        GameMode newMode = event.getNewGameMode();

        event.setCancelled(true);
        player.performCommand("gm " + newMode.ordinal());
    }
}
