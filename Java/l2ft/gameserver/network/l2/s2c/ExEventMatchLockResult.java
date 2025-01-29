package l2ft.gameserver.network.l2.s2c;

public class ExEventMatchLockResult extends L2GameServerPacket
{
	@Override
	protected void writeImpl()
	{
		writeEx(0x0B);
		// TODO пока не реализован даже в клиенте
	}
}