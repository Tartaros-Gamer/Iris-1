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

package com.volmit.iris.engine;

import com.google.common.util.concurrent.AtomicDouble;
import com.google.gson.Gson;
import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.core.events.IrisEngineHotloadEvent;
import com.volmit.iris.core.project.IrisProject;
import com.volmit.iris.core.service.PreservationSVC;
import com.volmit.iris.engine.actuator.IrisBiomeActuator;
import com.volmit.iris.engine.actuator.IrisDecorantActuator;
import com.volmit.iris.engine.actuator.IrisTerrainNormalActuator;
import com.volmit.iris.engine.data.cache.AtomicCache;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.framework.EngineEffects;
import com.volmit.iris.engine.framework.EngineMetrics;
import com.volmit.iris.engine.framework.EngineStage;
import com.volmit.iris.engine.framework.EngineTarget;
import com.volmit.iris.engine.framework.EngineWorldManager;
import com.volmit.iris.engine.framework.SeedManager;
import com.volmit.iris.engine.framework.WrongEngineBroException;
import com.volmit.iris.engine.mantle.EngineMantle;
import com.volmit.iris.engine.modifier.IrisBodyModifier;
import com.volmit.iris.engine.modifier.IrisCarveModifier;
import com.volmit.iris.engine.modifier.IrisDepositModifier;
import com.volmit.iris.engine.modifier.IrisPerfectionModifier;
import com.volmit.iris.engine.modifier.IrisPostModifier;
import com.volmit.iris.engine.object.IrisBiome;
import com.volmit.iris.engine.object.IrisBiomePaletteLayer;
import com.volmit.iris.engine.object.IrisDecorator;
import com.volmit.iris.engine.object.IrisEngineData;
import com.volmit.iris.engine.object.IrisObjectPlacement;
import com.volmit.iris.engine.scripting.EngineExecutionEnvironment;
import com.volmit.iris.util.atomics.AtomicRollingSequence;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.context.IrisContext;
import com.volmit.iris.util.documentation.BlockCoordinates;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.hunk.Hunk;
import com.volmit.iris.util.io.IO;
import com.volmit.iris.util.math.M;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.scheduling.ChronoLatch;
import com.volmit.iris.util.scheduling.J;
import com.volmit.iris.util.scheduling.PrecisionStopwatch;
import lombok.Data;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Data
public class IrisEngine implements Engine {
    private final AtomicInteger bud;
    private final AtomicInteger buds;
    private final AtomicInteger generated;
    private final AtomicInteger generatedLast;
    private final AtomicDouble perSecond;
    private final AtomicLong lastGPS;
    private final EngineTarget target;
    private final IrisContext context;
    private final EngineMantle mantle;
    private final ChronoLatch perSecondLatch;
    private final ChronoLatch perSecondBudLatch;
    private final EngineMetrics metrics;
    private final boolean studio;
    private final KList<EngineStage> stages;
    private final AtomicRollingSequence wallClock;
    private final int art;
    private final AtomicCache<IrisEngineData> engineData = new AtomicCache<>();
    private final AtomicBoolean cleaning;
    private final ChronoLatch cleanLatch;
    private final SeedManager seedManager;
    private EngineEffects effects;
    private EngineExecutionEnvironment execution;
    private EngineWorldManager worldManager;
    private volatile int parallelism;
    private volatile int minHeight;
    private boolean failing;
    private boolean closed;
    private int cacheId;
    private double maxBiomeObjectDensity;
    private double maxBiomeLayerDensity;
    private double maxBiomeDecoratorDensity;
    private IrisComplex complex;

