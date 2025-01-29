package l2ft.gameserver.network.l2.s2c;

/**
 * Reworked: VISTALL
 */
public class AcquireSkillDone extends L2GameServerPacket
{
	public static final L2GameServerPacket STATIC = new AcquireSkillDone();

	@Override
	protected void writeImpl()
	{
		writeC(0x94);
	}
}