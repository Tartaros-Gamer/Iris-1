/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2021 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.volmit.iris.core;

import com.volmit.iris.Iris;
import com.volmit.iris.engine.data.loader.ObjectResourceLoader;
import com.volmit.iris.engine.data.loader.ResourceLoader;
import com.volmit.iris.engine.object.*;
import com.volmit.iris.util.math.RNG;
import lombok.Data;

import java.io.File;
import java.util.Objects;
import java.util.function.Function;

@Data
public class IrisDataManager {
    private ResourceLoader<IrisBiome> biomeLoader;
    private ResourceLoader<IrisLootTable> lootLoader;
    private ResourceLoader<IrisRegion> regionLoader;
    private ResourceLoader<IrisDimension> dimensionLoader;
    private ResourceLoader<IrisGenerator> generatorLoader;
    private ResourceLoader<IrisJigsawPiece> jigsawPieceLoader;
    private ResourceLoader<IrisJigsawPool> jigsawPoolLoader;
    private ResourceLoader<IrisJigsawStructure> jigsawStructureLoader;
    private ResourceLoader<IrisEntity> entityLoader;
    private ResourceLoader<IrisSpawner> spawnerLoader;
    private ResourceLoader<IrisMod> modLoader;
    private ResourceLoader<IrisBlockData> blockLoader;
    private ObjectResourceLoader objectLoader;
    private boolean closed;
    private final File dataFolder;
    private final int id;

    public IrisDataManager(File dataFolder) {
        this(dataFolder, false);
    }

    public IrisDataManager(File dataFolder, boolean oneshot) {
        this.dataFolder = dataFolder;
        this.id = RNG.r.imax();
        closed = false;
        hotloaded();
    }

    public void close() {
        closed = true;
        dump();
        this.lootLoader = null;
        this.entityLoader = null;
        this.regionLoader = null;
        this.biomeLoader = null;
        this.modLoader = null;
        this.dimensionLoader = null;
        this.spawnerLoader = null;
        this.jigsawPoolLoader = null;
        this.jigsawPieceLoader = null;
        this.generatorLoader = null;
        this.jigsawStructureLoader = null;
        this.blockLoader = null;
        this.objectLoader = null;
    }

    private static void printData(ResourceLoader<?> rl) {
        Iris.warn("  " + rl.getResourceTypeName() + " @ /" + rl.getFolderName() + ": Cache=" + rl.getLoadCache().size() + " Folders=" + rl.getFolders().size());
    }

    public IrisDataManager copy() {
        return new IrisDataManager(dataFolder);
    }

    public void hotloaded() {
        if (closed) {
            return;
        }

        File packs = dataFolder;
        packs.mkdirs();
        this.lootLoader = new ResourceLoader<>(packs, this, "loot", "Loot", IrisLootTable.class);
        this.spawnerLoader = new ResourceLoader<>(packs, this, "spawners", "Spawner", IrisSpawner.class);
        this.entityLoader = new ResourceLoader<>(packs, this, "entities", "Entity", IrisEntity.class);
        this.regionLoader = new ResourceLoader<>(packs, this, "regions", "Region", IrisRegion.class);
        this.biomeLoader = new ResourceLoader<>(packs, this, "biomes", "Biome", IrisBiome.class);
        this.modLoader = new ResourceLoader<>(packs, this, "mods", "Mod", IrisMod.class);
        this.dimensionLoader = new ResourceLoader<>(packs, this, "dimensions", "Dimension", IrisDimension.class);
        this.jigsawPoolLoader = new ResourceLoader<>(packs, this, "jigsaw-pools", "Jigsaw Pool", IrisJigsawPool.class);
        this.jigsawStructureLoader = new ResourceLoader<>(packs, this, "jigsaw-structures", "Jigsaw Structure", IrisJigsawStructure.class);
        this.jigsawPieceLoader = new ResourceLoader<>(packs, this, "jigsaw-pieces", "Jigsaw Piece", IrisJigsawPiece.class);
        this.generatorLoader = new ResourceLoader<>(packs, this, "generators", "Generator", IrisGenerator.class);
        this.blockLoader = new ResourceLoader<>(packs, this, "blocks", "Block", IrisBlockData.class);
        this.objectLoader = new ObjectResourceLoader(packs, this, "objects", "Object");
    }