    public IrisEngine(EngineTarget target, boolean studio) {
        this.studio = studio;
        this.target = target;
        stages = new KList<>();
        getEngineData();
        verifySeed();
        this.seedManager = new SeedManager(target.getWorld().getRawWorldSeed());
        bud = new AtomicInteger(0);
        buds = new AtomicInteger(0);
        metrics = new EngineMetrics(32);
        cleanLatch = new ChronoLatch(Math.max(10000, IrisSettings.get().getConcurrency().getParallaxEvictionMS()));
        generatedLast = new AtomicInteger(0);
        perSecond = new AtomicDouble(0);
        perSecondLatch = new ChronoLatch(1000, false);
        perSecondBudLatch = new ChronoLatch(1000, false);
        wallClock = new AtomicRollingSequence(32);
        lastGPS = new AtomicLong(M.ms());
        generated = new AtomicInteger(0);
        mantle = new IrisEngineMantle(this);
        context = new IrisContext(this);
        cleaning = new AtomicBoolean(false);
        context.touch();
        Iris.info("Initializing Engine: " + target.getWorld().name() + "/" + target.getDimension().getLoadKey() + " (" + 256 + " height) Seed: " + getSeedManager().getSeed());
        getData().setEngine(this);
        minHeight = 0;
        failing = false;
        closed = false;
        art = J.ar(this::tickRandomPlayer, 0);
        setupEngine();
        Iris.debug("Engine Initialized " + getCacheID());
    }

    private void verifySeed() {
        if (getEngineData().getSeed() != null && getEngineData().getSeed() != target.getWorld().getRawWorldSeed()) {
            target.getWorld().setRawWorldSeed(getEngineData().getSeed());
        }
    }

    private void tickRandomPlayer() {
        if (perSecondBudLatch.flip()) {
            buds.set(bud.get());
            bud.set(0);
        }

        if (effects != null) {
            effects.tickRandomPlayer();
        }
    }

    private void prehotload() {
        worldManager.close();
        complex.close();
        execution.close();
        stages.forEach(EngineStage::close);
        stages.clear();
        effects.close();
        J.a(() -> new IrisProject(getData().getDataFolder()).updateWorkspace());
    }

    private void setupEngine() {
        try {
            Iris.debug("Setup Engine " + getCacheID());
            cacheId = RNG.r.nextInt();
            worldManager = new IrisWorldManager(this);
            complex = new IrisComplex(this);
            execution = new IrisExecutionEnvironment(this);
            effects = new IrisEngineEffects(this);
            setupStages();
            J.a(this::computeBiomeMaxes);
        } catch (Throwable e) {
            Iris.error("FAILED TO SETUP ENGINE!");
            e.printStackTrace();
        }

        Iris.debug("Engine Setup Complete " + getCacheID());
    }

    private void setupStages() {
        var terrain = new IrisTerrainNormalActuator(this);
        var biome = new IrisBiomeActuator(this);
        var decorant = new IrisDecorantActuator(this);
        var cave = new IrisCarveModifier(this);
        var post = new IrisPostModifier(this);
        var deposit = new IrisDepositModifier(this);
        var bodies = new IrisBodyModifier(this);
        var perfection = new IrisPerfectionModifier(this);

        registerStage((x, z, k, p, m) -> getMantle().generateMatter(x >> 4, z >> 4, m));
        registerStage((x, z, k, p, m) -> terrain.actuate(x, z, k, m));
        registerStage((x, z, k, p, m) -> biome.actuate(x, z, p, m));
        registerStage((x, z, k, p, m) -> cave.modify(x >> 4, z >> 4, k, m));
        registerStage((x, z, k, p, m) -> bodies.modify(x >> 4, z >> 4, k, m));
        registerStage((x, z, k, p, m) -> decorant.actuate(x, z, k, m));
        registerStage((x, z, k, p, m) -> post.modify(x, z, k, m));
        registerStage((x, z, k, p, m) -> deposit.modify(x, z, k, m));
        registerStage((x, z, K, p, m) -> getMantle().insertMatter(x >> 4, z >> 4, BlockData.class, K, m));
        registerStage((x, z, k, p, m) -> perfection.modify(x, z, k, m));
    }

    @Override
    public void hotload() {
        hotloadSilently();
        Iris.callEvent(new IrisEngineHotloadEvent(this));
    }

    public void hotloadComplex() {
        complex.close();
        complex = new IrisComplex(this);
    }

