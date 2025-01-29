package l2ft.gameserver.listener.inventory;

import l2ft.commons.listener.Listener;
import l2ft.gameserver.model.Playable;
import l2ft.gameserver.model.items.ItemInstance;

public interface OnEquipListener extends Listener<Playable>
{
	public void onEquip(int slot, ItemInstance item, Playable actor);

	public void onUnequip(int slot, ItemInstance item, Playable actor);
}
