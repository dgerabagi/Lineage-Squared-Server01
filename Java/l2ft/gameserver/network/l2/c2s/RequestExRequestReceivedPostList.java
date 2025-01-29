package l2ft.gameserver.network.l2.c2s;

import l2ft.gameserver.model.Player;
import l2ft.gameserver.network.l2.s2c.ExNoticePostArrived;
import l2ft.gameserver.network.l2.s2c.ExShowReceivedPostList;

/**
 * Отсылается при нажатии на кнопку "почта", "received mail" или уведомление от {@link ExNoticePostArrived}, запрос входящих писем.
 * В ответ шлется {@link ExShowReceivedPostList}
 */
public class RequestExRequestReceivedPostList extends L2GameClientPacket
{
	@Override
	protected void readImpl()
	{
		//just a trigger
	}

	@Override
	protected void runImpl()
	{
		Player cha = getClient().getActiveChar();
		if(cha != null)
		{
			cha.sendItemList(true);
			cha.sendItemList(false);
			cha.sendPacket(new ExShowReceivedPostList(cha));
		}
		
		
	}
}