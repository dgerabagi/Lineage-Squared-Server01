package handler.items;

import l2ft.gameserver.model.Playable;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.items.ItemInstance;
import l2ft.gameserver.network.l2.components.SystemMsg;
import l2ft.gameserver.network.l2.s2c.SystemMessage;

abstract class SimpleItemHandler extends ScriptItemHandler
{
	@Override
	public boolean useItem(Playable playable, ItemInstance item, boolean ctrl)
	{
		Player player;
		if(playable.isPlayer())
			player = (Player) playable;
		else if(playable.isPet())
			player = playable.getPlayer();
		else
			return false;

		if(player.isInFlyingTransform())
		{
			player.sendPacket(new SystemMessage(SystemMessage.S1_CANNOT_BE_USED_DUE_TO_UNSUITABLE_TERMS).addItemName(item.getItemId()));
			return false;
		}

		return useItemImpl(player, item, ctrl);
	}

	protected abstract boolean useItemImpl(Player player, ItemInstance item, boolean ctrl);

	public static boolean useItem(Player player, ItemInstance item, long count)
	{
		if(player.getInventory().destroyItem(item, count))
		{
			player.sendPacket(new SystemMessage(SystemMessage.YOU_USE_S1).addItemName(item.getItemId()));
			return true;
		}

		player.sendPacket(SystemMsg.INCORRECT_ITEM_COUNT);
		return false;
	}
}
