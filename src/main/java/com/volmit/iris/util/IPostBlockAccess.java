package ninja.bytecode.iris.util;

import org.bukkit.block.data.BlockData;

public interface IPostBlockAccess
{
	public BlockData getPostBlock(int x, int y, int z);

	public void setPostBlock(int x, int y, int z, BlockData d);

	public int highestTerrainOrFluidBlock(int x, int z);

	public int highestTerrainBlock(int x, int z);
}