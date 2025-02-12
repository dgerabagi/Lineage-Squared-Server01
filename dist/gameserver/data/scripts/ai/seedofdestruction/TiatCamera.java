package ai.seedofdestruction;

import java.util.ArrayList;
import java.util.List;

import l2ft.gameserver.ai.DefaultAI;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.World;
import l2ft.gameserver.model.instances.NpcInstance;
import l2ft.gameserver.network.l2.s2c.ExStartScenePlayer;

public class TiatCamera extends DefaultAI
{
	private List<Player> _players = new ArrayList<Player>();

	public TiatCamera(NpcInstance actor)
	{
		super(actor);
		actor.startImmobilized();
		actor.startDamageBlocked();
	}

	@Override
	protected boolean thinkActive()
	{
		NpcInstance actor = getActor();
		for(Player p : World.getAroundPlayers(actor, 300, 300))
			if(!_players.contains(p))
			{
				p.showQuestMovie(ExStartScenePlayer.SCENE_TIAT_OPENING);
				_players.add(p);
			}
		return true;
	}
}