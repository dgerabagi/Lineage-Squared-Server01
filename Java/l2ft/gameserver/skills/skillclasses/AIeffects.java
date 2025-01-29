package l2ft.gameserver.skills.skillclasses;

import java.util.List;

import l2ft.gameserver.model.Creature;
import l2ft.gameserver.model.Skill;
import l2ft.gameserver.templates.StatsSet;

public class AIeffects extends Skill
{
	public AIeffects(StatsSet set)
	{
		super(set);
	}

	@Override
	public void useSkill(Creature activeChar, List<Creature> targets)
	{
		for(Creature target : targets)
			if(target != null)
				getEffects(activeChar, target, getActivateRate() > 0, false);

		if(isSSPossible())
			activeChar.unChargeShots(isMagic());
	}
}
