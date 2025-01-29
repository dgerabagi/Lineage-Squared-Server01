package l2ft.gameserver.network.l2.c2s;

import l2ft.gameserver.Config;
import l2ft.gameserver.autofarm.PathData;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.network.l2.components.SystemMsg;
import l2ft.gameserver.network.l2.s2c.ActionFail;
import l2ft.gameserver.network.l2.s2c.CharMoveToLocation;
import l2ft.gameserver.utils.Location;

/**
 * This packet is sent whenever a user clicks on the ground (non-object) to
 * move.
 * The user can hold SHIFT or not, but the standard client rarely sends SHIFT=1
 * here (only modded clients do).
 */
public class MoveBackwardToLocation extends L2GameClientPacket {

	private Location _targetLoc = new Location();
	private Location _originLoc = new Location();
	private int _moveMovement; // some clients call this "Shift" or "MoveMovement" flag

	@Override
	protected void readImpl() {
		_targetLoc.x = readD();
		_targetLoc.y = readD();
		_targetLoc.z = readD();
		_originLoc.x = readD();
		_originLoc.y = readD();
		_originLoc.z = readD();

		if (_buf.hasRemaining()) {
			_moveMovement = readD();
		} else {
			_moveMovement = 0;
		}
	}

	@Override
	protected void runImpl() {
		Player activeChar = getClient().getActiveChar();
		if (activeChar == null)
			return;

		// SHIFT-click?
		boolean shiftClicked = (_moveMovement != 0);

		// 1) Standard movement checks
		activeChar.setActive();

		if (System.currentTimeMillis() - activeChar.getLastMovePacket() < Config.MOVE_PACKET_DELAY) {
			activeChar.sendActionFailed();
			return;
		}
		activeChar.setLastMovePacket();

		if (activeChar.isTeleporting()) {
			activeChar.sendActionFailed();
			return;
		}

		if (activeChar.isFrozen()) {
			activeChar.sendPacket(SystemMsg.YOU_CANNOT_MOVE_WHILE_FROZEN, ActionFail.STATIC);
			return;
		}

		if (activeChar.isInObserverMode()) {
			if (activeChar.getOlympiadObserveGame() == null)
				activeChar.sendActionFailed();
			else
				activeChar.sendPacket(new CharMoveToLocation(activeChar.getObjectId(), _originLoc, _targetLoc));
			return;
		}

		if (activeChar.isOutOfControl()) {
			activeChar.sendActionFailed();
			return;
		}

		if (activeChar.getTeleMode() > 0) {
			if (activeChar.getTeleMode() == 1)
				activeChar.setTeleMode(0);
			activeChar.sendActionFailed();
			activeChar.teleToLocation(_targetLoc);
			return;
		}

		if (activeChar.isInFlyingTransform()) {
			// clamp Z if flying transform
			_targetLoc.z = Math.min(5950, Math.max(50, _targetLoc.z));
		}

		// 2) Check design mode
		boolean isDesignModeActive = handleDesignModeClick(activeChar, _targetLoc, shiftClicked);
		if (isDesignModeActive) {
			// skip normal movement => locked in place
			activeChar.sendActionFailed();
			return;
		}

		// 3) Normal movement
		boolean usePathFind = (shiftClicked && !activeChar.getVarB("no_pf"));
		activeChar.moveToLocation(_targetLoc, 0, usePathFind);
	}

	/**
	 * If ANY path is in designMode, add a point if SHIFT was pressed.
	 * Return true => we block normal movement so the char remains stationary.
	 */
	private boolean handleDesignModeClick(Player player, Location target, boolean shiftClicked) {
		l2ft.gameserver.autofarm.AutoFarmState st = player.getAutoFarmState();
		if (st == null)
			return false;

		PathData pd = PathData.get(st);
		if (pd == null)
			return false;

		boolean foundDesignMode = false;

		for (String pathName : pd.getAllPathNames()) {
			if (pd.isDesignMode(pathName)) {
				foundDesignMode = true;

				if (!shiftClicked) {
					// user didn't SHIFT => block movement but do not create a point
					break;
				}

				// SHIFT => add a new coordinate
				int x = target.x;
				int y = target.y;
				int z = target.z;
				pd.addPoint(pathName, x, y, z);

				// Re-render lines (in cyan for design mode or red/green if selected)
				boolean isRunning = l2ft.gameserver.autofarm.AutoFarmEngine.getInstance().isAutoFarming(player);
				pd.renderAllPaths(player, isRunning);

				player.sendMessage("Added design point (" + x + "," + y + "," + z + ") to path: " + pathName);

				// Update DB
				l2ft.gameserver.model.ManageBbsPaths.PathRecord rec = l2ft.gameserver.model.ManageBbsPaths.getInstance()
						.getPath(player.getObjectId(), pathName);
				if (rec != null) {
					rec.points.add(new l2ft.gameserver.utils.Location(x, y, z));
					l2ft.gameserver.dao.CommunityPathsDAO.getInstance().update(rec.charId,
							rec.pathName,
							rec.points,
							rec.designMode);
				}

				// optionally: forcibly refresh the design page
				l2ft.gameserver.autofarm.AutoFarmPathEditorLogic
						.getInstance().showDesignPage(player, pd, pathName);

				break; // handle only the first designMode path we find
			}
		}

		return foundDesignMode;
	}
}
