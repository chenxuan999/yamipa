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
    private static final int LOCKED_INDEX = 0; // 固定使用 1.17+ 的值
    private static final @Nullable Constructor<?> MAP_ID_CONSTRUCTOR;
    private @Nullable StructureModifier<?> mapDataModifier;

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
            // 固定使用新版本的地图数据格式
            ParameterizedType genericType = (ParameterizedType) getModifier().getField(4).getGenericType();
            Class<?> mapDataType = (Class<?>) genericType.getActualTypeArguments()[0];
            Object mapDataInstance = StructureCache.newInstance(mapDataType);
            getModifier().write(3, Optional.empty());
            getModifier().write(4, Optional.of(mapDataInstance));
            mapDataModifier = new StructureModifier<>(mapDataType).withTarget(mapDataInstance);
        } catch (Exception e) {
            LOGGER.warning("Failed to setup new map format, trying legacy mode", e);
            try {
                Class<?> mapDataType = getModifier().getField(4).getType();
                Object mapDataInstance = getModifier().read(4);
                mapDataModifier = new StructureModifier<>(mapDataType).withTarget(mapDataInstance);
            } catch (Exception e2) {
                LOGGER.warning("Failed to setup legacy map format, using basic mode", e2);
                getBooleans().write(0, false); // Disable tracking position
                mapDataModifier = null;
            }
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
        getBooleans().write(LOCKED_INDEX, locked);
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
