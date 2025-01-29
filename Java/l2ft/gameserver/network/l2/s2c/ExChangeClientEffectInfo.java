package l2ft.gameserver.network.l2.s2c;

public class ExChangeClientEffectInfo extends L2GameServerPacket
{
	private int _state;

	public ExChangeClientEffectInfo(int state)
	{
		_state = state;
	}

	@Override
	protected void writeImpl()
	{
		writeEx(0xC1);
		writeD(0);
		writeD(_state);
	}
}
