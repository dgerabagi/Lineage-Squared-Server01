package l2ft.gameserver.ai;

import static l2ft.gameserver.ai.CtrlIntention.AI_INTENTION_ACTIVE;
import static l2ft.gameserver.ai.CtrlIntention.AI_INTENTION_ATTACK;
import static l2ft.gameserver.ai.CtrlIntention.AI_INTENTION_CAST;
import static l2ft.gameserver.ai.CtrlIntention.AI_INTENTION_FOLLOW;
import static l2ft.gameserver.ai.CtrlIntention.AI_INTENTION_INTERACT;
import static l2ft.gameserver.ai.CtrlIntention.AI_INTENTION_PICK_UP;

import java.util.concurrent.ScheduledFuture;

import l2ft.commons.threading.RunnableImpl;
import l2ft.gameserver.ThreadPoolManager;
import l2ft.gameserver.ai.PlayableAI.ExecuteFollow;
import l2ft.gameserver.ai.PlayableAI.ThinkFollow;
import l2ft.gameserver.cache.Msg;
import l2ft.gameserver.geodata.GeoEngine;
import l2ft.gameserver.model.Creature;
import l2ft.gameserver.model.GameObject;
import l2ft.gameserver.model.Playable;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.Skill;
import l2ft.gameserver.model.Skill.NextAction;
import l2ft.gameserver.model.Skill.SkillType;
import l2ft.gameserver.model.Summon;
import l2ft.gameserver.network.l2.components.SystemMsg;
import l2ft.gameserver.network.l2.s2c.MyTargetSelected;
import l2ft.gameserver.utils.Location;

/**
 * Revised PlayableAI that checks LOS in thinkCast(...):
 * - if no LOS => forcibly do Attack(...), letting official AI chase
 * - otherwise do normal cast logic
 */
public class PlayableAI extends CharacterAI {
	private volatile int thinking = 0; // to prevent recursive thinking

	protected Object _intention_arg0 = null, _intention_arg1 = null;
	protected Skill _skill;

	private nextAction _nextAction;
	private Object _nextAction_arg0;
	private Object _nextAction_arg1;
	private boolean _nextAction_arg2;
	private boolean _nextAction_arg3;

	protected boolean _forceUse;
	protected boolean _dontMove; // used in Attack(...) or Cast(...) calls

	private ScheduledFuture<?> _followTask;

	public PlayableAI(Playable actor) {
		super(actor);
	}

	public enum nextAction {
		ATTACK,
		CAST,
		MOVE,
		REST,
		PICKUP,
		INTERACT,
		COUPLE_ACTION
	}

	@Override
	public void changeIntention(CtrlIntention intention, Object arg0, Object arg1) {
		super.changeIntention(intention, arg0, arg1);
		_intention_arg0 = arg0;
		_intention_arg1 = arg1;
	}

	@Override
	public void setIntention(CtrlIntention intention, Object arg0, Object arg1) {
		_intention_arg0 = null;
		_intention_arg1 = null;
		super.setIntention(intention, arg0, arg1);
	}

	@Override
	protected void onIntentionCast(Skill skill, Creature target) {
		_skill = skill;
		super.onIntentionCast(skill, target);
	}

	@Override
	public void setNextAction(nextAction action, Object arg0, Object arg1, boolean arg2, boolean arg3) {
		_nextAction = action;
		_nextAction_arg0 = arg0;
		_nextAction_arg1 = arg1;
		_nextAction_arg2 = arg2;
		_nextAction_arg3 = arg3;
	}

