package l2ft.gameserver.network.l2.s2c;

public class RequestTimeCheck extends L2GameServerPacket
{
	@Override
	protected void writeImpl()
	{
		writeC(0xC1);
		//TODO d
	}
}