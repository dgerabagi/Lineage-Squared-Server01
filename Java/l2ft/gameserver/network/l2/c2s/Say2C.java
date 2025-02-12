package l2ft.gameserver.network.l2.c2s;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import l2ft.gameserver.Config;
import l2ft.gameserver.cache.ItemInfoCache;
import l2ft.gameserver.handler.voicecommands.IVoicedCommandHandler;
import l2ft.gameserver.handler.voicecommands.VoicedCommandHandler;
import l2ft.gameserver.instancemanager.PetitionManager;
import l2ft.gameserver.model.GameObjectsStorage;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.World;
import l2ft.gameserver.model.entity.olympiad.OlympiadGame;
import l2ft.gameserver.model.items.ItemInstance;
import l2ft.gameserver.model.matching.MatchingRoom;
import l2ft.gameserver.network.l2.components.ChatType;
import l2ft.gameserver.network.l2.components.CustomMessage;
import l2ft.gameserver.network.l2.components.SystemMsg;
import l2ft.gameserver.network.l2.s2c.ActionFail;
import l2ft.gameserver.network.l2.s2c.Say2;
import l2ft.gameserver.network.l2.s2c.SystemMessage2;
import l2ft.gameserver.taskmanager.tasks.TaskVariable.TaskType;
import l2ft.gameserver.utils.Log;
import l2ft.gameserver.utils.MapUtils;
import l2ft.gameserver.utils.Strings;
import l2ft.gameserver.utils.Util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.graphbuilder.math.Expression;
import com.graphbuilder.math.ExpressionParseException;
import com.graphbuilder.math.ExpressionTree;
import com.graphbuilder.math.VarMap;

public class Say2C extends L2GameClientPacket {
	private static final Logger _log = LoggerFactory.getLogger(Say2C.class);

	/**
	 * RegExp for caching item links, e.g.: \b\tType=1 \tID=268484598 \tColor=0
	 * \tUnderline=0 \tTitle=\u001BAdena\u001B\b
	 */
	private static final Pattern EX_ITEM_LINK_PATTERN = Pattern.compile(
			"[\b]\tType=[0-9]+[\\s]+\tID=([0-9]+)[\\s]+\tColor=[0-9]+[\\s]+\tUnderline=[0-9]+[\\s]+\tTitle=\u001B(.[^\u001B]*)[^\b]");
	private static final Pattern SKIP_ITEM_LINK_PATTERN = Pattern.compile(
			"[\b]\tType=[0-9]+(.[^\b]*)[\b]");

	private String _text;
	private ChatType _type;
	private String _target;

	@Override
	protected void readImpl() {
		_text = readS(Config.CHAT_MESSAGE_MAX_LEN);
		_type = l2ft.commons.lang.ArrayUtils.valid(ChatType.VALUES, readD());
		_target = (_type == ChatType.TELL) ? readS(Config.CNAME_MAXLEN) : null;
	}

