package l2ft.gameserver.network.l2.s2c;

public class KeyPacket extends L2GameServerPacket
{
	private byte[] _key;

	public KeyPacket(byte[] key)
	{
		_key = key;
	}

	@Override
	public void writeImpl()
	{
		writeC(0x2E);
		if(_key == null || _key.length == 0)
		{
			writeC(0x00);
			return;
		}
		writeC(0x01);
		writeB(_key);
		writeD(0x01);
		writeD(0x00);
		writeC(0x00);
		writeD(0x00); // Seed (obfuscation key)
	}

	@Override
	public String getType()
	{
		return getClass().getSimpleName();
	}
}