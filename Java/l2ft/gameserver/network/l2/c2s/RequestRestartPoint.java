package l2ft.gameserver.network.l2.c2s;

import org.apache.commons.lang3.tuple.Pair;
import l2ft.commons.lang.ArrayUtils;
import l2ft.gameserver.instancemanager.ReflectionManager;
import l2ft.gameserver.listener.actor.player.OnAnswerListener;
import l2ft.gameserver.listener.actor.player.impl.ReviveAnswerListener;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.base.RestartType;
import l2ft.gameserver.model.entity.Reflection;
import l2ft.gameserver.model.entity.events.GlobalEvent;
import l2ft.gameserver.model.entity.events.impl.ForgottenBattlegroundsEvent;
import l2ft.gameserver.model.entity.events.impl.IslandAssaultEvent;
import l2ft.gameserver.model.entity.residence.Castle;
import l2ft.gameserver.model.entity.residence.ClanHall;
import l2ft.gameserver.model.entity.residence.Fortress;
import l2ft.gameserver.model.entity.residence.ResidenceFunction;
import l2ft.gameserver.model.pledge.Clan;
import l2ft.gameserver.network.l2.components.SystemMsg;
import l2ft.gameserver.network.l2.s2c.ActionFail;
import l2ft.gameserver.network.l2.s2c.Die;
import l2ft.gameserver.utils.ItemFunctions;
import l2ft.gameserver.utils.Location;
import l2ft.gameserver.utils.TeleportUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RequestRestartPoint extends L2GameClientPacket {

	private static final Logger _log = LoggerFactory.getLogger(RequestRestartPoint.class);
	private RestartType _restartType;

	@Override
	protected void readImpl() {
		_restartType = ArrayUtils.valid(RestartType.VALUES, readD());
		if (_restartType == null)
			_restartType = RestartType.AGATHION;
	}

	@Override
	protected void runImpl() {
		Player activeChar = getClient().getActiveChar();

		if (activeChar == null)
			return;

		if (activeChar.isFakeDeath()) {
			activeChar.breakFakeDeath();
			return;
		}

		if (!activeChar.isDead() && !activeChar.isGM()) {
			activeChar.sendActionFailed();
			return;
		}

		if (activeChar.isFestivalParticipant()) {
			activeChar.doRevive();
			return;
		}

		// 1) EXTRA LOGGING
		_log.info("RequestRestartPoint.java: Player " + activeChar.getName() +
				" reflectionId=" + activeChar.getReflectionId() +
				", restartType=" + _restartType);

		// 2) CHECK IF PLAYER IS IN FORGOTTENBATTLEGROUNDS EVENT
		ForgottenBattlegroundsEvent fgbEvent = activeChar.getEvent(ForgottenBattlegroundsEvent.class);
		if (fgbEvent != null && _restartType == RestartType.TO_VILLAGE) {
			_log.info("RequestRestartPoint.java: BLOCKING To Village for player " +
					activeChar.getName() + " because they're in ForgottenBattlegroundsEvent!");
			activeChar.sendMessage("You cannot use To Village while inside the Forgotten Battlegrounds!");
			activeChar.sendPacket(ActionFail.STATIC, new Die(activeChar));
			return;
		}

		// NEW - BLOCK if in IslandAssault
		IslandAssaultEvent iaEvent = activeChar.getEvent(IslandAssaultEvent.class);
		if (iaEvent != null && _restartType == RestartType.TO_VILLAGE) {
			_log.info("RequestRestartPoint.java: BLOCKING To Village for player " +
					activeChar.getName() + " because they're in IslandAssaultEvent!");
			activeChar.sendMessage("You cannot use To Village while inside the Island Assault!");
			activeChar.sendPacket(ActionFail.STATIC, new Die(activeChar));
			return;
		}

		switch (_restartType) {
			case AGATHION:
				if (activeChar.isAgathionResAvailable())
					activeChar.doRevive(100);
				else
					activeChar.sendPacket(ActionFail.STATIC, new Die(activeChar));
				break;
			case FIXED:
				if (activeChar.getPlayerAccess().ResurectFixed)
					activeChar.doRevive(100);
				else if (ItemFunctions.removeItem(activeChar, 13300, 1, true) == 1) {
					activeChar.sendPacket(SystemMsg.YOU_HAVE_USED_THE_FEATHER_OF_BLESSING_TO_RESURRECT);
					activeChar.doRevive(100);
				} else if (ItemFunctions.removeItem(activeChar, 10649, 1, true) == 1) {
					activeChar.sendPacket(SystemMsg.YOU_HAVE_USED_THE_FEATHER_OF_BLESSING_TO_RESURRECT);
					activeChar.doRevive(100);
				} else
					activeChar.sendPacket(ActionFail.STATIC, new Die(activeChar));
				break;
			default:
				Location loc = null;
				Reflection ref = activeChar.getReflection();

				if (ref == ReflectionManager.DEFAULT)
					for (GlobalEvent e : activeChar.getEvents())
						loc = e.getRestartLoc(activeChar, _restartType);

				if (loc == null)
					loc = defaultLoc(_restartType, activeChar);

				if (loc != null) {
					Pair<Integer, OnAnswerListener> ask = activeChar.getAskListener(false);
					if (ask != null && ask.getValue() instanceof ReviveAnswerListener
							&& !((ReviveAnswerListener) ask.getValue()).isForPet())
						activeChar.getAskListener(true);

					activeChar.setPendingRevive(true);
					activeChar.teleToLocation(loc, ReflectionManager.DEFAULT);
				} else
					activeChar.sendPacket(ActionFail.STATIC, new Die(activeChar));
				break;
		}
	}

	// FIXME [VISTALL] вынести куда то?
	// телепорт к флагу, не обрабатывается, по дефалту
	public static Location defaultLoc(RestartType restartType, Player activeChar) {
		Location loc = null;
		Clan clan = activeChar.getClan();

		switch (restartType) {
			case TO_CLANHALL:
				if (clan != null && clan.getHasHideout() != 0) {
					ClanHall clanHall = activeChar.getClanHall();
					loc = TeleportUtils.getRestartLocation(activeChar, RestartType.TO_CLANHALL);
					if (clanHall.getFunction(ResidenceFunction.RESTORE_EXP) != null)
						activeChar.restoreExp(clanHall.getFunction(ResidenceFunction.RESTORE_EXP).getLevel());
				}
				break;
			case TO_CASTLE:
				if (clan != null && clan.getCastle() != 0) {
					Castle castle = activeChar.getCastle();
					loc = TeleportUtils.getRestartLocation(activeChar, RestartType.TO_CASTLE);
					if (castle.getFunction(ResidenceFunction.RESTORE_EXP) != null)
						activeChar.restoreExp(castle.getFunction(ResidenceFunction.RESTORE_EXP).getLevel());
				}
				break;
			case TO_FORTRESS:
				if (clan != null && clan.getHasFortress() != 0) {
					Fortress fort = activeChar.getFortress();
					loc = TeleportUtils.getRestartLocation(activeChar, RestartType.TO_FORTRESS);
					if (fort.getFunction(ResidenceFunction.RESTORE_EXP) != null)
						activeChar.restoreExp(fort.getFunction(ResidenceFunction.RESTORE_EXP).getLevel());
				}
				break;
			case TO_VILLAGE:
			default:
				loc = TeleportUtils.getRestartLocation(activeChar, RestartType.TO_VILLAGE);
				break;
		}
		return loc;
	}
}
