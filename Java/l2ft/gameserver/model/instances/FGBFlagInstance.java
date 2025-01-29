package l2ft.gameserver.model.instances;

import l2ft.gameserver.model.Creature;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.Skill;
import l2ft.gameserver.model.base.TeamType;
import l2ft.gameserver.model.entity.events.GlobalEvent;
import l2ft.gameserver.model.instances.residences.SiegeFlagInstance;
import l2ft.gameserver.model.pledge.Clan;
import l2ft.gameserver.templates.npc.NpcTemplate;
import l2ft.gameserver.model.entity.events.impl.ForgottenBattlegroundsEvent;
import l2ft.gameserver.model.entity.events.impl.IslandAssaultEvent;

public class FGBFlagInstance extends SiegeFlagInstance {
    // Instead of a ForgottenBattlegroundsEvent, store a generic GlobalEvent
    private GlobalEvent _event;
    private TeamType _flagOwnerTeam;
    private boolean _isRearFlag;
    private boolean _isFinalFlag;

    // Universal constructor for *any* event that extends GlobalEvent
    public FGBFlagInstance(int objectId, NpcTemplate template, GlobalEvent event,
            TeamType ownerTeam, boolean isRearFlag, boolean isFinalFlag) {
        super(objectId, template);
        _event = event;
        _flagOwnerTeam = ownerTeam;
        this._isRearFlag = isRearFlag;
        this._isFinalFlag = isFinalFlag;
    }

    // Convenience 5-arg constructor if you prefer
    public FGBFlagInstance(int objectId, NpcTemplate template, GlobalEvent event,
            TeamType ownerTeam, boolean isRearFlag) {
        this(objectId, template, event, ownerTeam, isRearFlag, false);
    }

    public boolean isRearFlag() {
        return _isRearFlag;
    }

    public boolean isFinalFlag() {
        return _isFinalFlag;
    }

    @Override
    protected void onDeath(Creature killer) {
        super.onDeath(killer);
        if (_event == null)
            return;

        Player destroyer = killer != null ? killer.getPlayer() : null;

        // If the event is Forgotten BG
        if (_event instanceof ForgottenBattlegroundsEvent) {
            ((ForgottenBattlegroundsEvent) _event).onFlagDestroyed(_flagOwnerTeam, destroyer);
        }
        // If the event is Island Assault
        else if (_event instanceof IslandAssaultEvent) {
            ((IslandAssaultEvent) _event).onFlagDestroyed(_flagOwnerTeam, destroyer);
        }
    }

    @Override
    public boolean isAttackable(Creature attacker) {
        if (_event == null || !_event.isInProgress())
            return false;

        Player player = attacker.getPlayer();
        if (player == null)
            return false;

        TeamType playerTeam = TeamType.NONE;

        // If the event is Forgotten BG
        if (_event instanceof ForgottenBattlegroundsEvent) {
            playerTeam = ((ForgottenBattlegroundsEvent) _event).getTeamOfPlayer(player);
        } else if (_event instanceof IslandAssaultEvent) {
            playerTeam = ((IslandAssaultEvent) _event).getTeamOfPlayer(player);
        }

        if (playerTeam == TeamType.NONE || playerTeam == _flagOwnerTeam)
            return false;

        // If final flag, always attackable
        if (isFinalFlag()) {
            return true;
        }
        // Otherwise, only attackable if not a rear flag
        return !isRearFlag();
    }

    @Override
    public boolean isAutoAttackable(Creature attacker) {
        return isAttackable(attacker);
    }

    @Override
    public String getName() {
        if (isFinalFlag()) {
            if (_flagOwnerTeam == TeamType.BLUE) {
                return "Final Blue Flag";
            } else if (_flagOwnerTeam == TeamType.RED) {
                return "Final Red Flag";
            }
        } else {
            if (_flagOwnerTeam == TeamType.BLUE) {
                return isRearFlag() ? "Blue Rear Flag" : "Blue Front Flag";
            } else if (_flagOwnerTeam == TeamType.RED) {
                return isRearFlag() ? "Red Rear Flag" : "Red Front Flag";
            }
        }
        return "Neutral Flag";
    }

    @Override
    public Clan getClan() {
        return null;
    }

    @Override
    public void onReduceCurrentHp(double damage, Creature attacker, Skill skill, boolean awake, boolean standUp,
            boolean directHp) {
        super.onReduceCurrentHp(damage, attacker, skill, awake, standUp, directHp);
    }

    public TeamType getTeam() {
        return _flagOwnerTeam;
    }
}
