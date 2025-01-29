package l2ft.gameserver.network.l2.s2c;

public class ExPlayAnimation extends L2GameServerPacket
{
	@Override
	protected void writeImpl()
	{
		writeEx(0x5A);
		// TODO dcdS
	}
}