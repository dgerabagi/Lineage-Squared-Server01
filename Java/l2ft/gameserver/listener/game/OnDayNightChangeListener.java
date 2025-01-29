package l2ft.gameserver.listener.game;

import l2ft.gameserver.listener.GameListener;

public interface OnDayNightChangeListener extends GameListener
{
	public void onDay();

	public void onNight();
}
