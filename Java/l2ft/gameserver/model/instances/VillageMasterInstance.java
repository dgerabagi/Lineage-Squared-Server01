// java/l2ft/gameserver/model/instances/VillageMasterInstance.java
package l2ft.gameserver.model.instances;

import java.util.Collection;

import l2ft.gameserver.Config;
import l2ft.gameserver.cache.Msg;
import l2ft.gameserver.data.xml.holder.ResidenceHolder;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.actor.instances.player.StackClass;
import l2ft.gameserver.model.base.Race;
import l2ft.gameserver.model.entity.events.impl.CastleSiegeEvent;
import l2ft.gameserver.model.entity.events.impl.SiegeEvent;
import l2ft.gameserver.model.entity.olympiad.Olympiad;
import l2ft.gameserver.model.entity.residence.Castle;
import l2ft.gameserver.model.entity.residence.Dominion;
import l2ft.gameserver.model.entity.residence.Residence;
import l2ft.gameserver.model.items.ItemInstance;
import l2ft.gameserver.model.pledge.Alliance;
import l2ft.gameserver.model.pledge.Clan;
import l2ft.gameserver.model.pledge.SubUnit;
import l2ft.gameserver.model.pledge.UnitMember;
import l2ft.gameserver.network.l2.components.CustomMessage;
import l2ft.gameserver.network.l2.components.SystemMsg;
import l2ft.gameserver.network.l2.s2c.NpcHtmlMessage;
import l2ft.gameserver.network.l2.s2c.PledgeReceiveSubPledgeCreated;
import l2ft.gameserver.network.l2.s2c.PledgeShowInfoUpdate;
import l2ft.gameserver.network.l2.s2c.PledgeShowMemberListUpdate;
import l2ft.gameserver.network.l2.s2c.PledgeStatusChanged;
import l2ft.gameserver.network.l2.s2c.SystemMessage;
import l2ft.gameserver.scripts.Functions;
import l2ft.gameserver.tables.ClanTable;
import l2ft.gameserver.tables.SkillTable;
import l2ft.gameserver.templates.npc.NpcTemplate;
import l2ft.gameserver.utils.HtmlUtils;
import l2ft.gameserver.utils.SiegeUtils;
import l2ft.gameserver.utils.Util;

public final class VillageMasterInstance extends NpcInstance {
	public VillageMasterInstance(int objectId, NpcTemplate template) {
		super(objectId, template);
	}

