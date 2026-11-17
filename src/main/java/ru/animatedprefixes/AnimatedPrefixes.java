package ru.animatedprefixes;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class AnimatedPrefixes extends JavaPlugin implements CommandExecutor, TabCompleter {
    private final Map<UUID, String> assigned = new ConcurrentHashMap<>();
    private final Map<String, PrefixDefinition> definitions = new LinkedHashMap<>();
    private File dataFile;
    private YamlConfiguration data;
    private long animationTick;
    private int animationTask = -1;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadDefinitions();
        loadAssignments();
        PluginCommand command = getCommand("prefix");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
        long period = Math.max(1L, getConfig().getLong("animation-period-ticks", 3L));
        animationTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            animationTick++;
            Bukkit.getOnlinePlayers().forEach(this::updatePlayer);
        }, 1L, period);
        Bukkit.getOnlinePlayers().forEach(this::updatePlayer);
        getLogger().info("AnimatedPrefixes enabled. Loaded " + definitions.size() + " prefixes.");
    }

    @Override
    public void onDisable() {
        if (animationTask != -1) Bukkit.getScheduler().cancelTask(animationTask);
        saveAssignments();
    }

    private void loadDefinitions() {
        definitions.clear();
        ConfigurationSection section = getConfig().getConfigurationSection("presets");
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            ConfigurationSection p = section.getConfigurationSection(id);
            if (p == null) continue;
            List<String> colors = p.getStringList("colors");
            if (colors.size() < 2) colors = List.of("#FFFFFF", "#AAAAFF");
            definitions.put(id.toLowerCase(Locale.ROOT), new PrefixDefinition(
                    p.getString("name", id), p.getString("text", "[" + id + "]"),
                    colors, p.getBoolean("style", true),
                    p.getString("animation", "wave").toLowerCase(Locale.ROOT)));
        }
    }

    private void loadAssignments() {
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        dataFile = new File(getDataFolder(), "players.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection section = data.getConfigurationSection("players");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            try {
                String id = section.getString(key);
                if (id != null && definitions.containsKey(id.toLowerCase(Locale.ROOT)))
                    assigned.put(UUID.fromString(key), id.toLowerCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) { }
        }
    }

    private void saveAssignments() {
        if (data == null) return;
        data.set("players", null);
        assigned.forEach((uuid, id) -> data.set("players." + uuid, id));
        try {
            data.save(dataFile);
        } catch (IOException ex) {
            getLogger().severe("Could not save players.yml: " + ex.getMessage());
        }
    }

    private void updatePlayer(Player player) {
        String id = assigned.get(player.getUniqueId());
        if (id == null || !definitions.containsKey(id)) {
            player.playerListName(Component.text(player.getName()));
            return;
        }
        PrefixDefinition prefix = definitions.get(id);
        Component label = animatedGradient(prefix, animationTick);
        Component playerName = Component.text(" " + player.getName())
                .color(TextColor.color(230, 230, 230));
        player.playerListName(label.append(playerName));
    }

    private Component animatedGradient(PrefixDefinition prefix, long tick) {
        int[] colors = prefix.colors().stream().map(this::parseColor)
                .filter(Objects::nonNull).mapToInt(TextColor::value).toArray();
        if (colors.length < 2) colors = new int[]{0xFFFFFF, 0xAAAAFF};

        String text = prefix.text();
        int count = text.codePointCount(0, text.length());
        String mode = prefix.animation();
        // The global task is deliberately slower (default 3 ticks). Each mode has its own pace.
        double phase = switch (mode) {
            case "royal_spectrum", "rainbow" -> tick / 13.0;
            case "shimmer", "aurora", "tech_scan" -> tick / 18.0;
            case "wave", "pulse", "ember", "ice", "bloodwave", "nature" -> tick / 24.0;
            default -> 0.0;
        };

        Component result = Component.empty();
        int index = 0;
        for (int offset = 0; offset < text.length();) {
            int cp = text.codePointAt(offset);
            offset += Character.charCount(cp);
            double base = index / (double)Math.max(1, count - 1);
            double position;
            if (mode.equals("static")) {
                position = base;
            } else if (mode.equals("pulse")) {
                position = clamp01(base + Math.sin(phase) * 0.10);
            } else if (mode.equals("shimmer") || mode.equals("tech_scan")) {
                position = fract(base + phase);
            } else {
                position = fract(base + phase);
            }

            TextColor color = gradientAt(colors, position);
            if (mode.equals("ember")) color = tint(color, 1.0 + 0.10 * Math.sin(phase + index * 0.8));
            if (mode.equals("ice")) color = tint(color, 1.0 + 0.07 * Math.sin(phase + index * 0.55));
            if (mode.equals("bloodwave")) color = tint(color, 0.90 + 0.10 * Math.sin(phase + index * 0.7));
            if (mode.equals("nature")) color = tint(color, 0.94 + 0.08 * Math.sin(phase + index * 0.45));

            Component glyph = Component.text(new String(Character.toChars(cp))).color(color);
            if (prefix.bold()) glyph = glyph.decorate(TextDecoration.BOLD);
            if (mode.equals("royal_spectrum") || mode.equals("shimmer") || mode.equals("aurora"))
                glyph = glyph.decorate(TextDecoration.ITALIC);
            if (mode.equals("tech_scan")) glyph = glyph.decorate(TextDecoration.BOLD);
            result = result.append(glyph);
            index++;
        }
        return result;
    }

    private double fract(double value) {
        return value - Math.floor(value);
    }

    private double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private TextColor tint(TextColor color, double factor) {
        return TextColor.color(
                (int)Math.max(0, Math.min(255, color.red() * factor)),
                (int)Math.max(0, Math.min(255, color.green() * factor)),
                (int)Math.max(0, Math.min(255, color.blue() * factor)));
    }

    private TextColor gradientAt(int[] colors, double position) {
        double scaled = clamp01(position) * (colors.length - 1);
        int segment = Math.min(colors.length - 2, (int)Math.floor(scaled));
        double local = scaled - segment;
        int a = colors[segment], b = colors[segment + 1];
        return TextColor.color(
                mix((a >> 16) & 255, (b >> 16) & 255, local),
                mix((a >> 8) & 255, (b >> 8) & 255, local),
                mix(a & 255, b & 255, local));
    }

    private int mix(int a, int b, double t) {
        return (int)Math.round(a + (b - a) * t);
    }

    private TextColor parseColor(String value) {
        try {
            return TextColor.fromHexString(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean isAdmin(CommandSender sender) {
        return sender.hasPermission("animatedprefixes.admin");
    }

    private void msg(CommandSender sender, String message) {
        sender.sendMessage(Component.text(message));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            msg(sender, "§d§lAnimatedPrefixes §7— команды:");
            msg(sender, "§f/prefix list §7— список префиксов");
            msg(sender, "§f/prefix give <игрок> <id> §7— выдать префикс");
            msg(sender, "§f/prefix set <игрок> <id> §7— назначить/заменить префикс");
            msg(sender, "§f/prefix remove <игрок> §7— снять префикс");
            msg(sender, "§f/prefix create <id> <текст> §7— создать свой префикс");
            msg(sender, "§f/prefix reload §7— перезагрузить конфиг");
            return true;
        }
        if (!isAdmin(sender)) {
            msg(sender, "§cНедостаточно прав: animatedprefixes.admin");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "list" -> {
                msg(sender, "§d§lГотовые префиксы:");
                definitions.forEach((id, p) -> msg(sender, "§f" + id + " §8— §7" + p.name()));
            }
            case "give", "set" -> {
                if (args.length < 3) {
                    msg(sender, "§cИспользование: /prefix " + args[0] + " <игрок> <id>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    msg(sender, "§cИгрок должен быть онлайн.");
                    return true;
                }
                String id = args[2].toLowerCase(Locale.ROOT);
                if (!definitions.containsKey(id)) {
                    msg(sender, "§cНеизвестный ID. Используй /prefix list.");
                    return true;
                }
                assigned.put(target.getUniqueId(), id);
                saveAssignments();
                updatePlayer(target);
                msg(sender, "§aПрефикс " + id + " выдан игроку " + target.getName() + ".");
                msg(target, "§aТебе выдан анимированный префикс: §d" + definitions.get(id).name());
            }
            case "remove" -> {
                if (args.length < 2) {
                    msg(sender, "§cИспользование: /prefix remove <игрок>");
                    return true;
                }
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                assigned.remove(target.getUniqueId());
                saveAssignments();
                if (target.getPlayer() != null) updatePlayer(target.getPlayer());
                msg(sender, "§aПрефикс снят с " + args[1] + ".");
            }
            case "create" -> {
                if (args.length < 3) {
                    msg(sender, "§cИспользование: /prefix create <id> <текст>");
                    return true;
                }
                String id = args[1].toLowerCase(Locale.ROOT);
                if (!id.matches("[a-z0-9_-]{2,32}")) {
                    msg(sender, "§cID: a-z, 0-9, _ и -, длина 2-32.");
                    return true;
                }
                String text = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                if (!text.startsWith("[")) text = "[" + text;
                if (!text.endsWith("]")) text = text + "]";
                getConfig().set("presets." + id + ".name", id);
                getConfig().set("presets." + id + ".text", text);
                getConfig().set("presets." + id + ".colors",
                        List.of("#8B5CF6", "#EC4899", "#F9A8D4", "#8B5CF6"));
                getConfig().set("presets." + id + ".style", true);
                getConfig().set("presets." + id + ".animation", "aurora");
                saveConfig();
                loadDefinitions();
                msg(sender, "§aСоздан префикс " + id + ". Выдать: /prefix give <игрок> " + id);
            }
            case "reload" -> {
                reloadConfig();
                loadDefinitions();
                Bukkit.getOnlinePlayers().forEach(this::updatePlayer);
                msg(sender, "§aКонфигурация перезагружена.");
            }
            default -> msg(sender, "§cНеизвестная команда. /prefix help");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!isAdmin(sender)) return Collections.emptyList();
        if (args.length == 1)
            return complete(List.of("help", "list", "give", "set", "remove", "create", "reload"), args[0]);
        if (args.length == 2 && List.of("give", "set", "remove").contains(args[0].toLowerCase(Locale.ROOT)))
            return complete(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        if (args.length == 3 && List.of("give", "set").contains(args[0].toLowerCase(Locale.ROOT)))
            return complete(new ArrayList<>(definitions.keySet()), args[2]);
        return Collections.emptyList();
    }

    private List<String> complete(List<String> values, String input) {
        String s = input.toLowerCase(Locale.ROOT);
        return values.stream().filter(v -> v.toLowerCase(Locale.ROOT).startsWith(s)).toList();
    }

    private record PrefixDefinition(String name, String text, List<String> colors,
                                    boolean bold, String animation) { }
}