	public boolean setNextIntention() {
		nextAction nextAction = _nextAction;
		Object nextAction_arg0 = _nextAction_arg0;
		Object nextAction_arg1 = _nextAction_arg1;
		boolean nextAction_arg2 = _nextAction_arg2;
		boolean nextAction_arg3 = _nextAction_arg3;

		Playable actor = getActor();
		if (nextAction == null || actor.isActionsDisabled())
			return false;

		Skill skill;
		Creature target;
		GameObject object;

		switch (nextAction) {
			case ATTACK:
				if (nextAction_arg0 == null)
					return false;
				target = (Creature) nextAction_arg0;
				_forceUse = nextAction_arg2;
				_dontMove = nextAction_arg3;
				clearNextAction();
				setIntention(AI_INTENTION_ATTACK, target);
				break;

			case CAST:
				if (nextAction_arg0 == null || nextAction_arg1 == null)
					return false;
				skill = (Skill) nextAction_arg0;
				target = (Creature) nextAction_arg1;
				_forceUse = nextAction_arg2;
				_dontMove = nextAction_arg3;
				clearNextAction();
				if (!skill.checkCondition(actor, target, _forceUse, _dontMove, true)) {
					if (skill.getNextAction() == NextAction.ATTACK && !actor.equals(target)) {
						setNextAction(nextAction.ATTACK, target, null, _forceUse, false);
						return setNextIntention();
					}
					return false;
				}
				setIntention(AI_INTENTION_CAST, skill, target);
				break;

			case MOVE:
				if (nextAction_arg0 == null || nextAction_arg1 == null)
					return false;
				Location loc = (Location) nextAction_arg0;
				Integer offset = (Integer) nextAction_arg1;
				clearNextAction();
				actor.moveToLocation(loc, offset, nextAction_arg2);
				break;

			case REST:
				actor.sitDown(null);
				break;

			case INTERACT:
				if (nextAction_arg0 == null)
					return false;
				object = (GameObject) nextAction_arg0;
				clearNextAction();
				onIntentionInteract(object);
				break;

			case PICKUP:
				if (nextAction_arg0 == null)
					return false;
				object = (GameObject) nextAction_arg0;
				clearNextAction();
				onIntentionPickUp(object);
				break;

			case COUPLE_ACTION:
				if (nextAction_arg0 == null || nextAction_arg1 == null)
					return false;
				target = (Creature) nextAction_arg0;
				Integer socialId = (Integer) nextAction_arg1;
				_forceUse = nextAction_arg2;
				_nextAction = null;
				clearNextAction();
				onIntentionCoupleAction((Player) target, socialId);
				break;

			default:
				return false;
		}
		return true;
	}

	@Override
	public void clearNextAction() {
		_nextAction = null;
		_nextAction_arg0 = null;
		_nextAction_arg1 = null;
		_nextAction_arg2 = false;
		_nextAction_arg3 = false;
	}

	@Override
	protected void onEvtFinishCasting() {
		if (!setNextIntention())
			setIntention(AI_INTENTION_ACTIVE);
	}

	@Override
	protected void onEvtReadyToAct() {
		if (!setNextIntention())
			onEvtThink();
	}

	@Override
	protected void onEvtArrived() {
		if (!setNextIntention()) {
			if (getIntention() == AI_INTENTION_INTERACT || getIntention() == AI_INTENTION_PICK_UP)
				onEvtThink();
			else
				changeIntention(AI_INTENTION_ACTIVE, null, null);
		}
	}

	@Override
	protected void onEvtArrivedTarget() {
		switch (getIntention()) {
			case AI_INTENTION_ATTACK:
				thinkAttack(false);
				break;
			case AI_INTENTION_CAST:
				thinkCast(false);
				break;
			case AI_INTENTION_FOLLOW:
				thinkFollow();
				break;
			default:
				onEvtThink();
				break;
		}
	}

	@Override
	protected final void onEvtThink() {
		Playable actor = getActor();
		if (actor.isActionsDisabled())
			return;

		try {
			if (thinking++ > 1)
				return;

			switch (getIntention()) {
				case AI_INTENTION_ACTIVE:
					thinkActive();
					break;
				case AI_INTENTION_ATTACK:
					thinkAttack(true);
					break;
				case AI_INTENTION_CAST:
					thinkCast(true);
					break;
				case AI_INTENTION_PICK_UP:
					thinkPickUp();
					break;
				case AI_INTENTION_INTERACT:
					thinkInteract();
					break;
				case AI_INTENTION_FOLLOW:
					thinkFollow();
					break;
				case AI_INTENTION_COUPLE_ACTION:
					thinkCoupleAction((Player) _intention_arg0, (Integer) _intention_arg1, false);
					break;
			}
		} catch (Exception e) {
			_log.error("", e);
		} finally {
			thinking--;
		}
	}

	protected void thinkActive() {
		// Override if needed
	}

