package io.josemmo.bukkit.plugin.packets;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.injector.StructureCache;
import com.comphenix.protocol.reflect.ExactReflection;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.utility.MinecraftReflection;
import io.josemmo.bukkit.plugin.utils.Internals;
import io.josemmo.bukkit.plugin.utils.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.lang.reflect.Constructor;
import java.lang.reflect.ParameterizedType;
import java.util.Optional;

public class MapDataPacket extends PacketContainer {
    private static final Logger LOGGER = Logger.getLogger("MapDataPacket");
    private static final @Nullable Constructor<?> MAP_ID_CONSTRUCTOR;
    private @Nullable StructureModifier<?> mapDataModifier;
    private static final boolean IS_MODERN_VERSION = true; // 1.21.x 使用新的数据包结构

    static {
        // 对于 1.21.x，使用新的 MapId 类
        Constructor<?> constructor = null;
        try {
            Class<?> mapIdClass = MinecraftReflection.getNullableNMS("world.level.saveddata.maps.MapId");
            if (mapIdClass != null) {
                constructor = ExactReflection.fromClass(mapIdClass, true).findConstructor(int.class);
            }
        } catch (Exception e) {
            LOGGER.warning("Failed to get MapId constructor, will use legacy mode", e);
        }
        MAP_ID_CONSTRUCTOR = constructor;
    }

    public MapDataPacket() {
        super(PacketType.Play.Server.MAP);
        getModifier().writeDefaults();

        try {
            Class<?> mapDataType;
            Object mapDataInstance;
            
            if (IS_MODERN_VERSION) {
                // 1.21.x 版本使用新的地图数据结构
                ParameterizedType genericType = (ParameterizedType) getModifier().getField(5).getGenericType();
                mapDataType = (Class<?>) genericType.getActualTypeArguments()[0];
                mapDataInstance = StructureCache.newInstance(mapDataType);
                
                // 设置默认值
                getModifier().write(4, Optional.empty()); // MapIconData
                getModifier().write(5, Optional.of(mapDataInstance)); // MapData
                getBooleans().write(0, false); // 不跟踪玩家
                getBooleans().write(1, false); // 不是装饰地图
            } else {
                // 旧版本兼容模式
                mapDataType = getModifier().getField(4).getType();
                mapDataInstance = getModifier().read(4);
            }
            
            mapDataModifier = new StructureModifier<>(mapDataType).withTarget(mapDataInstance);
        } catch (Exception e) {
            LOGGER.warning("Failed to setup map format", e);
            getBooleans().write(0, false); // 不跟踪玩家
            mapDataModifier = null;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public @NotNull MapDataPacket setId(int id) {
        if (MAP_ID_CONSTRUCTOR == null) {
            getIntegers().write(0, id);
        } else {
            try {
                Class<?> mapIdClass = MAP_ID_CONSTRUCTOR.getDeclaringClass();
                Object mapIdInstance = MAP_ID_CONSTRUCTOR.newInstance(id);
                ((StructureModifier) getSpecificModifier(mapIdClass)).write(0, mapIdInstance);
            } catch (Exception e) {
                LOGGER.warning("Failed to instantiate MapId for map #" + id + ", falling back", e);
                getIntegers().write(0, id);
            }
        }
        return this;
    }

    public @NotNull MapDataPacket setArea(int columns, int rows, int x, int z) {
        if (mapDataModifier == null) {
            getIntegers()
                .write(1, x)
                .write(2, z)
                .write(3, columns)
                .write (4, rows);
        } else {
            mapDataModifier.withType(Integer.TYPE)
                .write(0, x)
                .write(1, z)
                .write(2, columns)
                .write(3, rows);
        }
        return this;
    }

    public @NotNull MapDataPacket setScale(int scale) {
        getBytes().write(0, (byte) scale);
        return this;
    }

    public @NotNull MapDataPacket setLocked(boolean locked) {
        if (IS_MODERN_VERSION) {
            getBooleans().write(1, locked); // 1.21.x 中这是装饰地图标志
        } else {
            getBooleans().write(0, locked); // 旧版本中这是锁定标志
        }
        return this;
    }

    public @NotNull MapDataPacket setPixels(byte[] pixels) {
        if (mapDataModifier == null) {
            getByteArrays().write(0, pixels);
        } else {
            mapDataModifier.withType(byte[].class).write(0, pixels);
        }
        return this;
    }
}
