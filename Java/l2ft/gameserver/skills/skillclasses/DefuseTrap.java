package l2ft.gameserver.skills.skillclasses;

import java.util.List;

import l2ft.gameserver.model.Creature;
import l2ft.gameserver.model.Skill;
import l2ft.gameserver.model.instances.TrapInstance;
import l2ft.gameserver.network.l2.components.SystemMsg;
import l2ft.gameserver.templates.StatsSet;


public class DefuseTrap extends Skill
{
	public DefuseTrap(StatsSet set)
	{
		super(set);
	}

	@Override
	public boolean checkCondition(Creature activeChar, Creature target, boolean forceUse, boolean dontMove, boolean first)
	{
		if(target == null || !target.isTrap())
		{
			activeChar.sendPacket(SystemMsg.INVALID_TARGET);
			return false;
		}

		return super.checkCondition(activeChar, target, forceUse, dontMove, first);
	}

	@Override
	public void useSkill(Creature activeChar, List<Creature> targets)
	{
		for(Creature target : targets)
			if(target != null && target.isTrap())
			{

				TrapInstance trap = (TrapInstance) target;
				if(trap.getLevel() <= getPower())
					trap.deleteMe();
			}

		if(isSSPossible())
			activeChar.unChargeShots(isMagic());
	}
}