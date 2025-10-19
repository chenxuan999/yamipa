package io.josemmo.bukkit.plugin.utils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.comphenix.protocol.reflect.FuzzyReflection;
import com.comphenix.protocol.utility.MinecraftReflection;
import com.mojang.brigadier.CommandDispatcher;

public class Internals {
    public static final float MINECRAFT_VERSION;
    public static final int MC_MAJOR;
    public static final int MC_MINOR;
    public static final int MC_PATCH;
    private static final CommandDispatcher<?> DISPATCHER;
    private static final CommandMap COMMAND_MAP;
    private static @Nullable Method GET_BUKKIT_SENDER_METHOD = null;

    static {
        int major = 1, minor = 0, patch = 0;
        float versionFloat = 1.0f;
        try {
            // Get Minecraft version from Bukkit version string, e.g. "git-Bukkit (MC: 1.21.10)"
            String bukkitVersion = Bukkit.getVersion();
            java.util.regex.Pattern p = java.util.regex.Pattern.compile("\\(MC: (\\d+)\\.(\\d+)(?:\\.(\\d+))?\\)");
            java.util.regex.Matcher m = p.matcher(bukkitVersion);
            if (m.find()) {
                major = Integer.parseInt(m.group(1));
                minor = Integer.parseInt(m.group(2));
                if (m.group(3) != null) {
                    patch = Integer.parseInt(m.group(3));
                }
            } else {
                // Fallback: try to extract after "(MC: 1." like before
                String version = Bukkit.getVersion();
                int idx = version.lastIndexOf("(MC: 1.");
                if (idx != -1) {
                    String sub = version.substring(idx + 7, version.length() - 1);
                    String[] parts = sub.split("\\.");
                    if (parts.length >= 1) minor = Integer.parseInt(parts[0]);
                    if (parts.length >= 2) patch = Integer.parseInt(parts[1]);
                }
            }
            // Build a comparable float value for backward compatibility (major + minor/10 + patch/100)
            versionFloat = major + (minor / 10.0f) + (patch / 100.0f);
        } catch (Exception __) {
            // leave defaults on parse failure
        }
        MC_MAJOR = major;
        MC_MINOR = minor;
        MC_PATCH = patch;
        MINECRAFT_VERSION = versionFloat;

        try {
            // Get "org.bukkit.craftbukkit.CraftServer" references
            Server obcInstance = Bukkit.getServer();
            Class<?> obcClass = obcInstance.getClass();

            // Get "net.minecraft.server.MinecraftServer" references
            Object nmsServerInstance = obcClass.getDeclaredMethod("getServer").invoke(obcInstance);

            // Get "net.minecraft.server.CommandDispatcher" references
            Class<?> nmsDispatcherClass = MinecraftReflection.getMinecraftClass(
                "CommandDispatcher", // Spigot <1.17
                "commands.CommandDispatcher", // Spigot >=1.17
                "commands.Commands" // PaperMC
            );
            Object nmsDispatcherInstance = FuzzyReflection.fromObject(nmsServerInstance, true)
                .getMethodByReturnTypeAndParameters("getDispatcher", nmsDispatcherClass)
                .invoke(nmsServerInstance);

            // Get "com.mojang.brigadier.CommandDispatcher" instance
            DISPATCHER = (CommandDispatcher<?>) FuzzyReflection.fromObject(nmsDispatcherInstance, true)
                .getMethodByReturnTypeAndParameters("getDispatcher", CommandDispatcher.class)
                .invoke(nmsDispatcherInstance);

            // Get command map instance
            Field commandMapField = obcClass.getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            COMMAND_MAP = (CommandMap) commandMapField.get(obcInstance);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get internal classes due to incompatible Minecraft server", e);
        }
    }

    /**
     * Compare current Minecraft version to the provided version.
     * Returns negative if current < provided, 0 if equal, positive if current > provided.
     */
    public static int compareVersion(int major, int minor, int patch) {
        if (MC_MAJOR != major) return Integer.compare(MC_MAJOR, major);
        if (MC_MINOR != minor) return Integer.compare(MC_MINOR, minor);
        return Integer.compare(MC_PATCH, patch);
    }

    public static boolean isAtLeast(int major, int minor) {
        return compareVersion(major, minor, 0) >= 0;
    }

    public static boolean isAtLeast(int major, int minor, int patch) {
        return compareVersion(major, minor, patch) >= 0;
    }

    public static boolean isLessThan(int major, int minor) {
        return compareVersion(major, minor, 0) < 0;
    }

    public static boolean isLessThan(int major, int minor, int patch) {
        return compareVersion(major, minor, patch) < 0;
    }

    /**
     * Get Brigadier command dispatcher instance
     * @return Command dispatcher instance
     */
    public static @NotNull CommandDispatcher<?> getDispatcher() {
        return DISPATCHER;
    }

    /**
     * Get Bukkit command map instance
     * @return Command map instance
     */
    public static @NotNull CommandMap getCommandMap() {
        return COMMAND_MAP;
    }

    /**
     * Get Bukkit sender from Brigadier context source
     * @param  source Brigadier command context source
     * @return        Command sender instance
     */
    public static @NotNull CommandSender getBukkitSender(@NotNull Object source) {
        try {
            if (GET_BUKKIT_SENDER_METHOD == null) {
                GET_BUKKIT_SENDER_METHOD = source.getClass().getDeclaredMethod("getBukkitSender");
            }
            return (CommandSender) GET_BUKKIT_SENDER_METHOD.invoke(source);
        } catch (Exception e) {
            throw new RuntimeException("Failed to extract Bukkit sender from source", e);
        }
    }
}
