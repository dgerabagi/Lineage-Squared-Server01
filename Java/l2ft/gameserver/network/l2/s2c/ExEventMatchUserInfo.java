package l2ft.gameserver.network.l2.s2c;

public class ExEventMatchUserInfo extends L2GameServerPacket
{
	@Override
	protected void writeImpl()
	{
		writeEx(0x02);
		// TODO dSdddddddd
	}
}