    public void hotloadSilently() {
        getData().dump();
        getData().clearLists();
        getTarget().setDimension(getData().getDimensionLoader().load(getDimension().getLoadKey()));
        prehotload();
        setupEngine();
    }

    @Override
    public IrisEngineData getEngineData() {
        World w = null;

        return engineData.aquire(() -> {
            //TODO: Method this file
            File f = new File(getWorld().worldFolder(), "iris/engine-data/" + getDimension().getLoadKey() + ".json");

            if (!f.exists()) {
                try {
                    f.getParentFile().mkdirs();
                    IO.writeAll(f, new Gson().toJson(new IrisEngineData()));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            try {
                return new Gson().fromJson(IO.readAll(f), IrisEngineData.class);
            } catch (Throwable e) {
                e.printStackTrace();
            }

            return new IrisEngineData();
        });
    }

    @Override
    public int getGenerated() {
        return generated.get();
    }

    @Override
    public double getGeneratedPerSecond() {
        if (perSecondLatch.flip()) {
            double g = generated.get() - generatedLast.get();
            generatedLast.set(generated.get());

            if (g == 0) {
                return 0;
            }

            long dur = M.ms() - lastGPS.get();
            lastGPS.set(M.ms());
            perSecond.set(g / ((double) (dur) / 1000D));
        }

        return perSecond.get();
    }

    @Override
    public boolean isStudio() {
        return studio;
    }

    private void computeBiomeMaxes() {
        for (IrisBiome i : getDimension().getAllBiomes(this)) {
            double density = 0;

            for (IrisObjectPlacement j : i.getObjects()) {
                density += j.getDensity() * j.getChance();
            }

            maxBiomeObjectDensity = Math.max(maxBiomeObjectDensity, density);
            density = 0;

            for (IrisDecorator j : i.getDecorators()) {
                density += Math.max(j.getStackMax(), 1) * j.getChance();
            }

            maxBiomeDecoratorDensity = Math.max(maxBiomeDecoratorDensity, density);
            density = 0;

            for (IrisBiomePaletteLayer j : i.getLayers()) {
                density++;
            }

            maxBiomeLayerDensity = Math.max(maxBiomeLayerDensity, density);
        }
    }

    @Override
    public void registerStage(EngineStage stage) {
        stages.add(stage);
    }

    @Override
    public int getBlockUpdatesPerSecond() {
        return buds.get();
    }

    public void printMetrics(CommandSender sender) {
        KMap<String, Double> totals = new KMap<>();
        KMap<String, Double> weights = new KMap<>();
        double masterWallClock = wallClock.getAverage();
        KMap<String, Double> timings = getMetrics().pull();
        double totalWeight = 0;
        double wallClock = getMetrics().getTotal().getAverage();

        for (double j : timings.values()) {
            totalWeight += j;
        }

        for (String j : timings.k()) {
            weights.put(getName() + "." + j, (wallClock / totalWeight) * timings.get(j));
        }

        totals.put(getName(), wallClock);

        double mtotals = 0;

        for (double i : totals.values()) {
            mtotals += i;
        }

        for (String i : totals.k()) {
            totals.put(i, (masterWallClock / mtotals) * totals.get(i));
        }

        double v = 0;

        for (double i : weights.values()) {
            v += i;
        }

        for (String i : weights.k()) {
            weights.put(i, weights.get(i) / v);
        }

        sender.sendMessage("Total: " + C.BOLD + C.WHITE + Form.duration(masterWallClock, 0));

        for (String i : totals.k()) {
            sender.sendMessage("  Engine " + C.UNDERLINE + C.GREEN + i + C.RESET + ": " + C.BOLD + C.WHITE + Form.duration(totals.get(i), 0));
        }

        sender.sendMessage("Details: ");

        for (String i : weights.sortKNumber().reverse()) {
            String befb = C.UNDERLINE + "" + C.GREEN + "" + i.split("\\Q[\\E")[0] + C.RESET + C.GRAY + "[";
            String num = C.GOLD + i.split("\\Q[\\E")[1].split("]")[0] + C.RESET + C.GRAY + "].";
            String afb = C.ITALIC + "" + C.AQUA + i.split("\\Q]\\E")[1].substring(1) + C.RESET + C.GRAY;

            sender.sendMessage("  " + befb + num + afb + ": " + C.BOLD + C.WHITE + Form.pc(weights.get(i), 0));
        }
    }

    @Override
    public void close() {
        closed = true;
        J.car(art);
        getWorldManager().close();
        getTarget().close();
        saveEngineData();
        stages.forEach(EngineStage::close);
        stages.clear();
        getMantle().close();
        getComplex().close();
        getData().dump();
        getData().clearLists();
        Iris.service(PreservationSVC.class).dereference();
        Iris.debug("Engine Fully Shutdown!");
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void recycle() {
        if (!cleanLatch.flip()) {
            return;
        }

        if (cleaning.get()) {
            cleanLatch.flipDown();
            return;
        }

        cleaning.set(true);

        J.a(() -> {
            try {
                getMantle().trim();
                getData().getObjectLoader().clean();
            } catch (Throwable e) {
                Iris.reportError(e);
                Iris.error("Cleanup failed! Enable debug to see stacktrace.");
            }

            cleaning.lazySet(false);
        });
    }

    @BlockCoordinates
    @Override
    public double modifyX(double x) {
        return x / getDimension().getTerrainZoom();
    }

    @BlockCoordinates
    @Override
    public double modifyZ(double z) {
        return z / getDimension().getTerrainZoom();
    }

    @BlockCoordinates
    @Override
    public void generate(int x, int z, Hunk<BlockData> vblocks, Hunk<Biome> vbiomes, boolean multicore) throws WrongEngineBroException {
        if (closed) {
            throw new WrongEngineBroException();
        }

        context.touch();
        getEngineData().getStatistics().generatedChunk();
        try {
            PrecisionStopwatch p = PrecisionStopwatch.start();
            Hunk<BlockData> blocks = vblocks.listen((xx, y, zz, t) -> catchBlockUpdates(x + xx, y + getMinHeight(), z + zz, t));

            if (getDimension().isDebugChunkCrossSections() && ((x >> 4) % getDimension().getDebugCrossSectionsMod() == 0 || (z >> 4) % getDimension().getDebugCrossSectionsMod() == 0)) {
                for (int i = 0; i < 16; i++) {
                    for (int j = 0; j < 16; j++) {
                        blocks.set(i, 0, j, Material.CRYING_OBSIDIAN.createBlockData());
                    }
                }
            } else {
                for (EngineStage i : stages) {
                    i.generate(x, z, blocks, vbiomes, multicore);
                }
            }

            getMetrics().getTotal().put(p.getMilliseconds());
            generated.incrementAndGet();
            recycle();
        } catch (Throwable e) {
            Iris.reportError(e);
            fail("Failed to generate " + x + ", " + z, e);
        }
    }

    @Override
    public void saveEngineData() {
        //TODO: Method this file
        File f = new File(getWorld().worldFolder(), "iris/engine-data/" + getDimension().getLoadKey() + ".json");
        f.getParentFile().mkdirs();
        try {
            IO.writeAll(f, new Gson().toJson(getEngineData()));
            Iris.debug("Saved Engine Data");
        } catch (IOException e) {
            Iris.error("Failed to save Engine Data");
            e.printStackTrace();
        }
    }

    @Override
    public void blockUpdatedMetric() {
        bud.incrementAndGet();
    }

    @Override
    public IrisBiome getFocus() {
        if (getDimension().getFocus() == null || getDimension().getFocus().trim().isEmpty()) {
            return null;
        }

        return getData().getBiomeLoader().load(getDimension().getFocus());
    }

    @Override
    public void fail(String error, Throwable e) {
        failing = true;
        Iris.error(error);
        e.printStackTrace();
    }

    @Override
    public boolean hasFailed() {
        return failing;
    }

    @Override
    public int getCacheID() {
        return cacheId;
    }
}
