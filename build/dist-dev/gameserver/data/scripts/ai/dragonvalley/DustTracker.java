package ai.dragonvalley;

import l2ft.gameserver.model.instances.NpcInstance;
import l2ft.gameserver.utils.Location;

public class DustTracker extends Patrollers
{
	public DustTracker(NpcInstance actor)
	{
		super(actor);
		_points = new Location[]{new Location(113544, 109976, -2992),
				new Location(111400, 110632, -3040),
				new Location(109912, 112040, -3232),
				new Location(109432, 113784, -3056),
				new Location(109368, 115896, -3104),
				new Location(108728, 117880, -3024),
				new Location(109368, 115896, -3104),
				new Location(109432, 113784, -3056),
				new Location(109912, 112040, -3232),
				new Location(111400, 110632, -3040),
				new Location(113544, 109976, -2992)};
	}
}
