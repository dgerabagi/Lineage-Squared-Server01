package l2ft.gameserver.network.l2.s2c;

public class ExNotifyBirthDay extends L2GameServerPacket
{
	public static final L2GameServerPacket STATIC = new ExNotifyBirthDay();

	@Override
	protected void writeImpl()
	{
		writeEx(0x8F);
	}
}