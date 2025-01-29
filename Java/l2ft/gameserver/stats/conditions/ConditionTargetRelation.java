package l2ft.gameserver.stats.conditions;

import l2ft.gameserver.model.Creature;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.base.TeamType;
import l2ft.gameserver.model.entity.events.impl.ForgottenBattlegroundsEvent;
import l2ft.gameserver.model.entity.events.impl.IslandAssaultEvent;
import l2ft.gameserver.stats.Env;

public class ConditionTargetRelation extends Condition {
	private final Relation _state;

	public static enum Relation {
		Neutral,
		Friend,
		Enemy;
	}

	public ConditionTargetRelation(Relation state) {
		_state = state;
	}

	@Override
	protected boolean testImpl(Env env) {
		return getRelation(env.character, env.target) == _state;
	}

	public static Relation getRelation(Creature activeChar, Creature aimingTarget) {
		if (activeChar.isPlayable() && activeChar.getPlayer() != null) {
			Player player = activeChar.getPlayer();

			if (aimingTarget.isPlayable() && aimingTarget.getPlayer() != null) {
				Player target = aimingTarget.getPlayer();

				// Check Battleground event:
				ForgottenBattlegroundsEvent event = player.getEvent(ForgottenBattlegroundsEvent.class);
				if (event != null && event.isParticipant(player) && event.isParticipant(target)) {
					TeamType atkTeam = event.getTeamOfPlayer(player);
					TeamType tarTeam = event.getTeamOfPlayer(target);
					if (atkTeam != TeamType.NONE && tarTeam != TeamType.NONE && atkTeam != tarTeam)
						return Relation.Enemy; // They are enemies in the battleground
				}

				// NEW - Check Island Assault event
				IslandAssaultEvent iaEvent = player.getEvent(IslandAssaultEvent.class);
				if (iaEvent != null && iaEvent.isParticipant(player) && iaEvent.isParticipant(target)) {
					TeamType atkTeam = iaEvent.getTeamOfPlayer(player);
					TeamType tarTeam = iaEvent.getTeamOfPlayer(target);
					if (atkTeam != TeamType.NONE && tarTeam != TeamType.NONE && atkTeam != tarTeam)
						return Relation.Enemy; // They are enemies in Island Assault
				}

				if (player == target || player.getParty() != null && player.getParty() == target.getParty())
					return Relation.Friend;
				if (player.isInOlympiadMode() && player.isOlympiadCompStart()
						&& player.getOlympiadSide() == target.getOlympiadSide())
					return Relation.Friend;
				if (player.getTeam() != TeamType.NONE && target.getTeam() != TeamType.NONE
						&& player.getTeam() == target.getTeam())
					return Relation.Friend;
				if (activeChar.isInZoneBattle())
					return Relation.Enemy;
				if (player.getClanId() != 0 && player.getClanId() == target.getClanId())
					return Relation.Friend;
				if (activeChar.isInZonePeace())
					return Relation.Neutral;
				if (player.atMutualWarWith(target))
					return Relation.Enemy;
				if (target.getKarma() > 0)
					return Relation.Enemy;
			}
		}
		return Relation.Neutral;
	}
}