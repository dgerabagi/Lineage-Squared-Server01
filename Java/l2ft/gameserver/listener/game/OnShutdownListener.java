package l2ft.gameserver.listener.game;

import l2ft.gameserver.listener.GameListener;

public interface OnShutdownListener extends GameListener
{
	public void onShutdown();
}
