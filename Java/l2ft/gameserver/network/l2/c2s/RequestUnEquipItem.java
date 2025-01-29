package l2ft.gameserver.network.l2.c2s;

import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.items.ItemInstance;
import l2ft.gameserver.network.l2.components.SystemMsg;
import l2ft.gameserver.stats.Stats;
import l2ft.gameserver.templates.item.ItemTemplate;

//@Deprecated
public class RequestUnEquipItem extends L2GameClientPacket
{
	private int _slot;

	/**
	 * packet type id 0x16
	 * format:		cd
	 */
	@Override
	protected void readImpl()
	{
		_slot = readD();
	}

	@Override
	protected void runImpl()
	{
		Player activeChar = getClient().getActiveChar();
		if(activeChar == null)
			return;
		
		if(activeChar.isActionsDisabled())
		{
			activeChar.sendActionFailed();
			return;
		}

		// You cannot do anything else while fishing
		if(activeChar.isFishing())
		{
			activeChar.sendPacket(SystemMsg.YOU_CANNOT_DO_THAT_WHILE_FISHING_2);
			return;
		}
		if(activeChar.calcStat(Stats.REFLECT_AND_BLOCK_MSKILL_DAMAGE_CHANCE, 0, activeChar, null) > 0)
			if(_slot == ItemTemplate.SLOT_R_EAR || _slot == ItemTemplate.SLOT_L_EAR || _slot == ItemTemplate.SLOT_R_FINGER || _slot == ItemTemplate.SLOT_L_FINGER || _slot == ItemTemplate.SLOT_NECK)
				return;

		// Нельзя снимать проклятое оружие и флаги
		if((_slot == ItemTemplate.SLOT_R_HAND || _slot == ItemTemplate.SLOT_L_HAND || _slot == ItemTemplate.SLOT_LR_HAND) && (activeChar.isCursedWeaponEquipped() || activeChar.getActiveWeaponFlagAttachment() != null))
			return;

		if(_slot == ItemTemplate.SLOT_R_HAND)
		{
			ItemInstance weapon = activeChar.getActiveWeaponInstance();
			if(weapon == null)
				return;
			activeChar.abortAttack(true, true);
			activeChar.abortCast(true, true);
			activeChar.sendDisarmMessage(weapon);
		}

		activeChar.getInventory().unEquipItemInBodySlot(_slot);
	}
}