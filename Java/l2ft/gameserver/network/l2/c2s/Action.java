package l2ft.gameserver.network.l2.c2s;

import l2ft.gameserver.model.GameObject;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.network.l2.components.SystemMsg;
import l2ft.gameserver.network.l2.s2c.ActionFail;

public class Action extends L2GameClientPacket {

	private int _objectId;
	private int _actionId; // 0 = simple click, 1 = SHIFT-click
	private int _originX, _originY, _originZ; // possibly from client if ground-click or so

	@Override
	protected void readImpl() {
		_objectId = readD();
		_originX = readD();
		_originY = readD();
		_originZ = readD();
		_actionId = readC(); // 0 or 1
	}

	@Override
	protected void runImpl() {
		Player activeChar = getClient().getActiveChar();
		if (activeChar == null)
			return;

		// Example standard checks
		if (activeChar.isOutOfControl() || activeChar.isInStoreMode() || activeChar.isFrozen()) {
			activeChar.sendActionFailed();
			return;
		}

		// Try to find the object in the world
		GameObject obj = activeChar.getVisibleObject(_objectId);
		if (obj == null) {
			activeChar.sendActionFailed();
			return;
		}

		// basic checks
		activeChar.setActive();

		if (activeChar.getAggressionTarget() != null && activeChar.getAggressionTarget() != obj) {
			activeChar.sendActionFailed();
			return;
		}

		if (activeChar.isLockedTarget()) {
			activeChar.sendActionFailed();
			return;
		}

		// ============= Normal or SHIFT usage? =============
		// We pass the boolean "shift" so onAction can differentiate
		obj.onAction(activeChar, (_actionId == 1));
	}
}
