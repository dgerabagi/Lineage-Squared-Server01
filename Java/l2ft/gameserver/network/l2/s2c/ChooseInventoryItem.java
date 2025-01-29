package l2ft.gameserver.network.l2.s2c;

public class ChooseInventoryItem extends L2GameServerPacket
{
	private int ItemID;

	public ChooseInventoryItem(int id)
	{
		ItemID = id;
	}

	@Override
	protected final void writeImpl()
	{
		writeC(0x7c);
		writeD(ItemID);
	}
}