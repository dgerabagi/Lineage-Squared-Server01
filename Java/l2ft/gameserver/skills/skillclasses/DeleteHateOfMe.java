package l2ft.gameserver.skills.skillclasses;

import java.util.List;

import l2ft.gameserver.ai.CtrlIntention;
import l2ft.gameserver.model.Creature;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.Skill;
import l2ft.gameserver.model.instances.NpcInstance;
import l2ft.gameserver.network.l2.components.CustomMessage;
import l2ft.gameserver.stats.Formulas;
import l2ft.gameserver.templates.StatsSet;


public class DeleteHateOfMe extends Skill
{
	public DeleteHateOfMe(StatsSet set)
	{
		super(set);
	}

	@Override
	public void useSkill(Creature activeChar, List<Creature> targets)
	{
		for(Creature target : targets)
			if(target != null)
			{
				if(activeChar.isPlayer() && ((Player) activeChar).isGM())
					activeChar.sendMessage(new CustomMessage("l2ft.gameserver.skills.Formulas.Chance", (Player)activeChar).addString(getName()).addNumber(getActivateRate()));
				if(Formulas.calcSkillSuccess(activeChar, target, this, getActivateRate()))
				{
					if(target.isNpc())
					{
						NpcInstance npc = (NpcInstance) target;
						npc.getAggroList().remove(activeChar, true);
						npc.getAI().setIntention(CtrlIntention.AI_INTENTION_ACTIVE);
					}
					getEffects(activeChar, target, false, false);
				}
			}
	}
}