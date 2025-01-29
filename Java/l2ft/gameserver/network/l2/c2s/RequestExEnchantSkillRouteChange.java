package l2ft.gameserver.network.l2.c2s;

import l2ft.commons.util.Rnd;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.Skill;
import l2ft.gameserver.model.base.EnchantSkillLearn;
import l2ft.gameserver.network.l2.components.SystemMsg;
import l2ft.gameserver.network.l2.s2c.ExEnchantSkillInfo;
import l2ft.gameserver.network.l2.s2c.ExEnchantSkillResult;
import l2ft.gameserver.network.l2.s2c.SystemMessage2;
import l2ft.gameserver.scripts.Functions;
import l2ft.gameserver.tables.SkillTable;
import l2ft.gameserver.tables.SkillTreeTable;
import l2ft.gameserver.utils.Log;

public final class RequestExEnchantSkillRouteChange extends L2GameClientPacket
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

		int slevel = activeChar.getSkillDisplayLevel(_skillId);
		if(slevel == -1)
			return;

		if(slevel <= sl.getBaseLevel() || slevel % 100 != _skillLvl % 100)
			return;

		int[] cost = sl.getCost();
		int requiredSp = cost[1] * sl.getCostMult() / SkillTreeTable.SAFE_ENCHANT_COST_MULTIPLIER;
		int requiredAdena = cost[0] * sl.getCostMult() / SkillTreeTable.SAFE_ENCHANT_COST_MULTIPLIER;

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

		if(Functions.getItemCount(activeChar, SkillTreeTable.CHANGE_ENCHANT_BOOK) == 0)
		{
			activeChar.sendPacket(SystemMsg.YOU_DO_NOT_HAVE_ALL_OF_THE_ITEMS_NEEDED_TO_ENCHANT_THAT_SKILL);
			return;
		}

		Functions.removeItem(activeChar, SkillTreeTable.CHANGE_ENCHANT_BOOK, 1);
		Functions.removeItem(activeChar, 57, requiredAdena);
		activeChar.addExpAndSp(0, -1 * requiredSp);

		int levelPenalty = Rnd.get(Math.min(4, _skillLvl % 100));

		_skillLvl -= levelPenalty;
		if(_skillLvl % 100 == 0)
			_skillLvl = sl.getBaseLevel();

		Skill skill = SkillTable.getInstance().getInfo(_skillId, SkillTreeTable.convertEnchantLevel(sl.getBaseLevel(), _skillLvl, sl.getMaxLevel()));

		if(skill != null)
			activeChar.addSkill(skill, true);

		if(levelPenalty == 0)
		{
			SystemMessage2 sm = new SystemMessage2(SystemMsg.S1S_AUCTION_HAS_ENDED);
			sm.addSkillName(_skillId, _skillLvl);
			activeChar.sendPacket(sm);
		}
		else
		{
			SystemMessage2 sm = new SystemMessage2(SystemMsg.S1S2S_AUCTION_HAS_ENDED);
			sm.addSkillName(_skillId, _skillLvl);
			sm.addInteger(levelPenalty);
			activeChar.sendPacket(sm);
		}

		Log.add(activeChar.getName() + "|Successfully changed route|" + _skillId + "|" + slevel + "|to+" + _skillLvl + "|" + levelPenalty, "enchant_skills");

		activeChar.sendPacket(new ExEnchantSkillInfo(_skillId, activeChar.getSkillDisplayLevel(_skillId)), new ExEnchantSkillResult(1));
		RequestExEnchantSkill.updateSkillShortcuts(activeChar, _skillId, _skillLvl);
	}
}