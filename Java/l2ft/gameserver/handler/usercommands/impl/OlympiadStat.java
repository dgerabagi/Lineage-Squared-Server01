// C:/l2sq/Pac Project/Java/l2ft/gameserver/handler/usercommands/impl/OlympiadStat.java
package l2ft.gameserver.handler.usercommands.impl;

import l2ft.gameserver.Config;
import l2ft.gameserver.handler.usercommands.IUserCommandHandler;
import l2ft.gameserver.model.GameObject;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.entity.olympiad.Olympiad;
import l2ft.gameserver.network.l2.components.SystemMsg;
import l2ft.gameserver.network.l2.s2c.SystemMessage2;

/**
 * Support for /olympiadstat command
 */
public class OlympiadStat implements IUserCommandHandler {
	private static final int[] COMMAND_IDS = { 109 };

	@Override
	public boolean useUserCommand(int id, Player activeChar) {
		if (id != COMMAND_IDS[0])
			return false;

		GameObject objectTarget = Config.OLYMPIAD_OLDSTYLE_STAT ? activeChar : activeChar.getTarget();
		if (objectTarget == null || !objectTarget.isPlayer() || !objectTarget.getPlayer().isNoble()) {
			activeChar.sendPacket(SystemMsg.THIS_COMMAND_CAN_ONLY_BE_USED_BY_A_NOBLESSE);
			return true;
		}

		Player playerTarget = objectTarget.getPlayer();
		int combination = Olympiad.getClassCombination(playerTarget);
		SystemMessage2 sm = new SystemMessage2(
				SystemMsg.FOR_THE_CURRENT_GRAND_OLYMPIAD_YOU_HAVE_PARTICIPATED_IN_S1_MATCHES_S2_WINS_S3_DEFEATS_YOU_CURRENTLY_HAVE_S4_OLYMPIAD_POINTS);
		sm.addInteger(Olympiad.getCompetitionDone(playerTarget.getObjectId(), combination));
		sm.addInteger(Olympiad.getCompetitionWin(playerTarget.getObjectId(), combination));
		sm.addInteger(Olympiad.getCompetitionLoose(playerTarget.getObjectId(), combination));
		sm.addInteger(Olympiad.getNoblePoints(playerTarget.getObjectId(), combination));

		activeChar.sendPacket(sm);

		int[] ar = Olympiad.getWeekGameCounts(playerTarget.getObjectId());
		activeChar.sendMessage("You have " + ar[0] + " matches remaining that you can participate in this week ("
				+ ar[1] + " 2vs2 Matches, " + ar[2] + " 4vs4 matches, & " + ar[3] + " 6vs6 matches)");
		return true;
	}

	@Override
	public int[] getUserCommandList() {
		return COMMAND_IDS;
	}
}
// EOF C:/l2sq/Pac
// Project/Java/l2ft/gameserver/handler/usercommands/impl/OlympiadStat.java