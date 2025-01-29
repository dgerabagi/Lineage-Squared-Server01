package l2ft.gameserver.network.l2.s2c;

public class ExRestartClient extends L2GameServerPacket
{
	@Override
	protected final void writeImpl()
	{
		writeEx(0x48);
	}
}
