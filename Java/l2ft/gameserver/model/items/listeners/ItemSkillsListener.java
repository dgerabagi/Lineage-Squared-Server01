package l2ft.gameserver.model.items.listeners;

import l2ft.gameserver.listener.inventory.OnEquipListener;
import l2ft.gameserver.model.Playable;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.Skill;
import l2ft.gameserver.model.items.ItemInstance;
import l2ft.gameserver.network.l2.s2c.SkillCoolTime;
import l2ft.gameserver.network.l2.s2c.SkillList;
import l2ft.gameserver.stats.Formulas;
import l2ft.gameserver.templates.item.ItemTemplate;

public final class ItemSkillsListener implements OnEquipListener
{
	private static final ItemSkillsListener _instance = new ItemSkillsListener();

	public static ItemSkillsListener getInstance()
	{
		return _instance;
	}

	@Override
	public void onUnequip(int slot, ItemInstance item, Playable actor)
	{
		Player player = (Player)actor;

		Skill[] itemSkills = null;
		Skill enchant4Skill = null;

		ItemTemplate it = item.getTemplate();

		itemSkills = it.getAttachedSkills();

		enchant4Skill = it.getEnchant4Skill();

		player.removeTriggers(it);

		if(itemSkills != null && itemSkills.length > 0)
			for(Skill itemSkill : itemSkills)
					player.removeSkill(itemSkill, false);

		if(enchant4Skill != null)
			player.removeSkill(enchant4Skill, false);

		if(itemSkills.length > 0 || enchant4Skill != null)
		{
			player.sendPacket(new SkillList(player));
			player.updateStats();
		}
	}

	@Override
	public void onEquip(int slot, ItemInstance item, Playable actor)
	{
		Player player = (Player)actor;

		Skill[] itemSkills = null;
		Skill enchant4Skill = null;

		ItemTemplate it = item.getTemplate();

		itemSkills = it.getAttachedSkills();

		if(item.getEnchantLevel() >= 4)
			enchant4Skill = it.getEnchant4Skill();

		// Для оружия при несоотвествии грейда скилы не выдаем
		if(it.getType2() == ItemTemplate.TYPE2_WEAPON && player.getWeaponsExpertisePenalty() > 0)
			return;

		player.addTriggers(it);

		boolean needSendInfo = false;
		if(itemSkills.length > 0)
			for(Skill itemSkill : itemSkills)
				if(player.getSkillLevel(itemSkill.getId()) < itemSkill.getLevel())
				{
					player.addSkill(itemSkill, false);

					if(itemSkill.isActive())
					{
						long reuseDelay = Formulas.calcSkillReuseDelay(player, itemSkill);
						reuseDelay = Math.min(reuseDelay, 30000);

						if(reuseDelay > 0 && !player.isSkillDisabled(itemSkill))
						{
							player.disableSkill(itemSkill, reuseDelay);
							needSendInfo = true;
						}
					}
				}

		if(enchant4Skill != null)
			player.addSkill(enchant4Skill, false);

		if(itemSkills.length > 0 || enchant4Skill != null)
		{
			player.sendPacket(new SkillList(player));
			player.updateStats();
			if (needSendInfo)
				player.sendPacket(new SkillCoolTime(player));
		}
	}
}