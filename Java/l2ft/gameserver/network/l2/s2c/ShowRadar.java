package l2ft.gameserver.network.l2.s2c;

public class ShowRadar extends L2GameServerPacket
{
	@Override
	protected final void writeImpl()
	{
		writeC(0xAA);
		//TODO ddddd
	}
}