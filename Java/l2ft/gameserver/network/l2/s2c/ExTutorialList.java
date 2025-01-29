package l2ft.gameserver.network.l2.s2c;

public class ExTutorialList extends L2GameServerPacket
{
	@Override
	protected void writeImpl()
	{
		writeEx(0x6B);
		// todo writeB(new byte[128]);
	}
}