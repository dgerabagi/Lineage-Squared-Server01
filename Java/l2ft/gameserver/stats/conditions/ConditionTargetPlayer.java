package l2ft.gameserver.stats.conditions;

import l2ft.gameserver.model.Creature;
import l2ft.gameserver.stats.Env;

public class ConditionTargetPlayer extends Condition
{
	private final boolean _flag;

	public ConditionTargetPlayer(boolean flag)
	{
		_flag = flag;
	}

	@Override
	protected boolean testImpl(Env env)
	{
		Creature target = env.target;
		return target != null && target.isPlayer() == _flag;
	}
}
