package l2ft.gameserver.network.l2.s2c;

import l2ft.gameserver.model.Player;

/**
 * Format: (ch) dc
 * d: character object id
 * c: 1 if won 0 if failed
 */
public class ExFishingEnd extends L2GameServerPacket
{
	private int _charId;
	private boolean _win;

	public ExFishingEnd(Player character, boolean win)
	{
		_charId = character.getObjectId();
		_win = win;
	}

	@Override
	protected final void writeImpl()
	{
		writeEx(0x1f);
		writeD(_charId);
		writeC(_win ? 1 : 0);
	}
}