	@Override
	protected void runImpl() {
		Player activeChar = getClient().getActiveChar();
		if (activeChar == null)
			return;

		if (_type == null || _text == null || _text.length() == 0) {
			activeChar.sendActionFailed();
			return;
		}

		// Replace "\n" if present
		_text = _text.replaceAll("\\\\n", "\n");

		// Trim lines
		if (_text.contains("\n")) {
			String[] lines = _text.split("\n");
			_text = StringUtils.EMPTY;
			for (String line : lines) {
				String trimmed = line.trim();
				if (trimmed.length() == 0)
					continue;
				if (_text.length() > 0)
					_text += "\n  >";
				_text += trimmed;
			}
		}

		if (_text.length() == 0) {
			activeChar.sendActionFailed();
			return;
		}

		// Handle .commands if in ALL chat
		if (_text.startsWith(".") && _type == ChatType.ALL) {
			String fullcmd = _text.substring(1).trim();
			String command = fullcmd.split("\\s+")[0];
			String args = fullcmd.substring(command.length()).trim();

			if (command.length() > 0) {
				IVoicedCommandHandler vch = VoicedCommandHandler.getInstance().getVoicedCommandHandler(command);
				if (vch != null) {
					vch.useVoicedCommand(command, activeChar, args);
					return;
				}
			}
			activeChar.sendMessage(new CustomMessage("common.command404", activeChar));
			return;
		}
		// Evaluate == expressions
		else if (_text.startsWith("==")) {
			String expression = _text.substring(2);
			Expression expr = null;

			if (!expression.isEmpty()) {
				try {
					expr = ExpressionTree.parse(expression);
				} catch (ExpressionParseException epe) {
					// ignore parse error
				}

				if (expr != null) {
					try {
						VarMap vm = new VarMap();
						vm.setValue("adena", activeChar.getAdena());
						double result = expr.eval(vm, null);
						activeChar.sendMessage(expression);
						activeChar.sendMessage("=" + Util.formatDouble(result, "NaN", false));
					} catch (Exception e) {
						// ignore
					}
				}
			}
			return;
		}

		// Level-based chat filter
		if (Config.CHATFILTER_MIN_LEVEL > 0
				&& ArrayUtils.contains(Config.CHATFILTER_CHANNELS, _type.ordinal())
				&& activeChar.getLevel() < Config.CHATFILTER_MIN_LEVEL) {
			if (Config.CHATFILTER_WORK_TYPE == 1) {
				// redirect to ALL chat
				_type = ChatType.ALL;
			} else if (Config.CHATFILTER_WORK_TYPE == 2) {
				// block usage
				activeChar.sendMessage(new CustomMessage("chat.NotHavePermission", activeChar)
						.addNumber(Config.CHATFILTER_MIN_LEVEL));
				return;
			}
		}

		// Ban check logic
		boolean globalchat = (_type != ChatType.ALLIANCE && _type != ChatType.CLAN && _type != ChatType.PARTY);
		if (activeChar.taskExists(TaskType.Chat_ban)
				&& (globalchat || ArrayUtils.contains(Config.BAN_CHANNEL_LIST, _type.ordinal()))) {
			int timeRemained = Math.round(activeChar.getTaskExpireTime(TaskType.Chat_ban) / 60000);
			activeChar.sendMessage(new CustomMessage("common.ChatBanned", activeChar).addNumber(timeRemained));
			activeChar.sendActionFailed();
			return;
		}

		// Censorship/abuse checks
		if (globalchat) {
			if (Config.ABUSEWORD_REPLACE) {
				if (Config.containsAbuseWord(_text)) {
					_text = Config.ABUSEWORD_REPLACE_STRING;
					activeChar.sendActionFailed();
				}
			} else if (Config.ABUSEWORD_BANCHAT && Config.containsAbuseWord(_text)) {
				activeChar.sendMessage(new CustomMessage("common.ChatBanned", activeChar)
						.addNumber(Config.ABUSEWORD_BANTIME * 60));
				Log.add(activeChar + ": " + _text, "abuse");
				activeChar.addNewTask(TaskType.Chat_ban, Config.ABUSEWORD_BANTIME * 60000);
				activeChar.sendActionFailed();
				return;
			}
		}

		// Cache item links if present
		Matcher m = EX_ITEM_LINK_PATTERN.matcher(_text);
		while (m.find()) {
			int objectId = Integer.parseInt(m.group(1));
			ItemInstance item = activeChar.getInventory().getItemByObjectId(objectId);

			if (item == null) {
				activeChar.sendActionFailed();
				break;
			}
			ItemInfoCache.getInstance().put(item);
		}

		// Translit check
		String translit = activeChar.getVar("translit");
		if (translit != null) {
			// Exclude item links from transliteration
			m = SKIP_ITEM_LINK_PATTERN.matcher(_text);
			StringBuilder sb = new StringBuilder();
			int end = 0;
			while (m.find()) {
				sb.append(Strings.fromTranslit(
						_text.substring(end, m.start()),
						translit.equals("tl") ? 1 : 2));
				sb.append(_text.substring(m.start(), m.end()));
				end = m.end();
			}
			_text = sb.append(Strings.fromTranslit(
					_text.substring(end), translit.equals("tl") ? 1 : 2)).toString();
		}

		// Log the chat
		Log.LogChat(_type.name(), activeChar.getName(), _target, _text);

		// Build the packet
		Say2 cs = new Say2(activeChar.getObjectId(), _type, activeChar.getName(), _text);

		switch (_type) {
			case TELL: {
				Player receiver = World.getPlayer(_target);
				if (receiver != null && receiver.isInOfflineMode()) {
					activeChar.sendMessage("The person is in offline trade mode.");
					activeChar.sendActionFailed();
				} else if (receiver != null && !receiver.isInBlockList(activeChar) && !receiver.isBlockAll()) {
					if (!receiver.getMessageRefusal()) {
						if (activeChar.antiFlood.canTell(receiver.getObjectId(), _text))
							receiver.sendPacket(cs);
						cs = new Say2(activeChar.getObjectId(), _type, "->" + receiver.getName(), _text);
						activeChar.sendPacket(cs);
					} else {
						activeChar.sendPacket(SystemMsg.THAT_PERSON_IS_IN_MESSAGE_REFUSAL_MODE);
					}
				} else if (receiver == null) {
					activeChar.sendPacket(
							new SystemMessage2(SystemMsg.S1_IS_NOT_CURRENTLY_LOGGED_IN).addString(_target),
							ActionFail.STATIC);
				} else {
					activeChar.sendPacket(
							SystemMsg.YOU_HAVE_BEEN_BLOCKED_FROM_CHATTING_WITH_THAT_CONTACT,
							ActionFail.STATIC);
				}
				break;
			}

			case SHOUT: {
				if (activeChar.isCursedWeaponEquipped()) {
					activeChar.sendPacket(
							SystemMsg.SHOUT_AND_TRADE_CHATTING_CANNOT_BE_USED_WHILE_POSSESSING_A_CURSED_WEAPON);
					return;
				}
				if (activeChar.isInObserverMode()) {
					activeChar.sendPacket(SystemMsg.YOU_CANNOT_CHAT_WHILE_IN_OBSERVATION_MODE);
					return;
				}
				if (!activeChar.isGM() && !activeChar.antiFlood.canShout(_text)) {
					activeChar.sendMessage("Shout chat is allowed once per 5 seconds.");
					return;
				}
				if (Config.GLOBAL_SHOUT)
					announce(activeChar, cs);
				else
					shout(activeChar, cs);
				activeChar.sendPacket(cs);
				break;
			}

			case TRADE: {
				if (activeChar.isCursedWeaponEquipped()) {
					activeChar.sendPacket(
							SystemMsg.SHOUT_AND_TRADE_CHATTING_CANNOT_BE_USED_WHILE_POSSESSING_A_CURSED_WEAPON);
					return;
				}
				if (activeChar.isInObserverMode()) {
					activeChar.sendPacket(SystemMsg.YOU_CANNOT_CHAT_WHILE_IN_OBSERVATION_MODE);
					return;
				}
				if (!activeChar.isGM() && !activeChar.antiFlood.canTrade(_text)) {
					activeChar.sendMessage("Trade chat is allowed once per 5 seconds.");
					return;
				}
				if (Config.GLOBAL_TRADE_CHAT)
					announce(activeChar, cs);
				else
					shout(activeChar, cs);
				activeChar.sendPacket(cs);
				break;
			}

			case ALL: {
				if (activeChar.isCursedWeaponEquipped()) {
					// Use transformation name if cursed
					cs = new Say2(activeChar.getObjectId(), _type, activeChar.getTransformationName(), _text);
				}

				List<Player> list = null;
				if (activeChar.isInObserverMode()
						&& activeChar.getObserverRegion() != null
						&& activeChar.getOlympiadObserveGame() != null) {
					// If the player is observing an Olympiad match
					OlympiadGame game = activeChar.getOlympiadObserveGame();
					if (game != null)
						list = game.getAllPlayers();
				} else if (activeChar.isInOlympiadMode()) {
					// If the player is *in* an Olympiad match
					OlympiadGame game = activeChar.getOlympiadGame();
					if (game != null)
						list = game.getAllPlayers();
				} else {
					// Normal case: broadcast in the nearby range
					list = World.getAroundPlayers(activeChar);
				}

				if (list != null) {
					for (Player player : list) {
						if (player == activeChar
								|| player.getReflection() != activeChar.getReflection()
								|| player.isBlockAll()
								|| player.isInBlockList(activeChar)) {
							continue;
						}
						player.sendPacket(cs);
					}
				}
				// Ensure the activeChar also sees their own message
				activeChar.sendPacket(cs);
				break;
			}

			case CLAN: {
				// Use SLF4J varargs approach
				_log.info("CLAN chat invoked by '{}' (clan={}) | text='{}'",
						new Object[] {
								activeChar.getName(),
								(activeChar.getClan() == null ? "null" : activeChar.getClan().getName()),
								_text
						});
				if (activeChar.getClan() != null) {
					_log.info("Broadcasting CLAN chat to online members of '{}'",
							activeChar.getClan().getName());
					activeChar.getClan().broadcastToOnlineMembers(cs);
				}
				break;
			}

			case ALLIANCE: {
				_log.info("ALLIANCE chat invoked by '{}' | text='{}'",
						activeChar.getName(),
						_text);
				if (activeChar.getClan() != null && activeChar.getClan().getAlliance() != null) {
					_log.info("Broadcasting ALLIANCE chat to alliance '{}'",
							activeChar.getClan().getAlliance().getAllyName());
					activeChar.getClan().getAlliance().broadcastToOnlineMembers(cs);
				}
				break;
			}

			case PARTY: {
				if (activeChar.isInParty())
					activeChar.getParty().broadCast(cs);
				break;
			}

			case PARTY_ROOM: {
				MatchingRoom r = activeChar.getMatchingRoom();
				if (r != null && r.getType() == MatchingRoom.PARTY_MATCHING)
					r.broadCast(cs);
				break;
			}

			case COMMANDCHANNEL_ALL: {
				if (!activeChar.isInParty() || !activeChar.getParty().isInCommandChannel()) {
					activeChar.sendPacket(SystemMsg.YOU_DO_NOT_HAVE_THE_AUTHORITY_TO_USE_THE_COMMAND_CHANNEL);
					return;
				}
				if (activeChar.getParty().getCommandChannel().getChannelLeader() == activeChar)
					activeChar.getParty().getCommandChannel().broadCast(cs);
				else
					activeChar.sendPacket(SystemMsg.ONLY_THE_COMMAND_CHANNEL_CREATOR_CAN_USE_THE_RAID_LEADER_TEXT);
				break;
			}

			case COMMANDCHANNEL_COMMANDER: {
				if (!activeChar.isInParty() || !activeChar.getParty().isInCommandChannel()) {
					activeChar.sendPacket(SystemMsg.YOU_DO_NOT_HAVE_THE_AUTHORITY_TO_USE_THE_COMMAND_CHANNEL);
					return;
				}
				if (activeChar.getParty().isLeader(activeChar))
					activeChar.getParty().getCommandChannel().broadcastToChannelPartyLeaders(cs);
				else
					activeChar.sendPacket(SystemMsg.ONLY_A_PARTY_LEADER_CAN_ACCESS_THE_COMMAND_CHANNEL);
				break;
			}

			case HERO_VOICE: {
				if (activeChar.isHero() || activeChar.getPlayerAccess().CanAnnounce) {
					// Only heroes or GMs can use this. GMs skip rate limits
					if (!activeChar.getPlayerAccess().CanAnnounce) {
						// Rate-limit hero chat
						if (!activeChar.antiFlood.canHero(_text)) {
							activeChar.sendMessage("Hero chat is allowed once per 10 seconds.");
							return;
						}
					}
					for (Player player : GameObjectsStorage.getAllPlayersForIterate()) {
						if (!player.isInBlockList(activeChar) && !player.isBlockAll()) {
							player.sendPacket(cs);
						}
					}
				}
				break;
			}

			case PETITION_PLAYER:
			case PETITION_GM: {
				if (!PetitionManager.getInstance().isPlayerInConsultation(activeChar)) {
					activeChar.sendPacket(
							new SystemMessage2(SystemMsg.YOU_ARE_CURRENTLY_NOT_IN_A_PETITION_CHAT));
					return;
				}
				PetitionManager.getInstance().sendActivePetitionMessage(activeChar, _text);
				break;
			}

			case BATTLEFIELD: {
				if (activeChar.getBattlefieldChatId() == 0)
					return;

				for (Player player : GameObjectsStorage.getAllPlayersForIterate()) {
					if (!player.isInBlockList(activeChar)
							&& !player.isBlockAll()
							&& player.getBattlefieldChatId() == activeChar.getBattlefieldChatId()) {
						player.sendPacket(cs);
					}
				}
				break;
			}

			case MPCC_ROOM: {
				MatchingRoom r2 = activeChar.getMatchingRoom();
				if (r2 != null && r2.getType() == MatchingRoom.CC_MATCHING)
					r2.broadCast(cs);
				break;
			}

			default: {
				_log.warn("Character {} used unknown chat type: {}.", activeChar.getName(), _type.ordinal());
				break;
			}
		}
	}