	protected void thinkFollow() {
		Playable actor = getActor();

		Creature target = (Creature) _intention_arg0;
		Integer offset = (Integer) _intention_arg1;

		// too far or invalid => fail
		if (target == null || target.isAlikeDead() || actor.getDistance(target) > 4000 || offset == null) {
			clientActionFailed();
			return;
		}

		// already following?
		if (actor.isFollow && actor.getFollowTarget() == target) {
			clientActionFailed();
			return;
		}

		// if close or can't move
		if (actor.isInRange(target, offset + 20) || actor.isMovementDisabled())
			clientActionFailed();

		if (_followTask != null) {
			_followTask.cancel(false);
			_followTask = null;
		}

		_followTask = ThreadPoolManager.getInstance().schedule(new ThinkFollow(), 250L);
	}

	protected class ThinkFollow extends RunnableImpl {
		@Override
		public void runImpl() {
			Playable actor = getActor();

			if (getIntention() != AI_INTENTION_FOLLOW) {
				// e.g. pet stops follow
				if ((actor.isPet() || actor.isSummon()) && getIntention() == AI_INTENTION_ACTIVE)
					((Summon) actor).setFollowMode(false);
				return;
			}

			Creature target = (Creature) _intention_arg0;
			int offset = _intention_arg1 instanceof Integer ? (Integer) _intention_arg1 : 0;

			if (target == null || target.isAlikeDead() || actor.getDistance(target) > 4000) {
				setIntention(CtrlIntention.AI_INTENTION_ACTIVE);
				return;
			}

			Player player = actor.getPlayer();
			if (player == null || player.isLogoutStarted()
					|| ((actor.isPet() || actor.isSummon()) && player.getPet() != actor)) {
				setIntention(CtrlIntention.AI_INTENTION_ACTIVE);
				return;
			}

			if (!actor.isInRange(target, offset + 20) && (!actor.isFollow || actor.getFollowTarget() != target))
				actor.followToCharacter(target, offset, false);

			_followTask = ThreadPoolManager.getInstance().schedule(this, 250L);
		}
	}

	protected class ExecuteFollow extends RunnableImpl {
		private Creature _target;
		private int _range;

		public ExecuteFollow(Creature target, int range) {
			_target = target;
			_range = range;
		}

		@Override
		public void runImpl() {
			if (_target.isDoor())
				_actor.moveToLocation(_target.getLoc(), 40, true);
			else
				_actor.followToCharacter(_target, _range, true);
		}
	}

	@Override
	protected void onIntentionInteract(GameObject object) {
		Playable actor = getActor();

		if (actor.isActionsDisabled()) {
			setNextAction(nextAction.INTERACT, object, null, false, false);
			clientActionFailed();
			return;
		}

		clearNextAction();
		changeIntention(AI_INTENTION_INTERACT, object, null);
		onEvtThink();
	}

	@Override
	protected void onIntentionCoupleAction(Player player, Integer socialId) {
		clearNextAction();
		changeIntention(CtrlIntention.AI_INTENTION_COUPLE_ACTION, player, socialId);
		onEvtThink();
	}

	protected void thinkInteract() {
		Playable actor = getActor();
		GameObject target = (GameObject) _intention_arg0;

		if (target == null) {
			setIntention(AI_INTENTION_ACTIVE);
			return;
		}

		int range = (int) (Math.max(30, actor.getMinDistance(target)) + 20);

		if (actor.isInRangeZ(target, range)) {
			if (actor.isPlayer())
				((Player) actor).doInteract(target);
			setIntention(AI_INTENTION_ACTIVE);
		} else {
			actor.moveToLocation(target.getLoc(), 40, true);
			setNextAction(nextAction.INTERACT, target, null, false, false);
		}
	}

	@Override
	protected void onIntentionPickUp(GameObject object) {
		Playable actor = getActor();

		if (actor.isActionsDisabled()) {
			setNextAction(nextAction.PICKUP, object, null, false, false);
			clientActionFailed();
			return;
		}

		clearNextAction();
		changeIntention(AI_INTENTION_PICK_UP, object, null);
		onEvtThink();
	}

	protected void thinkPickUp() {
		final Playable actor = getActor();
		final GameObject target = (GameObject) _intention_arg0;

		if (target == null) {
			setIntention(AI_INTENTION_ACTIVE);
			return;
		}

		if (actor.isInRange(target, 30) && Math.abs(actor.getZ() - target.getZ()) < 50) {
			if (actor.isPlayer() || actor.isPet())
				actor.doPickupItem(target);
			setIntention(AI_INTENTION_ACTIVE);
		} else {
			ThreadPoolManager.getInstance().execute(new RunnableImpl() {
				@Override
				public void runImpl() {
					actor.moveToLocation(target.getLoc(), 10, true);
					setNextAction(nextAction.PICKUP, target, null, false, false);
				}
			});
		}
	}

