package l2ft.gameserver.skills.skillclasses;

import java.util.List;

import l2ft.commons.util.Rnd;
import l2ft.gameserver.model.Creature;
import l2ft.gameserver.model.Skill;
import l2ft.gameserver.network.l2.components.SystemMsg;
import l2ft.gameserver.network.l2.s2c.SystemMessage2;
import l2ft.gameserver.stats.Formulas;
import l2ft.gameserver.stats.Stats;
import l2ft.gameserver.templates.StatsSet;


public class ManaDam extends Skill
{
	public ManaDam(StatsSet set)
	{
		super(set);
	}

	@Override
	public void useSkill(Creature activeChar, List<Creature> targets)
	{
		int sps = 0;
		if(isSSPossible())
			sps = activeChar.getChargedSpiritShot();

		for(Creature target : targets)
			if(target != null)
			{
				if(target.isDead())
					continue;

				int magicLevel = getMagicLevel() == 0 ? activeChar.getLevel() : getMagicLevel();
				int landRate = Rnd.get(30, 100);
				landRate *= target.getLevel();
				landRate /= magicLevel;

				if(Rnd.chance(landRate))
				{
					double mAtk = activeChar.getMAtk(target, this);
					if(sps == 2)
						mAtk *= 4;
					else if(sps == 1)
						mAtk *= 2;

					double mDef = target.getMDef(activeChar, this);
					if(mDef < 1.)
						mDef = 1.;

					double damage = Math.sqrt(mAtk) * this.getPower() * (target.getMaxMp() / 97) / mDef;

					boolean crit = Formulas.calcMCrit(activeChar.getMagicCriticalRate(target, this));
					if(crit)
					{
						activeChar.sendPacket(SystemMsg.MAGIC_CRITICAL_HIT);
						damage *= activeChar.calcStat(Stats.MCRITICAL_DAMAGE, activeChar.isPlayable() && target.isPlayable() ? 2.5 : 3., target, this);
					}
					
					activeChar.sendMessage(Math.round(damage)+" mana was burned!");
					target.reduceCurrentMp(damage, activeChar);
				}
				else
				{
					SystemMessage2 msg = new SystemMessage2(SystemMsg.C1_RESISTED_C2S_MAGIC).addName(target).addName(activeChar);
					activeChar.sendPacket(msg);
					target.sendPacket(msg);
					target.reduceCurrentHp(1., activeChar, this, true, true, false, true, false, false, true);
				}

				getEffects(activeChar, target, getActivateRate() > 0, false);
			}

		if(isSSPossible())
			activeChar.unChargeShots(isMagic());
	}
}