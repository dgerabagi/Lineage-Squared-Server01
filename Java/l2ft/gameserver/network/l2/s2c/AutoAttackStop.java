package l2ft.gameserver.network.l2.s2c;

public class AutoAttackStop extends L2GameServerPacket
{
	// dh
	private int _targetId;

	/**
	 * @param _characters
	 */
	public AutoAttackStop(int targetId)
	{
		_targetId = targetId;
	}

	@Override
	protected final void writeImpl()
	{
		writeC(0x26);
		writeD(_targetId);
	}
}