	@Override
	public void onBypassFeedback(Player player, String command) {
		if (!canBypassCheck(player, this))
			return;

		if (command.startsWith("create_clan") && command.length() > 12) {
			String val = command.substring(12);
			createClan(player, val);
		} else if (command.startsWith("create_academy") && command.length() > 15) {
			String sub = command.substring(15, command.length());
			createSubPledge(player, sub, Clan.SUBUNIT_ACADEMY, 5, "");
		} else if (command.startsWith("create_royal") && command.length() > 15) {
			String[] sub = command.substring(13, command.length()).split(" ", 2);
			if (sub.length == 2)
				createSubPledge(player, sub[1], Clan.SUBUNIT_ROYAL1, 6, sub[0]);
		} else if (command.startsWith("create_knight") && command.length() > 16) {
			String[] sub = command.substring(14, command.length()).split(" ", 2);
			if (sub.length == 2)
				createSubPledge(player, sub[1], Clan.SUBUNIT_KNIGHT1, 7, sub[0]);
		} else if (command.startsWith("assign_subpl_leader") && command.length() > 22) {
			String[] sub = command.substring(20, command.length()).split(" ", 2);
			if (sub.length == 2)
				assignSubPledgeLeader(player, sub[1], sub[0]);
		} else if (command.startsWith("assign_new_clan_leader") && command.length() > 23) {
			String val = command.substring(23);
			setLeader(player, val);
		}
		if (command.startsWith("create_ally") && command.length() > 12) {
			String val = command.substring(12);
			createAlly(player, val);
		} else if (command.startsWith("dissolve_ally"))
			dissolveAlly(player);
		else if (command.startsWith("dissolve_clan"))
			dissolveClan(player);
		else if (command.startsWith("increase_clan_level"))
			levelUpClan(player);
		else if (command.startsWith("learn_clan_skills"))
			showClanSkillList(player);
		else if (command.startsWith("ShowCouponExchange")) {
			if (Functions.getItemCount(player, 8869) > 0 || Functions.getItemCount(player, 8870) > 0)
				command = "Multisell 800";
			else
				command = "Link villagemaster/reflect_weapon_master_noticket.htm";
			super.onBypassFeedback(player, command);
		} else if (command.equalsIgnoreCase("CertificationList")) {
			return;
		} else if (command.startsWith("Subclass")) {
			if (player.getPet() != null) {
				player.sendPacket(
						SystemMsg.A_SUBCLASS_MAY_NOT_BE_CREATED_OR_CHANGED_WHILE_A_SERVITOR_OR_PET_IS_SUMMONED);
				return;
			}

			// ĐˇĐ°Đ± ĐşĐ»Đ°Ń�Ń� Đ˝ĐµĐ»ŃŚĐ·ŃŹ ĐżĐľĐ»Ń�Ń‡Đ¸Ń‚ŃŚ Đ¸Đ»Đ¸ ĐżĐľĐĽĐµĐ˝ŃŹŃ‚ŃŚ,
			// ĐżĐľĐşĐ° Đ¸Ń�ĐżĐľĐ»ŃŚĐ·Ń�ĐµŃ‚Ń�ŃŹ Ń�ĐşĐ¸Đ»Đ» Đ¸Đ»Đ¸ ĐżĐµŃ€Ń�ĐľĐ˝Đ°Đ¶
			// Đ˝Đ°Ń…ĐľĐ´Đ¸Ń‚Ń�ŃŹ Đ˛ Ń€ĐµĐ¶Đ¸ĐĽĐµ Ń‚Ń€Đ°Đ˝Ń�Ń„ĐľŃ€ĐĽĐ°Ń†Đ¸Đ¸
			if (player.isActionsDisabled() || player.getTransformation() != 0) {
				player.sendPacket(SystemMsg.SUBCLASSES_MAY_NOT_BE_CREATED_OR_CHANGED_WHILE_A_SKILL_IS_IN_USE);
				return;
			}

			if (player.getWeightPenalty() >= 3) {
				player.sendPacket(
						SystemMsg.A_SUBCLASS_CANNOT_BE_CREATED_OR_CHANGED_WHILE_YOU_ARE_OVER_YOUR_WEIGHT_LIMIT);
				return;
			}

			if (player.getInventoryLimit() * 0.8 < player.getInventory().getSize()) {
				player.sendPacket(
						SystemMsg.A_SUBCLASS_CANNOT_BE_CREATED_OR_CHANGED_BECAUSE_YOU_HAVE_EXCEEDED_YOUR_INVENTORY_LIMIT);
				return;
			}

			StringBuilder content = new StringBuilder("<html><body>");
			NpcHtmlMessage html = new NpcHtmlMessage(player, this);

			if (player.getLevel() < 40) {
				content.append("You must be level 40 or more to operate with your sub-classes.");
				content.append("</body></html>");
				html.setHtml(content.toString());
				player.sendPacket(html);
				return;
			}

			int classId = 0;
			int intVal = 0;

			try {
				for (String id : command.substring(9, command.length()).split(" ")) {
					if (intVal == 0) {
						intVal = Integer.parseInt(id);
						continue;
					}
					classId = Integer.parseInt(id);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			StackClass first = Player.restoreCharSubClass(player, 131);
			StackClass second = Player.restoreCharSubClass(player, 134);
			StackClass third = Player.restoreCharSubClass(player, 133);

			switch (intVal) {
				case 1: // Text for adding subclass
					if (player.getRace() == Race.kamael || player.getActiveClass().isKamael()) {
						player.sendMessage(new CustomMessage(
								"l2ft.gameserver.model.instances.L2VillageMasterInstance.NoSubAtThisTime", player));
						return;
					}

					content.append("Add Subclass:<br>Which subclass do you wish to add?<br>");

					if (first == null)
						content.append("<a action=\"bypass -h npc_").append(getObjectId()).append("_Subclass 4 ")
								.append(131).append("\">").append("Doombringer/Male Soulhound").append("</a><br>");

					if (second == null)
						content.append("<a action=\"bypass -h npc_").append(getObjectId()).append("_Subclass 4 ")
								.append(134).append("\">").append("Trickster/Female Soulhound").append("</a><br>");

					if (first != null && second != null && third == null && first.getLevel() >= 75
							&& second.getLevel() >= 75)
						content.append("<a action=\"bypass -h npc_").append(getObjectId()).append("_Subclass 4 ")
								.append(133).append("\">").append("Soulhound/Judicator").append("</a><br>");

					break;
				case 2: // Text for Switching between subclasses
					content.append("Change Subclass:<br>");

					content.append("Which class would you like to switch to?<br>");

					if (player.getActiveClass().isKamael())
						content.append("<a action=\"bypass -h npc_").append(getObjectId()).append("_Subclass 5 ")
								.append("0").append("\">").append("Main Class")
								.append("</a> " + "<font color=\"LEVEL\">(Base Class)</font><br><br>");
					else {
						if (first != null)
							content.append("<a action=\"bypass -h npc_").append(getObjectId()).append("_Subclass 5 ")
									.append("131").append("\">").append("Doombringer/Male Soulhound")
									.append("</a> <br>");
						if (second != null)
							content.append("<a action=\"bypass -h npc_").append(getObjectId()).append("_Subclass 5 ")
									.append("134").append("\">").append("Trickster/Female Soulhound")
									.append("</a> <br>");
						if (third != null)
							content.append("<a action=\"bypass -h npc_").append(getObjectId()).append("_Subclass 5 ")
									.append("133").append("\">").append("Soulhound/Judicator").append("</a> <br>");
					}
					break;
				case 3: // Changing subclass to different one
					break;
				case 4: // Adding subclass
					boolean allowAddition = true;

					if (player.getLevel() < Config.ALT_GAME_LEVEL_TO_GET_SUBCLASS) {
						player.sendMessage(new CustomMessage(
								"l2ft.gameserver.model.instances.L2VillageMasterInstance.NoSubBeforeLevel", player)
								.addNumber(Config.ALT_GAME_LEVEL_TO_GET_SUBCLASS));
						allowAddition = false;
					}

					if (Config.ENABLE_OLYMPIAD && Olympiad.isRegisteredInComp(player)) {
						player.sendPacket(Msg.YOU_CANT_JOIN_THE_OLYMPIAD_WITH_A_SUB_JOB_CHARACTER);
						return;
					}

					if (player.getActiveClass().isKamael()) {
						player.sendMessage("Change your class to main first!");
						return;
					}

					if (!Config.ALT_GAME_SUBCLASS_WITHOUT_QUESTS)
						if (player.isQuestCompleted("_234_FatesWhisper")) {
							allowAddition = player.isQuestCompleted("_235_MimirsElixir");
							if (!allowAddition)
								player.sendMessage(new CustomMessage(
										"l2ft.gameserver.model.instances.L2VillageMasterInstance.QuestMimirsElixir",
										player));
						} else {
							player.sendMessage(new CustomMessage(
									"l2ft.gameserver.model.instances.L2VillageMasterInstance.QuestFatesWhisper",
									player));
							allowAddition = false;
						}

					if (allowAddition) {
						if (!player.addSubClass(classId)) {
							player.sendMessage(new CustomMessage(
									"l2ft.gameserver.model.instances.L2VillageMasterInstance.SubclassCouldNotBeAdded",
									player));
							return;
						}

						content.append("Add Subclass:<br>The subclass of <font color=\"LEVEL\">")
								.append(HtmlUtils.htmlClassName(classId))
								.append("</font> has been added. Relog to take effect.");
						player.sendPacket(SystemMsg.THE_NEW_SUBCLASS_HAS_BEEN_ADDED);
					} else
						html.setFile("villagemaster/SubClass_Fail.htm");
					break;
				case 5: // switching subclass

					if (Config.ENABLE_OLYMPIAD && Olympiad.isRegisteredInComp(player)) {
						player.sendPacket(Msg.YOU_CANT_JOIN_THE_OLYMPIAD_WITH_A_SUB_JOB_CHARACTER);
						return;
					}

					player.switchActiveClass(classId);

					content.append(
							"Change Subclass:<br>Your active subclass has been changed. You must relog to take effect.");

					player.sendPacket(SystemMsg.YOU_HAVE_SUCCESSFULLY_SWITCHED_TO_YOUR_SUBCLASS);
					// completed.
					break;
				case 6:
					break;
				case 7:
					break;
			}
			content.append("</body></html>");

			// If the content is greater than for a basic blank page,
			// then assume no external HTML file was assigned.
			if (content.length() > 26)
				html.setHtml(content.toString());

			player.sendPacket(html);
		} else
			super.onBypassFeedback(player, command);
	}

	@Override
	public String getHtmlPath(int npcId, int val, Player player) {
		String pom;
		if (val == 0)
			pom = "" + npcId;
		else
			pom = npcId + "-" + val;

		return "villagemaster/" + pom + ".htm";
	}

	// Private stuff
	public void createClan(Player player, String clanName) {
		if (player.getLevel() < 10) {
			player.sendPacket(Msg.YOU_ARE_NOT_QUALIFIED_TO_CREATE_A_CLAN);
			return;
		}

		if (player.getClanId() != 0) {
			player.sendPacket(Msg.YOU_HAVE_FAILED_TO_CREATE_A_CLAN);
			return;
		}

		if (!player.canCreateClan()) {
			// you can't create a new clan within 10 days
			player.sendPacket(Msg.YOU_MUST_WAIT_10_DAYS_BEFORE_CREATING_A_NEW_CLAN);
			return;
		}
		if (clanName.length() > 16) {
			player.sendPacket(Msg.CLAN_NAMES_LENGTH_IS_INCORRECT);
			return;
		}
		if (!Util.isMatchingRegexp(clanName, Config.CLAN_NAME_TEMPLATE)) {
			// clan name is not matching template
			player.sendPacket(Msg.CLAN_NAME_IS_INCORRECT);
			return;
		}

		Clan clan = ClanTable.getInstance().createClan(player, clanName);
		if (clan == null) {
			// clan name is already taken
			player.sendPacket(Msg.THIS_NAME_ALREADY_EXISTS);
			return;
		}

		// should be update packet only
		player.sendPacket(clan.listAll());
		player.sendPacket(new PledgeShowInfoUpdate(clan), Msg.CLAN_HAS_BEEN_CREATED);
		player.updatePledgeClass();
		player.broadcastCharInfo();
	}

	public void setLeader(Player leader, String newLeader) {
		if (!leader.isClanLeader()) {
			leader.sendPacket(Msg.ONLY_THE_CLAN_LEADER_IS_ENABLED);
			return;
		}

		if (leader.getEvent(SiegeEvent.class) != null) {
			leader.sendMessage(new CustomMessage("scripts.services.Rename.SiegeNow", leader));
			return;
		}

		Clan clan = leader.getClan();
		SubUnit mainUnit = clan.getSubUnit(Clan.SUBUNIT_MAIN_CLAN);
		UnitMember member = mainUnit.getUnitMember(newLeader);

		if (member == null) {
			// FIX ME Đ·Đ°Ń‡ĐµĐĽ 2-Đ˛Đµ ĐĽĐµŃ�Ń�Đ°ĐłĐ¸(VISTALL)
			// leader.sendMessage(new
			// CustomMessage("l2ft.gameserver.model.instances.L2VillageMasterInstance.S1IsNotMemberOfTheClan",
			// leader).addString(newLeader));
			showChatWindow(leader, "villagemaster/clan-20.htm");
			return;
		}

		if (member.getLeaderOf() != Clan.SUBUNIT_NONE) {
			leader.sendMessage(new CustomMessage(
					"l2ft.gameserver.model.instances.L2VillageMasterInstance.CannotAssignUnitLeader", leader));
			return;
		}

		setLeader(leader, clan, mainUnit, member);
	}

	public static void setLeader(Player player, Clan clan, SubUnit unit, UnitMember newLeader) {
		player.sendMessage(new CustomMessage(
				"l2ft.gameserver.model.instances.L2VillageMasterInstance.ClanLeaderWillBeChangedFromS1ToS2", player)
				.addString(clan.getLeaderName()).addString(newLeader.getName()));
		// TODO: Đ’ Đ´Đ°Đ˝Đ˝ĐľĐą Ń€ĐµĐ´Đ°ĐşŃ†Đ¸Đ¸ Ń�ĐĽĐµĐ˝Đ° Đ»Đ¸Đ´ĐµŃ€Đ°
		// ĐżŃ€ĐľĐ¸Đ·Đ˛ĐľĐ´Đ¸Ń‚Ń�ŃŹ Ń�Ń€Đ°Đ·Ń� Đ¶Đµ.
		// ĐťĐ°Đ´Đľ ĐżĐľĐ´Ń�ĐĽĐ°Ń‚ŃŚ Đ˝Đ°Đ´ Ń€ĐµĐ°Đ»Đ¸Đ·Đ°Ń†Đ¸ĐµĐą Ń�ĐĽĐµĐ˝Ń‹
		// ĐşĐ»Đ°Đ˝Đ»Đ¸Đ´ĐµŃ€Đ° Đ˛ Đ·Đ°ĐżĐ»Đ°Đ˝Đ¸Ń€ĐľĐ˛Đ°Đ˝Đ˝Ń‹Đą Đ´ĐµĐ˝ŃŚ Đ˝ĐµĐ´ĐµĐ»Đ¸.

		/*
		 * if(clan.getLevel() >= CastleSiegeManager.getSiegeClanMinLevel())
		 * {
		 * if(clan.getLeader() != null)
		 * {
		 * L2Player oldLeaderPlayer = clan.getLeader().getPlayer();
		 * if(oldLeaderPlayer != null)
		 * SiegeUtils.removeSiegeSkills(oldLeaderPlayer);
		 * }
		 * L2Player newLeaderPlayer = newLeader.getPlayer();
		 * if(newLeaderPlayer != null)
		 * SiegeUtils.addSiegeSkills(newLeaderPlayer);
		 * }
		 */
		unit.setLeader(newLeader, true);

		clan.broadcastClanStatus(true, true, false);
	}

	public void createSubPledge(Player player, String clanName, int pledgeType, int minClanLvl, String leaderName) {
		UnitMember subLeader = null;

		Clan clan = player.getClan();

		if (clan == null || !player.isClanLeader()) {
			player.sendPacket(Msg.YOU_HAVE_FAILED_TO_CREATE_A_CLAN);
			return;
		}

		if (!Util.isMatchingRegexp(clanName, Config.CLAN_NAME_TEMPLATE)) {
			player.sendPacket(Msg.CLAN_NAME_IS_INCORRECT);
			return;
		}

		Collection<SubUnit> subPledge = clan.getAllSubUnits();
		for (SubUnit element : subPledge)
			if (element.getName().equals(clanName)) {
				player.sendPacket(Msg.ANOTHER_MILITARY_UNIT_IS_ALREADY_USING_THAT_NAME_PLEASE_ENTER_A_DIFFERENT_NAME);
				return;
			}

		if (ClanTable.getInstance().getClanByName(clanName) != null) {
			player.sendPacket(Msg.ANOTHER_MILITARY_UNIT_IS_ALREADY_USING_THAT_NAME_PLEASE_ENTER_A_DIFFERENT_NAME);
			return;
		}

		if (clan.getLevel() < minClanLvl) {
			player.sendPacket(Msg.THE_CONDITIONS_NECESSARY_TO_CREATE_A_MILITARY_UNIT_HAVE_NOT_BEEN_MET);
			return;
		}

		SubUnit unit = clan.getSubUnit(Clan.SUBUNIT_MAIN_CLAN);

		if (pledgeType != Clan.SUBUNIT_ACADEMY) {
			subLeader = unit.getUnitMember(leaderName);
			if (subLeader == null) {
				player.sendMessage(new CustomMessage(
						"l2ft.gameserver.model.instances.L2VillageMasterInstance.PlayerCantBeAssignedAsSubUnitLeader",
						player));
				return;
			} else if (subLeader.getLeaderOf() != Clan.SUBUNIT_NONE) {
				player.sendMessage(new CustomMessage(
						"l2ft.gameserver.model.instances.L2VillageMasterInstance.ItCantBeSubUnitLeader", player));
				return;
			}
		}

		pledgeType = clan.createSubPledge(player, pledgeType, subLeader, clanName);
		if (pledgeType == Clan.SUBUNIT_NONE)
			return;

		clan.broadcastToOnlineMembers(new PledgeReceiveSubPledgeCreated(clan.getSubUnit(pledgeType)));

		SystemMessage sm;
		if (pledgeType == Clan.SUBUNIT_ACADEMY) {
			sm = new SystemMessage(SystemMessage.CONGRATULATIONS_THE_S1S_CLAN_ACADEMY_HAS_BEEN_CREATED);
			sm.addString(player.getClan().getName());
		} else if (pledgeType >= Clan.SUBUNIT_KNIGHT1) {
			sm = new SystemMessage(SystemMessage.THE_KNIGHTS_OF_S1_HAVE_BEEN_CREATED);
			sm.addString(player.getClan().getName());
		} else if (pledgeType >= Clan.SUBUNIT_ROYAL1) {
			sm = new SystemMessage(SystemMessage.THE_ROYAL_GUARD_OF_S1_HAVE_BEEN_CREATED);
			sm.addString(player.getClan().getName());
		} else
			sm = Msg.CLAN_HAS_BEEN_CREATED;

		player.sendPacket(sm);

		if (subLeader != null) {
			clan.broadcastToOnlineMembers(new PledgeShowMemberListUpdate(subLeader));
			if (subLeader.isOnline()) {
				subLeader.getPlayer().updatePledgeClass();
				subLeader.getPlayer().broadcastCharInfo();
			}
		}
	}

	public void assignSubPledgeLeader(Player player, String clanName, String leaderName) {
		Clan clan = player.getClan();

		if (clan == null) {
			player.sendMessage(new CustomMessage(
					"l2ft.gameserver.model.instances.L2VillageMasterInstance.ClanDoesntExist", player));
			return;
		}

		if (!player.isClanLeader()) {
			player.sendPacket(Msg.ONLY_THE_CLAN_LEADER_IS_ENABLED);
			return;
		}

		SubUnit targetUnit = null;
		for (SubUnit unit : clan.getAllSubUnits()) {
			if (unit.getType() == Clan.SUBUNIT_MAIN_CLAN || unit.getType() == Clan.SUBUNIT_ACADEMY)
				continue;
			if (unit.getName().equalsIgnoreCase(clanName))
				targetUnit = unit;

		}
		if (targetUnit == null) {
			player.sendMessage(new CustomMessage(
					"l2ft.gameserver.model.instances.L2VillageMasterInstance.SubUnitNotFound", player));
			return;
		}
		SubUnit mainUnit = clan.getSubUnit(Clan.SUBUNIT_MAIN_CLAN);
		UnitMember subLeader = mainUnit.getUnitMember(leaderName);
		if (subLeader == null) {
			player.sendMessage(new CustomMessage(
					"l2ft.gameserver.model.instances.L2VillageMasterInstance.PlayerCantBeAssignedAsSubUnitLeader",
					player));
			return;
		}

		if (subLeader.getLeaderOf() != Clan.SUBUNIT_NONE) {
			player.sendMessage(new CustomMessage(
					"l2ft.gameserver.model.instances.L2VillageMasterInstance.ItCantBeSubUnitLeader", player));
			return;
		}

		targetUnit.setLeader(subLeader, true);
		clan.broadcastToOnlineMembers(new PledgeReceiveSubPledgeCreated(targetUnit));

		clan.broadcastToOnlineMembers(new PledgeShowMemberListUpdate(subLeader));
		if (subLeader.isOnline()) {
			subLeader.getPlayer().updatePledgeClass();
			subLeader.getPlayer().broadcastCharInfo();
		}

		player.sendMessage(new CustomMessage(
				"l2ft.gameserver.model.instances.L2VillageMasterInstance.NewSubUnitLeaderHasBeenAssigned", player));
	}

	private void dissolveClan(Player player) {
		if (player == null || player.getClan() == null)
			return;
		Clan clan = player.getClan();

		if (!player.isClanLeader()) {
			player.sendPacket(Msg.ONLY_THE_CLAN_LEADER_IS_ENABLED);
			return;
		}
		if (clan.getAllyId() != 0) {
			player.sendPacket(Msg.YOU_CANNOT_DISPERSE_THE_CLANS_IN_YOUR_ALLIANCE);
			return;
		}
		if (clan.isAtWar() > 0) {
			player.sendPacket(Msg.YOU_CANNOT_DISSOLVE_A_CLAN_WHILE_ENGAGED_IN_A_WAR);
			return;
		}
		if (clan.getCastle() != 0 || clan.getHasHideout() != 0 || clan.getHasFortress() != 0) {
			player.sendPacket(Msg.UNABLE_TO_DISPERSE_YOUR_CLAN_OWNS_ONE_OR_MORE_CASTLES_OR_HIDEOUTS);
			return;
		}

		for (Residence r : ResidenceHolder.getInstance().getResidences()) {
			if (r.getSiegeEvent().getSiegeClan(SiegeEvent.ATTACKERS, clan) != null
					|| r.getSiegeEvent().getSiegeClan(SiegeEvent.DEFENDERS, clan) != null
					|| r.getSiegeEvent().getSiegeClan(CastleSiegeEvent.DEFENDERS_WAITING, clan) != null) {
				player.sendPacket(
						SystemMsg.UNABLE_TO_DISSOLVE_YOUR_CLAN_HAS_REQUESTED_TO_PARTICIPATE_IN_A_CASTLE_SIEGE);
				return;
			}
		}

		ClanTable.getInstance().dissolveClan(player);
	}

	public void levelUpClan(Player player) {
		Clan clan = player.getClan();
		if (clan == null)
			return;
		if (!player.isClanLeader()) {
			player.sendPacket(Msg.ONLY_THE_CLAN_LEADER_IS_ENABLED);
			return;
		}

		boolean increaseClanLevel = false;

		switch (clan.getLevel()) {
			case 0:
				// Upgrade to 1
				if (player.getSp() >= 20000 && player.getAdena() >= 650000) {
					player.setSp(player.getSp() - 20000);
					player.reduceAdena(650000, true);
					increaseClanLevel = true;
				}
				break;
			case 1:
				// Upgrade to 2
				if (player.getSp() >= 100000 && player.getAdena() >= 2500000) {
					player.setSp(player.getSp() - 100000);
					player.reduceAdena(2500000, true);
					increaseClanLevel = true;
				}
				break;
			case 2:
				// Upgrade to 3
				// itemid 1419 == Blood Mark
				if (player.getSp() >= 350000 && player.getInventory().destroyItemByItemId(1419, 1)) {
					player.setSp(player.getSp() - 350000);
					increaseClanLevel = true;
				}
				break;
			case 3:
				// Upgrade to 4
				// itemid 3874 == Alliance Manifesto
				if (player.getSp() >= 1000000 && player.getInventory().destroyItemByItemId(3874, 1)) {
					player.setSp(player.getSp() - 1000000);
					increaseClanLevel = true;
				}
				break;
			case 4:
				// Upgrade to 5
				// itemid 3870 == Seal of Aspiration
				if (player.getSp() >= 2500000 && player.getInventory().destroyItemByItemId(3870, 1)) {
					player.setSp(player.getSp() - 2500000);
					increaseClanLevel = true;
				}
				break;
			case 5:
				// Upgrade to 6
				if (clan.getReputationScore() >= Config.CLAN_LEVEL_6_COST
						&& clan.getAllSize() >= Config.CLAN_LEVEL_6_REQUIREMEN) {
					clan.incReputation(-Config.CLAN_LEVEL_6_COST, false, "LvlUpClan");
					increaseClanLevel = true;
				}
				break;
			case 6:
				// Upgrade to 7
				if (clan.getReputationScore() >= Config.CLAN_LEVEL_7_COST
						&& clan.getAllSize() >= Config.CLAN_LEVEL_7_REQUIREMEN) {
					clan.incReputation(-Config.CLAN_LEVEL_7_COST, false, "LvlUpClan");
					increaseClanLevel = true;
				}
				break;
			case 7:
				// Upgrade to 8
				if (clan.getReputationScore() >= Config.CLAN_LEVEL_8_COST
						&& clan.getAllSize() >= Config.CLAN_LEVEL_8_REQUIREMEN) {
					clan.incReputation(-Config.CLAN_LEVEL_8_COST, false, "LvlUpClan");
					increaseClanLevel = true;
				}
				break;
			case 8:
				// Upgrade to 9
				// itemId 9910 == Blood Oath
				if (clan.getReputationScore() >= Config.CLAN_LEVEL_9_COST
						&& clan.getAllSize() >= Config.CLAN_LEVEL_9_REQUIREMEN) {
					ItemInstance item = player.getInventory().getItemByItemId(9910);
					if (item != null && item.getCount() >= Config.BLOOD_OATHS) {
						clan.incReputation(-Config.CLAN_LEVEL_9_COST, false, "LvlUpClan");
						player.getInventory().destroyItemByItemId(9910, Config.BLOOD_OATHS);
						increaseClanLevel = true;
					}
				}
				break;
			case 9:
				// Upgrade to 10
				// itemId 9911 == Blood Alliance
				if (clan.getReputationScore() >= Config.CLAN_LEVEL_10_COST
						&& clan.getAllSize() >= Config.CLAN_LEVEL_10_REQUIREMEN) {
					ItemInstance item = player.getInventory().getItemByItemId(9911);
					if (item != null && item.getCount() >= Config.BLOOD_PLEDGES) {
						clan.incReputation(-Config.CLAN_LEVEL_10_COST, false, "LvlUpClan");
						player.getInventory().destroyItemByItemId(9911, Config.BLOOD_PLEDGES);
						increaseClanLevel = true;
					}
				}
				break;
			case 10:
				// Upgrade to 11
				if (clan.getReputationScore() >= Config.CLAN_LEVEL_11_COST
						&& clan.getAllSize() >= Config.CLAN_LEVEL_11_REQUIREMEN) {
					Castle castle = ResidenceHolder.getInstance().getResidence(clan.getCastle());
					Dominion dominion = castle.getDominion();
					if (dominion.getLordObjectId() == player.getObjectId()) {
						clan.incReputation(-Config.CLAN_LEVEL_11_COST, false, "LvlUpClan");
						increaseClanLevel = true;
					}
				}
				break;
		}

		if (increaseClanLevel) {
			clan.setLevel(clan.getLevel() + 1);
			clan.updateClanInDB();

			player.broadcastCharInfo();

			doCast(SkillTable.getInstance().getInfo(5103, 1), player, true);

			if (clan.getLevel() >= 4)
				SiegeUtils.addSiegeSkills(player);

			if (clan.getLevel() == 5)
				player.sendPacket(
						Msg.NOW_THAT_YOUR_CLAN_LEVEL_IS_ABOVE_LEVEL_5_IT_CAN_ACCUMULATE_CLAN_REPUTATION_POINTS);

			// notify all the members about it
			PledgeShowInfoUpdate pu = new PledgeShowInfoUpdate(clan);
			PledgeStatusChanged ps = new PledgeStatusChanged(clan);
			for (UnitMember mbr : clan)
				if (mbr.isOnline()) {
					mbr.getPlayer().updatePledgeClass();
					mbr.getPlayer().sendPacket(Msg.CLANS_SKILL_LEVEL_HAS_INCREASED, pu, ps);
					mbr.getPlayer().broadcastCharInfo();
				}
		} else
			player.sendPacket(Msg.CLAN_HAS_FAILED_TO_INCREASE_SKILL_LEVEL);
	}

	public void createAlly(Player player, String allyName) {
		// D5 You may not ally with clan you are battle with.
		// D6 Only the clan leader may apply for withdraw from alliance.
		// DD No response. Invitation to join an
		// D7 Alliance leaders cannot withdraw.
		// D9 Different Alliance
		// EB alliance information
		// Ec alliance name $s1
		// ee alliance leader: $s2 of $s1
		// ef affilated clans: total $s1 clan(s)
		// f6 you have already joined an alliance
		// f9 you cannot new alliance 10 days
		// fd cannot accept. clan ally is register as enemy during siege battle.
		// fe you have invited someone to your alliance.
		// 100 do you wish to withdraw from the alliance
		// 102 enter the name of the clan you wish to expel.
		// 202 do you realy wish to dissolve the alliance
		// 502 you have accepted alliance
		// 602 you have failed to invite a clan into the alliance
		// 702 you have withdraw

		if (!player.isClanLeader()) {
			player.sendPacket(Msg.ONLY_CLAN_LEADERS_MAY_CREATE_ALLIANCES);
			return;
		}
		if (player.getClan().getAllyId() != 0) {
			player.sendPacket(Msg.YOU_ALREADY_BELONG_TO_ANOTHER_ALLIANCE);
			return;
		}
		if (allyName.length() > 16) {
			player.sendPacket(Msg.INCORRECT_LENGTH_FOR_AN_ALLIANCE_NAME);
			return;
		}
		if (!Util.isMatchingRegexp(allyName, Config.ALLY_NAME_TEMPLATE)) {
			player.sendPacket(Msg.INCORRECT_ALLIANCE_NAME);
			return;
		}
		if (player.getClan().getLevel() < 5) {
			player.sendPacket(Msg.TO_CREATE_AN_ALLIANCE_YOUR_CLAN_MUST_BE_LEVEL_5_OR_HIGHER);
			return;
		}
		if (ClanTable.getInstance().getAllyByName(allyName) != null) {
			player.sendPacket(Msg.THIS_ALLIANCE_NAME_ALREADY_EXISTS);
			return;
		}
		if (!player.getClan().canCreateAlly()) {
			player.sendPacket(Msg.YOU_CANNOT_CREATE_A_NEW_ALLIANCE_WITHIN_1_DAY_AFTER_DISSOLUTION);
			return;
		}

		Alliance alliance = ClanTable.getInstance().createAlliance(player, allyName);
		if (alliance == null)
			return;

		player.broadcastCharInfo();
		player.sendMessage("Alliance " + allyName + " has been created.");
	}

	private void dissolveAlly(Player player) {
		if (player == null || player.getAlliance() == null)
			return;

		if (!player.isAllyLeader()) {
			player.sendPacket(Msg.FEATURE_AVAILABLE_TO_ALLIANCE_LEADERS_ONLY);
			return;
		}

		if (player.getAlliance().getMembersCount() > 1) {
			player.sendPacket(Msg.YOU_HAVE_FAILED_TO_DISSOLVE_THE_ALLIANCE);
			return;
		}

		ClanTable.getInstance().dissolveAlly(player);
	}
}
// EOF java/l2ft/gameserver/model/instances/VillageMasterInstance.java