	/**
	 * Helper for shouting in a region-limited area
	 */
	private static void shout(Player activeChar, Say2 cs) {
		int rx = MapUtils.regionX(activeChar);
		int ry = MapUtils.regionY(activeChar);
		int offset = Config.SHOUT_OFFSET;

		for (Player player : GameObjectsStorage.getAllPlayersForIterate()) {
			if (player == activeChar
					|| activeChar.getReflection() != player.getReflection()
					|| player.isBlockAll()
					|| player.isInBlockList(activeChar)) {
				continue;
			}

			int tx = MapUtils.regionX(player);
			int ty = MapUtils.regionY(player);

			if ((tx >= rx - offset && tx <= rx + offset && ty >= ry - offset && ty <= ry + offset)
					|| activeChar.isInRangeZ(player, Config.CHAT_RANGE)) {
				player.sendPacket(cs);
			}
		}
	}

	/**
	 * Helper for global announcements
	 */
	private static void announce(Player activeChar, Say2 cs) {
		for (Player player : GameObjectsStorage.getAllPlayersForIterate()) {
			if (player == activeChar
					|| activeChar.getReflection() != player.getReflection()
					|| player.isBlockAll()
					|| player.isInBlockList(activeChar)) {
				continue;
			}
			player.sendPacket(cs);
		}
	}
}
