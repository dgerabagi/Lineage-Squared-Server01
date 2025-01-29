package handler.bypass;

import l2ft.gameserver.handler.bypass.BypassHandler;
import l2ft.gameserver.handler.bypass.IBypassHandler;
import l2ft.gameserver.scripts.ScriptFile;

public abstract class ScriptBypassHandler implements ScriptFile, IBypassHandler
{
	@Override
	public void onLoad()
	{
		BypassHandler.getInstance().registerBypass(this);
	}

	@Override
	public void onReload()
	{

	}

	@Override
	public void onShutdown()
	{

	}
}
