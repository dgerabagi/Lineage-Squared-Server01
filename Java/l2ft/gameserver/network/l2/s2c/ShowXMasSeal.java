package l2ft.gameserver.network.l2.s2c;

public class ShowXMasSeal extends L2GameServerPacket
{
	private int _item;

	public ShowXMasSeal(int item)
	{
		_item = item;
	}

	@Override
	protected void writeImpl()
	{
		writeC(0xf8);
		writeD(_item);
	}
}