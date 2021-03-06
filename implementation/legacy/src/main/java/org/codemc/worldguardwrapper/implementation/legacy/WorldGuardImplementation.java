package org.codemc.worldguardwrapper.implementation.legacy;

import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.DefaultFlag;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedPolygonalRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.codemc.worldguardwrapper.flag.IWrappedFlag;
import org.codemc.worldguardwrapper.implementation.IWorldGuardImplementation;
import org.codemc.worldguardwrapper.implementation.legacy.flag.AbstractWrappedFlag;
import org.codemc.worldguardwrapper.implementation.legacy.region.WrappedRegion;
import org.codemc.worldguardwrapper.implementation.legacy.utility.WorldGuardFlagUtilities;
import org.codemc.worldguardwrapper.implementation.legacy.utility.WorldGuardVectorUtilities;
import org.codemc.worldguardwrapper.region.IWrappedRegion;

import java.util.*;
import java.util.stream.Collectors;

@NoArgsConstructor
public class WorldGuardImplementation implements IWorldGuardImplementation {

    private final WorldGuardPlugin plugin = WorldGuardPlugin.inst();

    private Optional<LocalPlayer> wrapPlayer(Player player) {
        return Optional.ofNullable(player).map(bukkitPlayer -> plugin.wrapPlayer(player));
    }

    private Optional<RegionManager> getWorldManager(@NonNull World world) {
        return Optional.ofNullable(plugin.getRegionManager(world));
    }

    private Optional<ApplicableRegionSet> getApplicableRegions(@NonNull Location location) {
        return getWorldManager(location.getWorld()).map(manager -> manager.getApplicableRegions(location));
    }

    private Optional<ApplicableRegionSet> getApplicableRegions(@NonNull Location minimum, @NonNull Location maximum) {
        return getWorldManager(minimum.getWorld()).map(manager -> manager.getApplicableRegions(
                new ProtectedCuboidRegion("temp", WorldGuardVectorUtilities.toBlockVector(minimum), WorldGuardVectorUtilities.toBlockVector(maximum))));
    }

    private <V> Optional<V> queryValue(Player player, @NonNull Location location, @NonNull Flag<V> flag) {
        return getApplicableRegions(location).map(applicableRegions -> applicableRegions.queryValue(wrapPlayer(player)
                .orElse(null), flag));
    }

    @Override
    public JavaPlugin getWorldGuardPlugin() {
        return WorldGuardPlugin.inst();
    }

    @Override
    public int getApiVersion() {
        return -6;
    }

    @Override
    public <T> Optional<IWrappedFlag<T>> getFlag(String name, Class<T> type) {
        for (Flag<?> currentFlag : DefaultFlag.getFlags()) {
            if (currentFlag.getName().equalsIgnoreCase(name)) {
                return Optional.of(WorldGuardFlagUtilities.wrap(currentFlag, type));
            }
        }
        return Optional.empty();
    }

    @Override
    public <T> Optional<T> queryFlag(Player player, Location location, IWrappedFlag<T> flag) {
        AbstractWrappedFlag<T> wrappedFlag = (AbstractWrappedFlag<T>) flag;
        return queryValue(player, location, wrappedFlag.getHandle()).flatMap(value -> wrappedFlag.fromWGValue(value));
    }

    @Override
    public <T> Optional<IWrappedFlag<T>> registerFlag(String name, Class<T> type, T defaultValue) {
        throw new UnsupportedOperationException("Custom flags aren't supported in this version of WorldGuard!");
    }

    @Override
    public Optional<IWrappedRegion> getRegion(World world, String id) {
        return getWorldManager(world)
                .map(regionManager -> regionManager.getRegion(id))
                .map(region -> new WrappedRegion(world, region));
    }

    @Override
    public Map<String, IWrappedRegion> getRegions(World world) {
        RegionManager regionManager = plugin.getRegionManager(world);
        Map<String, ProtectedRegion> regions = regionManager.getRegions();

        Map<String, IWrappedRegion> map = new HashMap<>();
        regions.forEach((name, region) -> map.put(name, new WrappedRegion(world, region)));

        return map;
    }

    @Override
    public Set<IWrappedRegion> getRegions(Location location) {
        ApplicableRegionSet regionSet = getApplicableRegions(location).orElse(null);
        Set<IWrappedRegion> set = new HashSet<>();

        if (regionSet == null) {
            return set;
        }

        regionSet.forEach(region -> set.add(new WrappedRegion(location.getWorld(), region)));
        return set;
    }

    @Override
    public Set<IWrappedRegion> getRegions(Location minimum, Location maximum) {
        ApplicableRegionSet regionSet = getApplicableRegions(minimum, maximum).orElse(null);
        Set<IWrappedRegion> set = new HashSet<>();

        if (regionSet == null) {
            return set;
        }

        regionSet.forEach(region -> set.add(new WrappedRegion(minimum.getWorld(), region)));
        return set;
    }

    @Override
    public Optional<IWrappedRegion> addRegion(String id, List<Location> points, int minY, int maxY) {
        ProtectedRegion region;
        World world = points.get(0).getWorld();
        if (points.size() == 2) {
            region = new ProtectedCuboidRegion(id, WorldGuardVectorUtilities.toBlockVector(points.get(0)), WorldGuardVectorUtilities.toBlockVector(points.get(1)));
        } else {
            region = new ProtectedPolygonalRegion(id, WorldGuardVectorUtilities.toBlockVector2DList(points), minY, maxY);
        }

        Optional<RegionManager> manager = getWorldManager(world);
        if (manager.isPresent()) {
            manager.get().addRegion(region);
            return Optional.of(new WrappedRegion(world, region));
        } else {
            return Optional.empty();
        }
    }

    @Override
    public Optional<Set<IWrappedRegion>> removeRegion(World world, String id) {
        Optional<Set<ProtectedRegion>> set = getWorldManager(world).map(manager -> manager.removeRegion(id));
        return set.map(protectedRegions -> protectedRegions.stream().map(region -> new WrappedRegion(world, region))
                .collect(Collectors.toSet()));
    }

}