	protected void thinkAttack(boolean checkRange) {
		Playable actor = getActor();

		Player player = actor.getPlayer();
		if (player == null) {
			setIntention(AI_INTENTION_ACTIVE);
			return;
		}

		if (actor.isActionsDisabled() || actor.isAttackingDisabled()) {
			actor.sendActionFailed();
			return;
		}

		boolean isPosessed = actor instanceof Summon && ((Summon) actor).isDepressed();

		Creature attack_target = getAttackTarget();
		if (attack_target == null || attack_target.isDead() ||
				!isPosessed
						&& !(_forceUse ? attack_target.isAttackable(actor) : attack_target.isAutoAttackable(actor))) {
			setIntention(AI_INTENTION_ACTIVE);
			actor.sendActionFailed();
			return;
		}

		int range = actor.getPhysicalAttackRange();
		if (range < 10)
			range = 10;

		// If bow or other weapon is used, range might be large.
		// But we add min distance:
		range += actor.getMinDistance(attack_target);

		boolean canSee = GeoEngine.canSeeTarget(actor, attack_target, false);

		// --------------------------------------------------------
		// NEW BLOCK: If we can't see target, but user called Attack(..., false, false),
		// do a melee-like chase instead of bailing out.
		// --------------------------------------------------------
		if (!canSee) {
			// If the AI call had forceUse=false, dontMove=false => we interpret that as
			// "run around to see the target"
			if (!_forceUse && !_dontMove) {
				ThreadPoolManager.getInstance().execute(new ExecuteFollow(attack_target, range - 20));
				return;
			}

			// Otherwise do the old "cannot see => fail"
			actor.sendPacket(SystemMsg.CANNOT_SEE_TARGET);
			setIntention(AI_INTENTION_ACTIVE);
			actor.sendActionFailed();
			return;
		}
		// --------------------------------------------------------
		// END NEW BLOCK
		// --------------------------------------------------------

		// If in range, do actual doAttack:
		if (actor.isInRangeZ(attack_target, range)) {
			clientStopMoving(false);
			actor.doAttack(attack_target);
		} else if (!_dontMove) {
			// Let the follow logic chase
			ThreadPoolManager.getInstance().execute(new ExecuteFollow(attack_target, range - 20));
		} else
			actor.sendActionFailed();
	}

	/**
	 * **Here** is our modification:
	 * If no LOS => forcibly do Attack(...), causing official AI chase.
	 * Otherwise do normal cast logic.
	 */
	protected void thinkCast(boolean checkRange) {
		Playable actor = getActor();
		if (actor == null) {
			setIntention(CtrlIntention.AI_INTENTION_ACTIVE);
			return;
		}

		Player player = actor.getPlayer();
		if (player == null) {
			setIntention(CtrlIntention.AI_INTENTION_ACTIVE);
			return;
		}

		// Disabled (stun, sleep, etc.)
		if (actor.isActionsDisabled()) {
			actor.sendActionFailed();
			return;
		}

		if (_skill == null) {
			setIntention(CtrlIntention.AI_INTENTION_ACTIVE);
			actor.sendActionFailed();
			return;
		}

		// For craft or toggle => skip range checks
		if (_skill.getSkillType() == SkillType.CRAFT || _skill.isToggle()) {
			if (_skill.checkCondition(actor, getAttackTarget(), _forceUse, _dontMove, true))
				actor.doCast(_skill, getAttackTarget(), _forceUse);
			return;
		}

		Creature target = getAttackTarget();
		if (target == null || (target.isDead() != _skill.getCorpse() && !_skill.isNotTargetAoE())) {
			setIntention(CtrlIntention.AI_INTENTION_ACTIVE);
			actor.sendActionFailed();
			return;
		}

		// If not checking range => final doCast
		if (!checkRange) {
			if (_skill.getNextAction() == NextAction.ATTACK && !actor.equals(target))
				setNextAction(nextAction.ATTACK, target, null, _forceUse, false);
			else
				clearNextAction();

			clientStopMoving();
			if (_skill.checkCondition(actor, target, _forceUse, _dontMove, true))
				actor.doCast(_skill, target, _forceUse);
			else {
				setNextIntention();
				if (getIntention() == AI_INTENTION_ATTACK)
					thinkAttack(true);
			}
			return;
		}

		// Checking distance + LOS
		int range = actor.getMagicalAttackRange(_skill);
		if (range < 10)
			range = 10;

		// Possibly skip LOS checks for certain skill types
		boolean noLosNeeded = false;
		if (_skill.getSkillType() == SkillType.TAKECASTLE
				|| _skill.getSkillType() == SkillType.TAKEFORTRESS
				|| actor.isFlying()) {
			noLosNeeded = true;
		}

		boolean canSee = noLosNeeded || GeoEngine.canSeeTarget(actor, target, false);

		// If no LOS => forcibly do Attack => chase
		if (!canSee) {
			actor.getAI().Attack(target, false, false);
			return; // Done
		}

		// If we do have LOS, proceed normal
		boolean noRangeSkill = (_skill.getCastRange() == 32767);

		range += actor.getMinDistance(target);

		if (actor.isFakeDeath())
			actor.breakFakeDeath();

		// If in range => cast
		if (actor.isInRangeZ(target, range) || noRangeSkill) {
			if (_skill.getNextAction() == NextAction.ATTACK && !actor.equals(target))
				setNextAction(nextAction.ATTACK, target, null, _forceUse, false);
			else
				clearNextAction();

			if (_skill.checkCondition(actor, target, _forceUse, _dontMove, true)) {
				clientStopMoving(false);
				actor.doCast(_skill, target, _forceUse);
			} else {
				setNextIntention();
				if (getIntention() == AI_INTENTION_ATTACK)
					thinkAttack(true);
			}
		} else if (!_dontMove) {
			// out of range => chase
			ThreadPoolManager.getInstance().execute(new ExecuteFollow(target, range - 20));
		} else {
			actor.sendPacket(Msg.YOUR_TARGET_IS_OUT_OF_RANGE);
			setIntention(AI_INTENTION_ACTIVE);
			actor.sendActionFailed();
		}
	}

