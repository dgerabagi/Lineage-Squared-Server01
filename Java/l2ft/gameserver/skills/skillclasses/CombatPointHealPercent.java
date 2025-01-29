package l2ft.gameserver.skills.skillclasses;

import java.util.List;

import l2ft.gameserver.model.Creature;
import l2ft.gameserver.model.Skill;
import l2ft.gameserver.network.l2.components.SystemMsg;
import l2ft.gameserver.network.l2.s2c.SystemMessage2;
import l2ft.gameserver.stats.Stats;
import l2ft.gameserver.templates.StatsSet;


public class CombatPointHealPercent extends Skill
{
	private final boolean _ignoreCpEff;

	public CombatPointHealPercent(StatsSet set)
	{
		super(set);
		_ignoreCpEff = set.getBool("ignoreCpEff", false);
	}

	@Override
	public void useSkill(Creature activeChar, List<Creature> targets)
	{
		for(Creature target : targets)
			if(target != null)
			{
				if(target.isDead() || target.isHealBlocked())
					continue;
				double cp = _power * target.getMaxCp() / 100.;
				double maxNewCp = cp * (!_ignoreCpEff ? target.calcStat(Stats.CPHEAL_EFFECTIVNESS, 100., activeChar, this) : 100.) / 100.;
				double addToCp = Math.max(0, Math.min(maxNewCp, target.calcStat(Stats.CP_LIMIT, null, null) * target.getMaxCp() / 100. - target.getCurrentCp()));
				
				if(addToCp > 0)
					target.setCurrentCp(addToCp + target.getCurrentCp());
				target.sendPacket(new SystemMessage2(SystemMsg.S1_CP_HAS_BEEN_RESTORED).addInteger((long) addToCp));
				getEffects(activeChar, target, getActivateRate() > 0, false);
			}
		if(isSSPossible())
			activeChar.unChargeShots(isMagic());
	}
}
