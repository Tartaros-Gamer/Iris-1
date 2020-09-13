package com.volmit.iris.object;

import org.bukkit.block.data.BlockData;

import com.volmit.iris.gen.atomics.AtomicCache;
import com.volmit.iris.noise.CNG;
import com.volmit.iris.util.ArrayType;
import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.MinNumber;
import com.volmit.iris.util.RNG;
import com.volmit.iris.util.Required;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("A palette of materials")
@Data
public class IrisMaterialPalette
{
	@DontObfuscate
	@Desc("The style of noise")
	private IrisGeneratorStyle style = NoiseStyle.STATIC.style();

	@MinNumber(0.0001)
	@DontObfuscate
	@Desc("The terrain zoom mostly for zooming in on a wispy palette")
	private double zoom = 5;

	@Required
	@ArrayType(min = 1, type = IrisBlockData.class)
	@DontObfuscate
	@Desc("The palette of blocks to be used in this layer")
	private KList<IrisBlockData> palette = new KList<IrisBlockData>().qadd(new IrisBlockData("STONE"));

	private final transient AtomicCache<KList<BlockData>> blockData = new AtomicCache<>();
	private final transient AtomicCache<CNG> layerGenerator = new AtomicCache<>();
	private final transient AtomicCache<CNG> heightGenerator = new AtomicCache<>();

	public BlockData get(RNG rng, double x, double y, double z)
	{
		if(getBlockData().isEmpty())
		{
			return null;
		}

		if(getBlockData().size() == 1)
		{
			return getBlockData().get(0);
		}

		return getLayerGenerator(rng).fit(getBlockData(), x / zoom, y / zoom, z / zoom);
	}

	public CNG getLayerGenerator(RNG rng)
	{
		return layerGenerator.aquire(() ->
		{
			RNG rngx = rng.nextParallelRNG(-23498896 + getBlockData().size());
			return style.create(rngx);
		});
	}

	public KList<IrisBlockData> add(String b)
	{
		palette.add(new IrisBlockData(b));

		return palette;
	}

	public IrisMaterialPalette qadd(String b)
	{
		palette.add(new IrisBlockData(b));

		return this;
	}

	public KList<BlockData> getBlockData()
	{
		return blockData.aquire(() ->
		{
			KList<BlockData> blockData = new KList<>();
			for(IrisBlockData ix : palette)
			{
				BlockData bx = ix.getBlockData();
				if(bx != null)
				{
					for(int i = 0; i < ix.getWeight(); i++)
					{
						blockData.add(bx);
					}
				}
			}

			return blockData;
		});
	}

	public IrisMaterialPalette zero()
	{
		palette.clear();
		return this;
	}
}