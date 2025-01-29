package l2ft.gameserver.skills.skillclasses;

import java.util.List;

import l2ft.gameserver.model.Creature;
import l2ft.gameserver.model.Skill;
import l2ft.gameserver.stats.Formulas;
import l2ft.gameserver.stats.Formulas.AttackInfo;
import l2ft.gameserver.templates.StatsSet;


public class LethalShot extends Skill
{
	public LethalShot(StatsSet set)
	{
		super(set);
	}

	@Override
	public void useSkill(Creature activeChar, List<Creature> targets)
	{
		boolean ss = activeChar.getChargedSoulShot() && isSSPossible();
		if(ss)
			activeChar.unChargeShots(false);

		Creature realTarget;
		boolean reflected;

		for(Creature target : targets)
			if(target != null)
			{
				if(target.isDead())
					continue;

				reflected = target.checkReflectSkill(activeChar, this);
				realTarget = reflected ? activeChar : target;

				if(getPower() > 0) // Если == 0 значит скилл "отключен"
				{
					AttackInfo info = Formulas.calcPhysDam(activeChar, realTarget, this, false, false, ss, false);

					if (info.lethal_dmg > 0)
						realTarget.reduceCurrentHp(info.lethal_dmg, activeChar, this, true, true, false, false, false, false, false);

					realTarget.reduceCurrentHp(info.damage, activeChar, this, true, true, false, true, false, false, true);
					if(!reflected)
						realTarget.doCounterAttack(this, activeChar, false);
				}

				getEffects(activeChar, target, getActivateRate() > 0, false, reflected);
			}
	}
}
