package l2ft.gameserver.network.l2.c2s;

import l2ft.commons.util.Rnd;
import l2ft.gameserver.Config;
import l2ft.gameserver.data.xml.holder.SkillAcquireHolder;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.actor.instances.player.ShortCut;
import l2ft.gameserver.model.Skill;
import l2ft.gameserver.model.base.AcquireType;
import l2ft.gameserver.model.base.EnchantSkillLearn;
import l2ft.gameserver.network.l2.components.SystemMsg;
import l2ft.gameserver.network.l2.s2c.ExEnchantSkillInfo;
import l2ft.gameserver.network.l2.s2c.ExEnchantSkillResult;
import l2ft.gameserver.network.l2.s2c.ShortCutRegister;
import l2ft.gameserver.network.l2.s2c.SkillList;
import l2ft.gameserver.network.l2.s2c.SystemMessage;
import l2ft.gameserver.network.l2.s2c.SystemMessage2;
import l2ft.gameserver.scripts.Functions;
import l2ft.gameserver.tables.SkillTable;
import l2ft.gameserver.tables.SkillTreeTable;
import l2ft.gameserver.utils.Log;

public class RequestExEnchantSkill extends L2GameClientPacket
{
	private int _skillId;
	private int _skillLvl;

	@Override
	protected void readImpl()
	{
		_skillId = readD();
		_skillLvl = readD();
	}

	@Override
	protected void runImpl()
	{
		Player activeChar = getClient().getActiveChar();
		if(activeChar == null)
			return;

		if(activeChar.getTransformation() != 0)
		{
			activeChar.sendMessage("You must leave transformation mode first.");
			return;
		}

		if(activeChar.getLevel() < 76 || activeChar.getActiveClassClassId().getLevel() < 4)
			{
			activeChar.sendMessage("You must have 3rd class change quest completed.");
			return;
		}

		EnchantSkillLearn sl = SkillTreeTable.getSkillEnchant(_skillId, _skillLvl);
		if(sl == null)
			return;

		int slevel = activeChar.getSkillLevel(_skillId);
		if(slevel == -1)
			return;

		int enchantLevel = SkillTreeTable.convertEnchantLevel(sl.getBaseLevel(), _skillLvl, sl.getMaxLevel());

		// already knows the skill with this level
		if(slevel >= enchantLevel)
			return;

		// ĐśĐľĐ¶ĐµĐĽ Đ»Đ¸ ĐĽŃ‹ ĐżĐµŃ€ĐµĐąŃ‚Đ¸ Ń� Ń‚ĐµĐşŃ�Ń‰ĐµĐłĐľ Ń�Ń€ĐľĐ˛Đ˝ŃŹ Ń�ĐşĐ¸Đ»Đ»Đ° Đ˝Đ° Đ´Đ°Đ˝Đ˝Ń�ŃŽ Đ·Đ°Ń‚ĐľŃ‡ĐşŃ�
		if(slevel == sl.getBaseLevel() ? _skillLvl % 100 != 1 : slevel != enchantLevel - 1)
		{
			activeChar.sendMessage("Incorrect enchant level.");
			return;
		}

		Skill skill = SkillTable.getInstance().getInfo(_skillId, enchantLevel);
		if(skill == null)
		{
			activeChar.sendMessage("Internal error: not found skill level");
			return;
		}

		int[] cost = sl.getCost();
		int requiredSp = cost[1] * SkillTreeTable.NORMAL_ENCHANT_COST_MULTIPLIER * sl.getCostMult();
		int requiredAdena = cost[0] * SkillTreeTable.NORMAL_ENCHANT_COST_MULTIPLIER * sl.getCostMult();
		int rate = sl.getRate(activeChar);

		if(activeChar.getSp() < requiredSp)
		{
			activeChar.sendPacket(SystemMsg.YOU_DO_NOT_HAVE_ENOUGH_SP_TO_ENCHANT_THAT_SKILL);
			return;
		}

		if(activeChar.getAdena() < requiredAdena)
		{
			activeChar.sendPacket(SystemMsg.YOU_DO_NOT_HAVE_ENOUGH_ADENA);
			return;
		}

		if(_skillLvl % 100 == 1) // only first lvl requires book (101, 201, 301 ...)
		{
			if(Functions.getItemCount(activeChar, SkillTreeTable.NORMAL_ENCHANT_BOOK) == 0)
			{
				activeChar.sendPacket(SystemMsg.YOU_DO_NOT_HAVE_ALL_OF_THE_ITEMS_NEEDED_TO_ENCHANT_THAT_SKILL);
				return;
			}
			Functions.removeItem(activeChar, SkillTreeTable.NORMAL_ENCHANT_BOOK, 1);
		}

		if(Rnd.chance(rate))
		{
			activeChar.addExpAndSp(0, -1 * requiredSp);
			Functions.removeItem(activeChar, 57, requiredAdena);
			activeChar.sendPacket(new SystemMessage2(SystemMsg.YOUR_SP_HAS_DECREASED_BY_S1).addInteger(requiredSp), new SystemMessage2(SystemMsg.SKILL_ENCHANT_WAS_SUCCESSFUL_S1_HAS_BEEN_ENCHANTED).addSkillName(_skillId, _skillLvl), new SkillList(activeChar), new ExEnchantSkillResult(1));
			Log.add(activeChar.getName() + "|Successfully enchanted|" + _skillId + "|to+" + _skillLvl + "|" + rate, "enchant_skills");
			activeChar.addSkill(skill, true);
		}
		else
		{
			skill = SkillTable.getInstance().getInfo(_skillId, sl.getBaseLevel());
			activeChar.sendPacket(new SystemMessage(SystemMessage.FAILED_IN_ENCHANTING_SKILL_S1).addSkillName(_skillId, _skillLvl), new ExEnchantSkillResult(0));
			Log.add(activeChar.getName() + "|Failed to enchant|" + _skillId + "|to+" + _skillLvl + "|" + rate, "enchant_skills");
			if(Config.AUTO_LEARN_SKILLS && SkillAcquireHolder.getInstance().isSkillPossible(activeChar, skill, AcquireType.NORMAL))
				activeChar.removeSkillFromDB(skill, activeChar.getClassOfTheSkill(skill));
			activeChar.addSkill(skill, !Config.AUTO_LEARN_SKILLS);
		}
		updateSkillShortcuts(activeChar, _skillId, _skillLvl);
		activeChar.sendPacket(new ExEnchantSkillInfo(_skillId, activeChar.getSkillDisplayLevel(_skillId)));
	}

	protected static void updateSkillShortcuts(Player player, int skillId, int skillLevel)
	{
		for(ShortCut sc : player.getAllShortCuts())
			if(sc.getId() == skillId && sc.getType() == ShortCut.TYPE_SKILL)
			{
				ShortCut newsc = new ShortCut(sc.getSlot(), sc.getPage(), sc.getType(), sc.getId(), skillLevel, 1);
				player.sendPacket(new ShortCutRegister(player, newsc));
				player.registerShortCut(newsc);
			}
	}
}