    public void dump() {
        if (closed) {
            return;
        }
        biomeLoader.clearCache();
        blockLoader.clearCache();
        lootLoader.clearCache();
        objectLoader.clearCache();
        spawnerLoader.clearCache();
        jigsawPieceLoader.clearCache();
        jigsawPoolLoader.clearCache();
        modLoader.clearCache();
        jigsawStructureLoader.clearCache();
        regionLoader.clearCache();
        dimensionLoader.clearCache();
        entityLoader.clearCache();
        generatorLoader.clearCache();
    }

    public void clearLists() {
        if (closed) {
            return;
        }

        lootLoader.clearList();
        blockLoader.clearList();
        entityLoader.clearList();
        biomeLoader.clearList();
        modLoader.clearList();
        spawnerLoader.clearList();
        regionLoader.clearList();
        dimensionLoader.clearList();
        generatorLoader.clearList();
        jigsawStructureLoader.clearList();
        jigsawPoolLoader.clearList();
        jigsawPieceLoader.clearList();
        objectLoader.clearList();
    }

    public static IrisObject loadAnyObject(String key) {
        return loadAny(key, (dm) -> dm.getObjectLoader().load(key, false));
    }

    public static IrisBiome loadAnyBiome(String key) {
        return loadAny(key, (dm) -> dm.getBiomeLoader().load(key, false));
    }

    public static IrisMod loadAnyMod(String key) {
        return loadAny(key, (dm) -> dm.getModLoader().load(key, false));
    }

    public static IrisJigsawPiece loadAnyJigsawPiece(String key) {
        return loadAny(key, (dm) -> dm.getJigsawPieceLoader().load(key, false));
    }

    public static IrisJigsawPool loadAnyJigsawPool(String key) {
        return loadAny(key, (dm) -> dm.getJigsawPoolLoader().load(key, false));
    }

    public static IrisEntity loadAnyEntity(String key) {
        return loadAny(key, (dm) -> dm.getEntityLoader().load(key, false));
    }

    public static IrisLootTable loadAnyLootTable(String key) {
        return loadAny(key, (dm) -> dm.getLootLoader().load(key, false));
    }

    public static IrisBlockData loadAnyBlock(String key) {
        return loadAny(key, (dm) -> dm.getBlockLoader().load(key, false));
    }

    public static IrisSpawner loadAnySpaner(String key) {
        return loadAny(key, (dm) -> dm.getSpawnerLoader().load(key, false));
    }

    public static IrisRegion loadAnyRegion(String key) {
        return loadAny(key, (dm) -> dm.getRegionLoader().load(key, false));
    }

    public static IrisDimension loadAnyDimension(String key) {
        return loadAny(key, (dm) -> dm.getDimensionLoader().load(key, false));
    }

    public static IrisJigsawStructure loadAnyJigsawStructure(String key) {
        return loadAny(key, (dm) -> dm.getJigsawStructureLoader().load(key, false));
    }

    public static IrisGenerator loadAnyGenerator(String key) {
        return loadAny(key, (dm) -> dm.getGeneratorLoader().load(key, false));
    }

    public static <T extends IrisRegistrant> T loadAny(String key, Function<IrisDataManager, T> v) {
        try {
            for (File i : Objects.requireNonNull(Iris.instance.getDataFolder("packs").listFiles())) {
                if (i.isDirectory()) {
                    IrisDataManager dm = new IrisDataManager(i, true);
                    T t = v.apply(dm);

                    if (t != null) {
                        return t;
                    }
                }
            }
        } catch (Throwable e) {
            Iris.reportError(e);
            e.printStackTrace();
        }

        return null;
    }
}