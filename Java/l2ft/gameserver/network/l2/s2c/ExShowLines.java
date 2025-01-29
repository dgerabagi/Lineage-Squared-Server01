package l2ft.gameserver.network.l2.s2c;

public class ExShowLines extends L2GameServerPacket
{
	@Override
	protected void writeImpl()
	{
		writeEx(0xA5);
		// TODO hdcc cx[ddd]
	}
}