package l2ft.gameserver.model.instances.residences;

import org.apache.commons.lang3.StringUtils;
import l2ft.gameserver.model.Creature;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.Skill;
import l2ft.gameserver.model.base.TeamType;
import l2ft.gameserver.model.entity.events.objects.SiegeClanObject;
import l2ft.gameserver.model.instances.NpcInstance;
import l2ft.gameserver.model.pledge.Clan;
import l2ft.gameserver.network.l2.components.SystemMsg;
import l2ft.gameserver.templates.npc.NpcTemplate;

public class SiegeFlagInstance extends NpcInstance {
	private SiegeClanObject _owner;
	private long _lastAnnouncedAttackedTime = 0;
	private TeamType _team = TeamType.NONE;

	public SiegeFlagInstance(int objectId, NpcTemplate template) {
		super(objectId, template);
		setHasChatWindow(false);
	}

	@Override
	public String getName() {
		if (_owner != null && _owner.getClan() != null)
			return _owner.getClan().getName();
		return super.getName();
	}

	@Override
	public Clan getClan() {
		return _owner != null ? _owner.getClan() : null;
	}

	@Override
	public String getTitle() {
		return StringUtils.EMPTY;
	}

	public void setTeam(TeamType team) {
		_team = team;
	}

	public TeamType getTeam() {
		return _team;
	}

	@Override
	public boolean isAutoAttackable(Creature attacker) {
		Player player = attacker.getPlayer();
		if (player == null || isInvul())
			return false;

		Clan clan = player.getClan();
		// If there's no owner or clan, just allow attack by default
		if (_owner == null || _owner.getClan() == null)
			return true;
		return clan == null || _owner.getClan() != clan;
	}

	@Override
	public boolean isAttackable(Creature attacker) {
		return true;
	}

	@Override
	protected void onDeath(Creature killer) {
		// Null-check before using _owner
		if (_owner != null)
			_owner.setFlag(null);
		super.onDeath(killer);
	}

	@Override
	protected void onReduceCurrentHp(final double damage, final Creature attacker, Skill skill, final boolean awake,
			final boolean standUp, boolean directHp) {
		// Check _owner and clan before using them
		if (System.currentTimeMillis() - _lastAnnouncedAttackedTime > 120000) {
			_lastAnnouncedAttackedTime = System.currentTimeMillis();
			if (_owner != null && _owner.getClan() != null)
				_owner.getClan().broadcastToOnlineMembers(SystemMsg.YOUR_BASE_IS_BEING_ATTACKED);
		}

		super.onReduceCurrentHp(damage, attacker, skill, awake, standUp, directHp);
	}

	@Override
	public boolean hasRandomAnimation() {
		return false;
	}

	@Override
	public boolean isInvul() {
		return _isInvul;
	}

	@Override
	public boolean isFearImmune() {
		return true;
	}

	@Override
	public boolean isParalyzeImmune() {
		return true;
	}

	@Override
	public boolean isLethalImmune() {
		return true;
	}

	@Override
	public boolean isHealBlocked() {
		return true;
	}

	@Override
	public boolean isEffectImmune() {
		return true;
	}

	public void setClan(SiegeClanObject owner) {
		_owner = owner;
	}
}
