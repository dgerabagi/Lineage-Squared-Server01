package l2ft.gameserver.network.l2.s2c;

public class WareHouseDone extends L2GameServerPacket
{

	@Override
	protected void writeImpl()
	{
		writeC(0x43);
		writeD(0); //?
	}
}