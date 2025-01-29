package l2ft.gameserver.skills.effects;

import l2ft.gameserver.model.Effect;
import l2ft.gameserver.stats.Env;

public final class EffectBuff extends Effect
{
	public EffectBuff(Env env, EffectTemplate template)
	{
		super(env, template);
	}

	@Override
	public boolean onActionTime()
	{
		return false;
	}
}