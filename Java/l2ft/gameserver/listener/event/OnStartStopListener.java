package l2ft.gameserver.listener.event;

import l2ft.gameserver.listener.EventListener;
import l2ft.gameserver.model.entity.events.GlobalEvent;

/**
 * @author VISTALL
 * @date 7:18/10.06.2011
 */
public interface OnStartStopListener extends EventListener
{
	void onStart(GlobalEvent event);

	void onStop(GlobalEvent event);
}
