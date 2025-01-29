package l2ft.gameserver.network.l2.s2c;

/**
 * @author SYS
 */
public class ExAttributeEnchantResult extends L2GameServerPacket
{
	private int _result;

	public ExAttributeEnchantResult(int unknown)
	{
		_result = unknown;
	}

	@Override
	protected final void writeImpl()
	{
		writeEx(0x61);
		writeD(_result);
	}
}