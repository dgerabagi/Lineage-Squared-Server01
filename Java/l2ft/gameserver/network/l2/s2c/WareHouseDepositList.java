package l2ft.gameserver.network.l2.s2c;

import java.util.ArrayList;
import java.util.List;

import l2ft.commons.lang.ArrayUtils;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.items.ItemInfo;
import l2ft.gameserver.model.items.ItemInstance;
import l2ft.gameserver.model.items.Warehouse.ItemClassComparator;
import l2ft.gameserver.model.items.Warehouse.WarehouseType;


public class WareHouseDepositList extends L2GameServerPacket
{
	private int _whtype;
	private long _adena;
	private List<ItemInfo> _itemList;

	public WareHouseDepositList(Player cha, WarehouseType whtype)
	{
		_whtype = whtype.ordinal();
		_adena = cha.getAdena();

		ItemInstance[] items = cha.getInventory().getItems();
		ArrayUtils.eqSort(items, ItemClassComparator.getInstance());
		_itemList = new ArrayList<ItemInfo>(items.length);
		for(ItemInstance item : items)
			if(item.canBeStored(cha, _whtype == 1))
				_itemList.add(new ItemInfo(item));
	}

	@Override
	protected final void writeImpl()
	{
		writeC(0x41);
		writeH(_whtype);
		writeQ(_adena);
		writeH(_itemList.size());
		for(ItemInfo item : _itemList)
		{
			writeItemInfo(item);
			writeD(item.getObjectId());
		}
	}
}