	protected void thinkCoupleAction(Player target, Integer socialId, boolean cancel) {
		// NOP
	}

	@Override
	protected void onEvtDead(Creature killer) {
		clearNextAction();
		super.onEvtDead(killer);
	}

	@Override
	protected void onEvtFakeDeath() {
		clearNextAction();
		super.onEvtFakeDeath();
	}

	public void lockTarget(Creature target) {
		Playable actor = getActor();

		if (target == null || target.isDead())
			actor.setAggressionTarget(null);
		else if (actor.getAggressionTarget() == null) {
			GameObject actorStoredTarget = actor.getTarget();
			actor.setAggressionTarget(target);
			actor.setTarget(target);

			clearNextAction();

			// We do NOT forcibly break attack/cast
			// e.g. no setAttackTarget(...) forcibly here
			// just swap the "visible" target
			if (actorStoredTarget != target)
				actor.sendPacket(new MyTargetSelected(target.getObjectId(), 0));
		}
	}

	@Override
	public void Attack(GameObject target, boolean forceUse, boolean dontMove) {
		Playable actor = getActor();
		if (target.isCreature() && (actor.isActionsDisabled() || actor.isAttackingDisabled())) {
			setNextAction(nextAction.ATTACK, target, null, forceUse, false);
			actor.sendActionFailed();
			return;
		}

		_dontMove = dontMove;
		_forceUse = forceUse;
		clearNextAction();
		setIntention(AI_INTENTION_ATTACK, target);
	}

	@Override
	public void Cast(Skill skill, Creature target, boolean forceUse, boolean dontMove) {
		Playable actor = getActor();
		if (skill.altUse() || skill.isToggle()) {
			// Some item/alt skill => ignore standard checks
			if ((skill.isToggle() || skill.isHandler()) &&
					(actor.isOutOfControl() || actor.isStunned() || actor.isSleeping() || actor.isParalyzed()
							|| actor.isAlikeDead())) {
				clientActionFailed();
			} else {
				actor.altUseSkill(skill, target);
			}
			return;
		}

		// if cannot cast, queue it
		if (actor.isActionsDisabled()) {
			setNextAction(nextAction.CAST, skill, target, forceUse, dontMove);
			clientActionFailed();
			return;
		}

		_forceUse = forceUse;
		_dontMove = dontMove;
		clearNextAction();
		setIntention(CtrlIntention.AI_INTENTION_CAST, skill, target);
	}

	@Override
	public Playable getActor() {
		return (Playable) super.getActor();
	}
}
