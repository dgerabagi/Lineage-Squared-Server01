//
// /java/l2ft/gameserver/model/actor/instances/player/StackClass.java
//

package l2ft.gameserver.model.actor.instances.player;

import l2ft.gameserver.model.DeathPenalty;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.actor.instances.player.Unlocks.UnlockedClass;
import l2ft.gameserver.model.base.ClassId;
import l2ft.gameserver.model.base.Experience;
import l2ft.gameserver.model.base.Race;

/**
 * Manages the player's "stacked" class system: main and secondary within the
 * same "slot."
 * The main is typically the base class, the secondary must be same race except
 * dwarves, who
 * can be set as secondary for any race.
 */
public class StackClass {
	private Player _owner;
	private int _level = 1;
	private long _exp = 0;
	private double _Hp = 1, _Mp = 1, _Cp = 1;
	private DeathPenalty _dp;
	private int _classId = 0;

	private int _secondaryClass = 1;
	private int _secondaryLevel = 1;
	private long _secondaryExp = 0;

	// track pre‐delevel exp/level in case the user might forcibly revert
	private long _expBeforeDelevel;
	private int _levelBeforeDelevel;

	public StackClass(Player owner) {
		_owner = owner;
	}

	public int getSecondaryClass() {
		return _secondaryClass;
	}

	public void setSecondaryClass(int classId) {
		_secondaryClass = classId;
	}

	public int getSecondaryLevel() {
		return _secondaryLevel;
	}

	public long getSecondaryExp() {
		return _secondaryExp;
	}

	public void setExpWithoutSave(long val) {
		val = Math.max(val, 0);
		val = Math.min(val, getMaxExp());
		_secondaryExp = val;
		if (_secondaryClass != 1)
			_secondaryLevel = Experience.getLevel(_secondaryExp);
	}

	public long getExpBeforeDelevel() {
		return _expBeforeDelevel;
	}

	public int getLevelBeforeDelevel() {
		return _levelBeforeDelevel;
	}

	public void setLevelBeforeDelevel(int lv) {
		_levelBeforeDelevel = lv;
	}

	public void setExpBeforeDelevel(long ex) {
		_expBeforeDelevel = ex;
	}

	/**
	 * Sets secondary exp. The old code forcibly called rewardSkills() on each new
	 * level,
	 * which auto‐learned skills. That can be suppressed if needed.
	 */
	public void setSecondaryExp(long val) {
		val = Math.max(val, 0);
		val = Math.min(val, getMaxExp());
		_secondaryExp = val;
		int currentLevel = _secondaryLevel;
		_secondaryLevel = Experience.getLevel(_secondaryExp);
		if (currentLevel != _secondaryLevel) {
			_owner.getUnlocks().changeClassVars(getSecondaryClass(), _secondaryLevel, _secondaryExp);
			// We removed the old forced _owner.rewardSkills(true) to avoid auto‐learning
			// forcibly.
		}
	}

	public void addExp(long val) {
		int oldSecLevel = getSecondaryLevel();
		if (_secondaryClass != 1)
			setSecondaryExp(_secondaryExp + getExpPenalty(val));

		int oldFirstLevel = getLevel();
		setFirstExp(_exp + val);

		// If Kamael, do nothing special
		if (isKamael())
			return;

		// If main or secondary gained a level, update the DB unlock row
		if (oldSecLevel < getSecondaryLevel() || oldFirstLevel < getLevel()) {
			UnlockedClass first = _owner.getUnlocks().getUnlockedClass(getFirstClassId());
			if (first != null)
				first.setExp(getExp());
			UnlockedClass sec = _owner.getUnlocks().getUnlockedClass(getSecondaryClass());
			if (sec != null)
				sec.setExp(getSecondaryExp());
			checkCertification();
		}
	}

	public void setFirstClassId(int classId) {
		_classId = classId;
	}

	public int getFirstClassId() {
		return _classId;
	}

	public long getMaxExp() {
		return Experience.LEVEL[Experience.getMaxLevel() + 1] - 1;
	}

	public long getExp() {
		return _exp;
	}

	public int getLevel() {
		return _level;
	}

	public void setLevel(int level) {
		_level = level;
	}

	public void setFirstExp(long val) {
		val = Math.max(val, 0);
		val = Math.min(val, getMaxExp());
		_exp = val;
		_level = Experience.getLevel(_exp);
	}

	public void setHp(double hpValue) {
		_Hp = hpValue;
	}

	public double getHp() {
		return _Hp;
	}

	public void setMp(double mpValue) {
		_Mp = mpValue;
	}

	public double getMp() {
		return _Mp;
	}

	public void setCp(double cpValue) {
		_Cp = cpValue;
	}

	public double getCp() {
		return _Cp;
	}

	public DeathPenalty getDeathPenalty(Player player) {
		if (_dp == null)
			_dp = new DeathPenalty(player, 0);
		return _dp;
	}

	public void setDeathPenalty(DeathPenalty dp) {
		_dp = dp;
	}

	private long getExpPenalty(long exp) {
		if (getSecondaryLevel() <= getLevel() - 15)
			return 0;
		int lvlDiff = getLevel() - getSecondaryLevel();
		if (lvlDiff >= 10 && lvlDiff <= 14)
			return (long) (exp * 0.3);
		return exp;
	}

	private void checkCertification() {
		int charsForCert = 0;
		for (Object obj : _owner.getUnlocks().getAllUnlocks().getValues()) {
			UnlockedClass uc = (UnlockedClass) obj;
			int realLvl = Experience.getLevel(uc.getExp());
			if (realLvl >= 70 && ClassId.values()[uc.getId()].getLevel() == 4)
				charsForCert++;
		}
		int currentCert = _owner.getVarInt("certification", 0);
		if (currentCert < Math.floor(charsForCert / 5.0)) {
			_owner.getInventory().addItem(10280, 1);
			if (currentCert % 2 == 1)
				_owner.getInventory().addItem(10612, 1);
			_owner.setVar("certification", (int) Math.floor(charsForCert / 5.0), -1);
		}
	}

	public boolean isKamael() {
		return ClassId.values()[getFirstClassId()].getRace().equals(Race.kamael);
	}

	@Override
	public String toString() {
		return ClassId.VALUES[_classId] + " lvl " + getLevel();
	}
}
