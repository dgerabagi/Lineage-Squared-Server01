package l2ft.gameserver.model;

import l2ft.gameserver.data.xml.holder.ItemHolder;
import l2ft.gameserver.templates.item.ItemTemplate;
import l2ft.gameserver.utils.Location;

public class TeleportLocation extends Location
{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private final long _price;
	private final ItemTemplate _item;
	private final int _name;
	private final int _castleId;

	public TeleportLocation(int item, long price, int name, int castleId)
	{
		_price = price;
		_name = name;
		_item = ItemHolder.getInstance().getTemplate(item);
		_castleId = castleId;
	}

	public long getPrice()
	{
		return _price;
	}

	public ItemTemplate getItem()
	{
		return _item;
	}

	public int getName()
	{
		return _name;
	}

	public int getCastleId()
	{
		return _castleId;
	}
}
