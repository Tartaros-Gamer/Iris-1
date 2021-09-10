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

package com.volmit.iris.util.hunk.view;

import com.volmit.iris.core.nms.INMS;
import com.volmit.iris.engine.data.chunk.LinkedTerrainChunk;
import com.volmit.iris.util.hunk.Hunk;
import lombok.Getter;
import org.bukkit.block.Biome;
import org.bukkit.generator.ChunkGenerator.BiomeGrid;

@SuppressWarnings("ClassCanBeRecord")
public class BiomeGridHunkView implements Hunk<Biome> {
    @Getter
    private final BiomeGrid chunk;

    public BiomeGridHunkView(BiomeGrid chunk) {
        this.chunk = chunk;
    }

    @Override
    public int getWidth() {
        return 16;
    }

    @Override
    public int getDepth() {
        return 16;
    }

    @Override
    public int getHeight() {
        // TODO: WARNING HEIGHT
        return 256;
    }

    @Override
    public void setRaw(int x, int y, int z, Biome t) {
        chunk.setBiome(x, y, z, t);
    }

    @Override
    public Biome getRaw(int x, int y, int z) {
        return chunk.getBiome(x, y, z);
    }

    public void forceBiomeBaseInto(int x, int y, int z, Object somethingVeryDirty) {
        if (chunk instanceof LinkedTerrainChunk) {
            INMS.get().forceBiomeInto(x, y, z, somethingVeryDirty, ((LinkedTerrainChunk) chunk).getRawBiome());
            return;
        }
        INMS.get().forceBiomeInto(x, y, z, somethingVeryDirty, chunk);
    }
}
