package handler.items;

import gnu.trove.TIntHashSet;

import l2ft.gameserver.cache.Msg;
import l2ft.gameserver.data.xml.holder.ItemHolder;
import l2ft.gameserver.model.Playable;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.items.ItemInstance;
import l2ft.gameserver.network.l2.s2c.L2GameServerPacket;
import l2ft.gameserver.network.l2.s2c.SystemMessage;
import l2ft.gameserver.stats.Stats;
import l2ft.gameserver.templates.item.ItemTemplate;
import l2ft.gameserver.utils.ItemFunctions;
import handler.items.ScriptItemHandler;

/**
 * @author VISTALL
 * @date 7:27/17.03.2011
 */
public class EquipableItem extends ScriptItemHandler
{
	private int[] _itemIds;

	public EquipableItem()
	{
		TIntHashSet set = new TIntHashSet();
		for(ItemTemplate template : ItemHolder.getInstance().getAllTemplates())
		{
			if(template == null)
				continue;
			if(template.isEquipable())
				set.add(template.getItemId());
		}
		_itemIds = set.toArray();
	}

	@Override
	public boolean useItem(Playable playable, ItemInstance item, boolean ctrl)
	{
		if(!playable.isPlayer())
			return false;
		Player player = playable.getPlayer();
		if(player.isCastingNow())
		{
			player.sendPacket(Msg.YOU_MAY_NOT_EQUIP_ITEMS_WHILE_CASTING_OR_PERFORMING_A_SKILL);
			return false;
		}

		// Нельзя снимать/одевать любое снаряжение при этих условиях
		if(player.isStunned() || player.isSleeping() || player.isParalyzed() || player.isAlikeDead() || player.isWeaponEquipBlocked())
		{
			player.sendPacket(new SystemMessage(SystemMessage.S1_CANNOT_BE_USED_DUE_TO_UNSUITABLE_TERMS).addItemName(item.getItemId()));
			return false;
		}

		int bodyPart = item.getBodyPart();

		if(bodyPart == ItemTemplate.SLOT_LR_HAND || bodyPart == ItemTemplate.SLOT_L_HAND || bodyPart == ItemTemplate.SLOT_R_HAND)
		{
			// Нельзя снимать/одевать оружие, сидя на пете
			// Нельзя снимать/одевать проклятое оружие и флаги
			// Нельзя одевать/снимать оружие/щит/сигил, управляя кораблем
			if(player.isMounted() || player.isCursedWeaponEquipped() || player.getActiveWeaponFlagAttachment() != null || player.isClanAirShipDriver())
			{
				player.sendPacket(new SystemMessage(SystemMessage.S1_CANNOT_BE_USED_DUE_TO_UNSUITABLE_TERMS).addItemName(item.getItemId()));
				return false;
			}
		}

		// Нельзя снимать/одевать проклятое оружие
		if(item.isCursed())
		{
			player.sendPacket(new SystemMessage(SystemMessage.S1_CANNOT_BE_USED_DUE_TO_UNSUITABLE_TERMS).addItemName(item.getItemId()));
			return false;
		}

		// Don't allow weapon/shield hero equipment during Olympiads
		if(player.isInOlympiadMode() && item.isHeroWeapon())
		{
			player.sendActionFailed();
			return false;
		}

		if(item.isEquipped())
		{
			if(item.isAccessory() && player.calcStat(Stats.REFLECT_AND_BLOCK_MSKILL_DAMAGE_CHANCE, 0, player, null) > 0)
				return false;
			
			ItemInstance weapon = player.getActiveWeaponInstance();
			if(item == weapon)
			{
				player.abortAttack(true, true);
				player.abortCast(true, true);
			}
			player.sendDisarmMessage(item);
			player.getInventory().unEquipItem(item);
			return false;
		}

		L2GameServerPacket p = ItemFunctions.checkIfCanEquip(player, item);
		if(p != null)
		{
			player.sendPacket(p);
			return false;
		}

		player.getInventory().equipItem(item);
		if(!item.isEquipped())
		{
			player.sendActionFailed();
			return false;
		}

		SystemMessage sm;
		if(item.getEnchantLevel() > 0)
		{
			sm = new SystemMessage(SystemMessage.EQUIPPED__S1_S2);
			sm.addNumber(item.getEnchantLevel());
			sm.addItemName(item.getItemId());
		}
		else
			sm = new SystemMessage(SystemMessage.YOU_HAVE_EQUIPPED_YOUR_S1).addItemName(item.getItemId());

		player.sendPacket(sm);
		return true;
	}

	@Override
	public int[] getItemIds()
	{
		return _itemIds;
	}
}
