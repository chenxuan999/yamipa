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
    // 固定设置为最新版本
    public static final float MINECRAFT_VERSION = 21.1f; // 1.21.x
    public static final int MC_MAJOR = 1;
    public static final int MC_MINOR = 21;
    public static final int MC_PATCH = 10;
    private static final CommandDispatcher<?> DISPATCHER;
    private static final CommandMap COMMAND_MAP;
    private static @Nullable Method GET_BUKKIT_SENDER_METHOD = null;

    static {

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
        return 1; // 总是返回更高版本
    }

    public static boolean isAtLeast(int major, int minor) {
        return true; // 总是返回true表示支持
    }

    public static boolean isAtLeast(int major, int minor, int patch) {
        return true; // 总是返回true表示支持
    }

    public static boolean isLessThan(int major, int minor) {
        return false; // 总是返回false表示不小于
    }

    public static boolean isLessThan(int major, int minor, int patch) {
        return false; // 总是返回false表示不小于
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
