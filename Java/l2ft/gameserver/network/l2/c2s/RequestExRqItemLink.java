package l2ft.gameserver.network.l2.c2s;

import l2ft.gameserver.cache.ItemInfoCache;
import l2ft.gameserver.model.items.ItemInfo;
import l2ft.gameserver.network.l2.s2c.ActionFail;
import l2ft.gameserver.network.l2.s2c.ExRpItemLink;

public class RequestExRqItemLink extends L2GameClientPacket
{
	private int _objectId;

	@Override
	protected void readImpl()
	{
		_objectId = readD();
	}

	@Override
	protected void runImpl()
	{
		ItemInfo item;
		if((item = ItemInfoCache.getInstance().get(_objectId)) == null)
			sendPacket(ActionFail.STATIC);
		else
			sendPacket(new ExRpItemLink(item));
	}
}