package l2ft.gameserver.network.l2.s2c;

import l2ft.gameserver.model.instances.PetInstance;
import l2ft.gameserver.model.items.ItemInstance;

public class PetItemList extends L2GameServerPacket
{
	private ItemInstance[] items;

	public PetItemList(PetInstance cha)
	{
		items = cha.getInventory().getItems();
	}

	@Override
	protected final void writeImpl()
	{
		writeC(0xb3);
		writeH(items.length);

		for(ItemInstance item : items)
			writeItemInfo(item);
	}
}