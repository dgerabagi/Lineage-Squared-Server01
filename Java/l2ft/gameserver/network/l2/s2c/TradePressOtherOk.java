package l2ft.gameserver.network.l2.s2c;

public class TradePressOtherOk extends L2GameServerPacket
{
	public static final L2GameServerPacket STATIC = new TradePressOtherOk();

	@Override
	protected final void writeImpl()
	{
		writeC(0x82);
	}
}