package l2ft.gameserver.network.l2.c2s;

import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.Skill;
import l2ft.gameserver.model.base.EnchantSkillLearn;
import l2ft.gameserver.network.l2.components.SystemMsg;
import l2ft.gameserver.network.l2.s2c.ExEnchantSkillInfo;
import l2ft.gameserver.network.l2.s2c.ExEnchantSkillResult;
import l2ft.gameserver.network.l2.s2c.SkillList;
import l2ft.gameserver.network.l2.s2c.SystemMessage2;
import l2ft.gameserver.scripts.Functions;
import l2ft.gameserver.tables.SkillTable;
import l2ft.gameserver.tables.SkillTreeTable;
import l2ft.gameserver.utils.Log;

public final class RequestExEnchantSkillUntrain extends L2GameClientPacket
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

		int oldSkillLevel = activeChar.getSkillDisplayLevel(_skillId);
		if(oldSkillLevel == -1)
			return;

		if(_skillLvl != (oldSkillLevel - 1) || (_skillLvl / 100) != (oldSkillLevel / 100))
			return;

		EnchantSkillLearn sl = SkillTreeTable.getSkillEnchant(_skillId, oldSkillLevel);
		if(sl == null)
			return;

		Skill newSkill;

		if(_skillLvl % 100 == 0)
		{
			_skillLvl = sl.getBaseLevel();
			newSkill = SkillTable.getInstance().getInfo(_skillId, _skillLvl);
		}
		else
			newSkill = SkillTable.getInstance().getInfo(_skillId, SkillTreeTable.convertEnchantLevel(sl.getBaseLevel(), _skillLvl, sl.getMaxLevel()));

		if(newSkill == null)
			return;

		if(Functions.getItemCount(activeChar, SkillTreeTable.UNTRAIN_ENCHANT_BOOK) == 0)
		{
			activeChar.sendPacket(SystemMsg.YOU_DO_NOT_HAVE_ALL_OF_THE_ITEMS_NEEDED_TO_ENCHANT_THAT_SKILL);
			return;
		}

		Functions.removeItem(activeChar, SkillTreeTable.UNTRAIN_ENCHANT_BOOK, 1);

		activeChar.addExpAndSp(0, sl.getCost()[1] * sl.getCostMult());
		activeChar.addSkill(newSkill, true);

		if(_skillLvl > 100)
		{
			SystemMessage2 sm = new SystemMessage2(SystemMsg.UNTRAIN_OF_ENCHANT_SKILL_WAS_SUCCESSFUL);
			sm.addSkillName(_skillId, _skillLvl);
			activeChar.sendPacket(sm);
		}
		else
		{
			SystemMessage2 sm = new SystemMessage2(SystemMsg.UNTRAIN_OF_ENCHANT_SKILL_WAS_SUCCESSFUL_);
			sm.addSkillName(_skillId, _skillLvl);
			activeChar.sendPacket(sm);
		}

		Log.add(activeChar.getName() + "|Successfully untranes|" + _skillId + "|to+" + _skillLvl + "|---", "enchant_skills");

		activeChar.sendPacket(new ExEnchantSkillInfo(_skillId, newSkill.getDisplayLevel()), ExEnchantSkillResult.SUCCESS, new SkillList(activeChar));
		RequestExEnchantSkill.updateSkillShortcuts(activeChar, _skillId, _skillLvl);
	}
}