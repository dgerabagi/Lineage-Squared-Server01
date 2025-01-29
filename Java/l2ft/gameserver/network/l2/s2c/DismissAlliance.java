package l2ft.gameserver.network.l2.s2c;

public class DismissAlliance extends L2GameServerPacket
{
	@Override
	protected void writeImpl()
	{
		writeC(0xAD);
		//TODO d
	}
}