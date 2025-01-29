package l2ft.gameserver.network.l2.c2s;

import l2ft.gameserver.data.xml.holder.HennaHolder;
import l2ft.gameserver.templates.Henna;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.network.l2.s2c.HennaUnequipInfo;

public class RequestHennaUnequipInfo extends L2GameClientPacket
{
	private int _symbolId;

	/**
	 * format: d
	 */
	@Override
	protected void readImpl()
	{
		_symbolId = readD();
	}

	@Override
	protected void runImpl()
	{
		Player player = getClient().getActiveChar();
		if(player == null)
			return;

		Henna henna = HennaHolder.getInstance().getHenna(_symbolId);
		if(henna != null)
			player.sendPacket(new HennaUnequipInfo(henna, player));
	}
}