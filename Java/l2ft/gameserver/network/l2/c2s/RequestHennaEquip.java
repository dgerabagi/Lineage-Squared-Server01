package l2ft.gameserver.network.l2.c2s;

import l2ft.gameserver.data.xml.holder.HennaHolder;
import l2ft.gameserver.templates.Henna;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.network.l2.components.SystemMsg;

public class RequestHennaEquip extends L2GameClientPacket
{
	private int _symbolId;

	/**
	 * packet type id 0x6F
	 * format:		cd
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

		Henna temp = HennaHolder.getInstance().getHenna(_symbolId);
		if(temp == null || !temp.isForThisClass(player))
		{
			player.sendPacket(SystemMsg.THE_SYMBOL_CANNOT_BE_DRAWN);
			return;
		}

		long adena = player.getAdena();
		long countDye = player.getInventory().getCountOf(temp.getDyeId());

		if(countDye >= temp.getDrawCount() && adena >= temp.getPrice())
		{
			if (player.consumeItem(temp.getDyeId(), temp.getDrawCount()) && player.reduceAdena(temp.getPrice()))
			{
				player.sendPacket(SystemMsg.THE_SYMBOL_HAS_BEEN_ADDED);
				player.addHenna(temp);
			}
		}
		else
			player.sendPacket(SystemMsg.THE_SYMBOL_CANNOT_BE_DRAWN);
	}
}