package l2ft.gameserver.network.l2.s2c;

/**
 * Format: ch (trigger)
 */
public class ExShowAdventurerGuideBook extends L2GameServerPacket
{
	@Override
	protected final void writeImpl()
	{
		writeEx(0x38);
	}
}