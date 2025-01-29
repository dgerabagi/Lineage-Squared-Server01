package l2ft.gameserver.skills.skillclasses;

import java.util.List;

import l2ft.commons.threading.RunnableImpl;
import l2ft.gameserver.ThreadPoolManager;
import l2ft.gameserver.model.Creature;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.Skill;
import l2ft.gameserver.model.instances.FeedableBeastInstance;
import l2ft.gameserver.templates.StatsSet;


public class BeastFeed extends Skill
{
	public BeastFeed(StatsSet set)
	{
		super(set);
	}

	@Override
	public void useSkill(final Creature activeChar, List<Creature> targets)
	{
		for(final Creature target : targets)
		{
			ThreadPoolManager.getInstance().execute(new RunnableImpl()
			{
				@Override
				public void runImpl() throws Exception
				{
					if(target instanceof FeedableBeastInstance)
						((FeedableBeastInstance) target).onSkillUse((Player) activeChar, _id);
				}
			});
		}
	}
}
