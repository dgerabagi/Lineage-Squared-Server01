package ai;

import l2ft.gameserver.ai.DefaultAI;
import l2ft.gameserver.model.Creature;
import l2ft.gameserver.model.Skill;
import l2ft.gameserver.model.instances.NpcInstance;
import l2ft.gameserver.tables.SkillTable;

public class PowderKeg extends DefaultAI
{
	public PowderKeg(NpcInstance actor)
	{
		super(actor);
	}
	
	@Override
	protected void onEvtAttacked(Creature attacker, int damage)
	{
		Skill killSelfSkill = SkillTable.getInstance().getInfo(4139, 12);
		getActor().doCast(killSelfSkill, getActor(), true);
		super.onEvtAttacked(attacker, damage);
	}
}