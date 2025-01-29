package l2ft.gameserver.model.items.listeners;

import l2ft.gameserver.data.xml.holder.OptionDataHolder;
import l2ft.gameserver.listener.inventory.OnEquipListener;
import l2ft.gameserver.model.Playable;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.Skill;
import l2ft.gameserver.model.items.ItemInstance;
import l2ft.gameserver.network.l2.s2c.SkillList;
import l2ft.gameserver.stats.triggers.TriggerInfo;
import l2ft.gameserver.templates.OptionDataTemplate;

/**
 * @author VISTALL
 * @date 19:34/19.05.2011
 */
public final class ItemEnchantOptionsListener implements OnEquipListener
{
	private static final ItemEnchantOptionsListener _instance = new ItemEnchantOptionsListener();

	public static ItemEnchantOptionsListener getInstance()
	{
		return _instance;
	}

	@Override
	public void onEquip(int slot, ItemInstance item, Playable actor)
	{
		if(!item.isEquipable())
			return;
		Player player = actor.getPlayer();

		boolean needSendInfo = false;
		for(int i : item.getEnchantOptions())
		{
			OptionDataTemplate template = OptionDataHolder.getInstance().getTemplate(i);
			if(template == null)
				continue;

			player.addStatFuncs(template.getStatFuncs(template));
			for(Skill skill : template.getSkills())
			{
				player.addSkill(skill, false);
				needSendInfo = true;
			}
			for(TriggerInfo triggerInfo : template.getTriggerList())
				player.addTrigger(triggerInfo);
		}

		if(needSendInfo)
			player.sendPacket(new SkillList(player));
		player.sendChanges();
	}

	@Override
	public void onUnequip(int slot, ItemInstance item, Playable actor)
	{
		if(!item.isEquipable())
			return;

		Player player = actor.getPlayer();

		boolean needSendInfo = false;
		for(int i : item.getEnchantOptions())
		{
			OptionDataTemplate template = OptionDataHolder.getInstance().getTemplate(i);
			if(template == null)
				continue;

			player.removeStatsOwner(template);
			for(Skill skill : template.getSkills())
			{
				player.removeSkill(skill, false);
				needSendInfo = true;
			}
			for(TriggerInfo triggerInfo : template.getTriggerList())
				player.removeTrigger(triggerInfo);
		}

		if(needSendInfo)
			player.sendPacket(new SkillList(player));
		player.sendChanges();
	}
}
