package l2ft.gameserver.network.l2.c2s;

import l2ft.gameserver.handler.items.IItemHandler;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.items.ItemInstance;
import l2ft.gameserver.network.l2.components.SystemMsg;
import l2ft.gameserver.network.l2.s2c.ExAutoSoulShot;
import l2ft.gameserver.network.l2.s2c.SystemMessage2;

/**
 * format:		chdd
 * @param decrypt
 */
public class RequestAutoSoulShot extends L2GameClientPacket
{
	private int _itemId;
	private boolean _type; // 1 = on : 0 = off;

	@Override
	protected void readImpl()
	{
		_itemId = readD();
		_type = readD() == 1;
	}

	@Override
	protected void runImpl()
	{
		Player activeChar = getClient().getActiveChar();

		if(activeChar == null)
			return;

		if(activeChar.getPrivateStoreType() != Player.STORE_PRIVATE_NONE || activeChar.isDead())
			return;

		ItemInstance item = activeChar.getInventory().getItemByItemId(_itemId);

		if(item == null)
			return;

		if(_type)
		{
			activeChar.addAutoSoulShot(_itemId);
			activeChar.sendPacket(new ExAutoSoulShot(_itemId, true));
			activeChar.sendPacket(new SystemMessage2(SystemMsg.THE_AUTOMATIC_USE_OF_S1_HAS_BEEN_ACTIVATED).addString(item.getName()));
			IItemHandler handler = item.getTemplate().getHandler();
			handler.useItem(activeChar, item, false);
			return;
		}

		activeChar.removeAutoSoulShot(_itemId);
		activeChar.sendPacket(new ExAutoSoulShot(_itemId, false));
		activeChar.sendPacket(new SystemMessage2(SystemMsg.THE_AUTOMATIC_USE_OF_S1_HAS_BEEN_DEACTIVATED).addString(item.getName()));
	}
}