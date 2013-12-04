package net.darthcraft.dcmod.addons;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.pravian.bukkitlib.util.ChatUtils;
import net.darthcraft.dcmod.DarthCraft;
import net.darthcraft.dcmod.addons.PlayerManager.PlayerInfo;
import net.darthcraft.dcmod.commands.Permissions.Permission;
import net.darthcraft.dcmod.commands.Permissions.PermissionUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class ChatFilter extends DarthCraftAddon {

    private final Map<String, Integer> warnings;
    //
    private boolean antiSwearEnabled;
    private final List<String> swearwords;
    //
    private boolean antiCapsEnabled;
    private int maxCaps;
    //
    private boolean replacementsEnabled;
    private final List<String> replacements;

    public ChatFilter(DarthCraft plugin) {
        super(plugin);
        this.warnings = new HashMap<String, Integer>();
        this.swearwords = new ArrayList<String>();
        this.replacements = new ArrayList<String>();
    }

    public void loadSettings() {

        // AntiSwear
        this.antiSwearEnabled = plugin.mainConfig.getBoolean("chat.antiswear.enabled");
        for (String swear : plugin.mainConfig.getStringList("chat.antiswear.swearwords")) {
            this.swearwords.add(swear.toLowerCase().trim());
        }

        // AntiCaps
        this.antiCapsEnabled = plugin.mainConfig.getBoolean("chat.anticaps.enabled");
        this.maxCaps = plugin.mainConfig.getInt("chat.anticaps.maxcaps");

        // Replacements
        this.replacementsEnabled = plugin.mainConfig.getBoolean("chat.replacements.enabled");
        for (String replacement : plugin.mainConfig.getStringList("chat.replacements.phrases")) {
            if (replacement.contains(";")) {
                replacements.add(replacement);
            } else {
                plugin.logger.warning("Chat: Replacement " + replacement + " isn't valid!");
            }
        }

    }

    // Events
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        final Player player = event.getPlayer();
        final PlayerInfo info = plugin.playerManager.getInfo(player);

        if (info.isMuted()) {
            event.setCancelled(true);
            logger.info(player.getName() + " tried to speak, but is muted");
            util.sendMessage(player, ChatColor.RED + "You try to open your mouth but words won't come out.");
            util.sendMessage(player, ChatColor.RED + "(You're muted)");
            return;
        }

        String message = " " + event.getMessage().trim();
        String pMessage = message;
        pMessage = pMessage.replace('*', 'u');
        pMessage = pMessage.replace('@', 'a');
        pMessage = pMessage.replace('!', 'i');
        pMessage = pMessage.replace('0', 'o');
        pMessage = pMessage.replace('$', 's');
        pMessage = pMessage.replace(" I ", " i ");

        // Prevent swearing where applicable
        if (antiSwearEnabled && !PermissionUtils.hasPermission(player, Permission.HOST)) {

            for (String swear : swearwords) {
                if (pMessage.toLowerCase().contains(" " + swear)) {
                    player.sendMessage(ChatColor.RED + "Keep the chat polite!");
                    warn(player, "Using bad language");
                    player.sendMessage(ChatColor.RED + "Warning: You'll be kicked every third warning!");
                    event.setCancelled(true);
                    return;
                }
            }
        }

        // De-caps, de-exclamationmark and de-questionmark 
        if (antiCapsEnabled && !PermissionUtils.hasPermission(player, Permission.HEADADMIN)) {

            int caps = 0;
            int excl = 0;
            int ques = 0;

            for (int i = 1; i < pMessage.length(); i++) { // Ignore first character
                try {
                    if (Character.isUpperCase(pMessage.charAt(i))) {
                        caps++;
                    }
                    if (message.charAt(i) == '!') {
                        excl++;
                    }
                    if (message.charAt(i) == '?') {
                        ques++;
                    }
                } catch (IndexOutOfBoundsException e) {
                }
            }

            if (caps > maxCaps) {
                message = StringUtils.capitalize(message.toLowerCase());
                message = message.replace(" i ", " I ");
            }

            if (excl > 2) {
                message = message.replace("!", "") + "!";
            }

            if (ques > 2) {
                message = message.replace("?", "") + "?";
            }
        }

        // Replacements
        if (replacementsEnabled) {
            for (String replacement : replacements) {
                if (!message.contains(replacement.split(";")[0])) {
                    continue;
                }

                final StringBuilder newMessage = new StringBuilder();
                String[] messageParts = message.split(replacement.split(";")[0]);

                newMessage.append(messageParts[0]);
                final String color = util.getColor(player).toString();

                messageParts = (String[]) ArrayUtils.subarray(messageParts, 1, messageParts.length);

                for (String part : messageParts) {
                    newMessage.append(ChatUtils.colorize(replacement.split(";")[1]));
                    newMessage.append(color);
                    newMessage.append(part);
                }
                message = newMessage.toString();
            }
        }

        event.setMessage(message.trim());
    }

    public void warn(Player player, String reason) {

        final String name = player.getName().trim();

        if (warnings.containsKey(name)) {
            warnings.put(name, warnings.get(name) + 1);
            player.sendMessage(ChatColor.RED + "You have been given a warning! (" + warnings.get(name) + ")");
        } else {
            warnings.put(name, 1);
            player.sendMessage(ChatColor.RED + "You have been given a warning! (1)");
        }

        if (warnings.get(name) % 3 == 0) {
            player.kickPlayer(ChatColor.RED + "Kicked: " + reason);
            if (!PermissionUtils.hasPermission(player, Permission.ADMIN)) {
                plugin.getServer().broadcastMessage(ChatColor.RED + name + " has been kicked from the game for " + reason);
            }
        }

    }
}