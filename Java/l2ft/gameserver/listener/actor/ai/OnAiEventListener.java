package l2ft.gameserver.listener.actor.ai;

import l2ft.gameserver.ai.CtrlEvent;
import l2ft.gameserver.listener.AiListener;
import l2ft.gameserver.model.Creature;

public interface OnAiEventListener extends AiListener
{
	public void onAiEvent(Creature actor, CtrlEvent evt, Object[] args);
}
