package l2ft.gameserver.templates;

import java.util.ArrayList;
import java.util.List;

import l2ft.gameserver.data.xml.holder.ItemHolder;
import l2ft.gameserver.model.base.ClassId;
import l2ft.gameserver.model.base.Race;
import l2ft.gameserver.templates.item.ItemTemplate;
import l2ft.gameserver.utils.Location;


public class PlayerTemplate extends CharTemplate
{
	/** The Class<?> object of the L2Player */
	public final ClassId classId;

	public final Race race;
	public final String className;

	public final Location spawnLoc = new Location();

	public final boolean isMale;

	public final int classBaseLevel;
	public final double lvlHpAdd;
	public final double lvlHpMod;
	public final double lvlCpAdd;
	public final double lvlCpMod;
	public final double lvlMpAdd;
	public final double lvlMpMod;

	private List<ItemTemplate> _items = new ArrayList<ItemTemplate>();

	public PlayerTemplate(StatsSet set)
	{
		super(set);
		classId = ClassId.VALUES[set.getInteger("classId")];
		race = Race.values()[set.getInteger("raceId")];
		className = set.getString("className");

		spawnLoc.set(new Location(-117345, 87178, -12695));

		isMale = set.getBool("isMale", true);

		classBaseLevel = set.getInteger("classBaseLevel");
		lvlHpAdd = set.getDouble("lvlHpAdd");
		lvlHpMod = set.getDouble("lvlHpMod");
		lvlCpAdd = set.getDouble("lvlCpAdd");
		lvlCpMod = set.getDouble("lvlCpMod");
		lvlMpAdd = set.getDouble("lvlMpAdd");
		lvlMpMod = set.getDouble("lvlMpMod");
	}

	/**
	 * add starter equipment
	 * @param i
	 */
	public void addItem(int itemId)
	{
		ItemTemplate item = ItemHolder.getInstance().getTemplate(itemId);
		if(item != null)
			_items.add(item);
	}

	/**
	 *
	 * @return itemIds of all the starter equipment
	 */
	public ItemTemplate[] getItems()
	{
		return _items.toArray(new ItemTemplate[_items.size()]);
	}
}