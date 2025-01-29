package l2ft.gameserver.listener.actor.ai;

import l2ft.gameserver.ai.CtrlIntention;
import l2ft.gameserver.listener.AiListener;
import l2ft.gameserver.model.Creature;

public interface OnAiIntentionListener extends AiListener
{
	public void onAiIntention(Creature actor, CtrlIntention intention, Object arg0, Object arg1);
}
