package l2ft.gameserver.network.l2.c2s;

import l2ft.gameserver.Config;
import l2ft.gameserver.instancemanager.games.MiniGameScoreManager;
import l2ft.gameserver.model.Player;

public class RequestBR_MiniGameInsertScore extends L2GameClientPacket
{
	private int _score;

	@Override
	protected void readImpl() throws Exception
	{
		_score = readD();
	}

	@Override
	protected void runImpl() throws Exception
	{
		Player player = getClient().getActiveChar();
		if(player == null || !Config.EX_JAPAN_MINIGAME)
			return;

		MiniGameScoreManager.getInstance().insertScore(player, _score);
	}
}