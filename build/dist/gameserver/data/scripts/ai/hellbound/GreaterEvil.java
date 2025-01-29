package ai.hellbound;

import l2ft.gameserver.ai.Fighter;
import l2ft.gameserver.model.instances.NpcInstance;
import l2ft.gameserver.utils.Location;

public class GreaterEvil extends Fighter {
	/**
	 * This array defines the sequential "path" (waypoints) for the NPC to move
	 * along.
	 * After reaching the final point, the code causes the NPC to "doDie" and resets
	 * current_point back to 0 to avoid any out-of-bounds errors.
	 */
	private static final Location[] path = {
			new Location(28448, 243816, -3696),
			new Location(27624, 245256, -3696),
			new Location(27528, 246808, -3656),
			new Location(28296, 247912, -3248),
			new Location(25880, 246184, -3176)
	};

	private int current_point = 0;

	public GreaterEvil(NpcInstance actor) {
		super(actor);
		// Increase pursuit range if needed:
		MAX_PURSUE_RANGE = 6000;
	}

	@Override
	public boolean isGlobalAI() {
		return true;
	}

	/**
	 * This method is called periodically (from DefaultAI) when the NPC
	 * is in the ACTIVE intention. We use it to move along the waypoints
	 * or to perform tasks if the NPC is "thinking".
	 */
	@Override
	protected boolean thinkActive() {
		NpcInstance actor = getActor();
		if (actor.isDead())
			return true;

		// If the NPC is already "busy" with an AI task, just continue it.
		if (_def_think) {
			doTask();
			return true;
		}

		// Check if we've run out of valid path indices:
		if (current_point >= path.length) {
			// At this point, we can kill off the NPC or do something else:
			actor.doDie(null);
			current_point = 0;
			return true;
		}

		// Otherwise, move to the current waypoint:
		actor.setRunning(); // Speed can be adjusted here if desired.
		addTaskMove(path[current_point], false);
		doTask();
		return false;
	}

	/**
	 * Called whenever the NPC arrives at a location assigned by addTaskMove().
	 * We increment current_point here so that the next movement is to the next
	 * waypoint in the path.
	 */
	@Override
	protected void onEvtArrived() {
		// Once we've arrived, increment our path index:
		current_point++;
		// Let the superclass handle any standard arrival logic:
		super.onEvtArrived();
	}

	/**
	 * Disables returning to "home" in the usual DefaultAI sense, because we
	 * want to control exactly how this NPC moves along the path without
	 * interfering with random movement logic.
	 */
	@Override
	protected boolean maybeMoveToHome() {
		return false;
	}
}
