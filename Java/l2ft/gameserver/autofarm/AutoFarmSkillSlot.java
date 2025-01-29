package l2ft.gameserver.autofarm;

import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.Creature;
import l2ft.gameserver.model.Skill;
import l2ft.gameserver.skills.skillclasses.Heal;
import l2ft.gameserver.skills.skillclasses.HealPercent;
import l2ft.gameserver.skills.skillclasses.ChainHeal;
import l2ft.gameserver.skills.skillclasses.ManaHeal;
import l2ft.gameserver.skills.skillclasses.ManaHealPercent;
import l2ft.gameserver.skills.skillclasses.CombatPointHeal;
import l2ft.gameserver.skills.skillclasses.CombatPointHealPercent;

/**
 * Holds data for one “Auto Skill” slot
 */
public class AutoFarmSkillSlot {
    private int skillId;
    private double targetHpPercent = 100.0;
    private double targetMpPercent = 100.0;
    private double targetCpPercent = 100.0; // NEW: CP threshold
    private long reuseDelayMs = 0;
    private long lastUseTimestamp = 0;

    private boolean partySkill = false;
    private boolean allySkill = false;
    private boolean selfBuff = false;
    private boolean autoReuse = false;

    public int getSkillId() {
        return skillId;
    }

    public void setSkillId(int val) {
        skillId = val;
    }

    public double getTargetHpPercent() {
        return targetHpPercent;
    }

    public void setTargetHpPercent(double val) {
        if (val < 0.1)
            val = 0.1;
        if (val > 100.0)
            val = 100.0;
        this.targetHpPercent = val;
    }

    public double getTargetMpPercent() {
        return targetMpPercent;
    }

    public void setTargetMpPercent(double val) {
        if (val < 0.1)
            val = 0.1;
        if (val > 100.0)
            val = 100.0;
        this.targetMpPercent = val;
    }

    public double getTargetCpPercent() {
        return targetCpPercent;
    }

    public void setTargetCpPercent(double val) {
        if (val < 0.1)
            val = 0.1;
        if (val > 100.0)
            val = 100.0;
        this.targetCpPercent = val;
    }

    public long getReuseDelayMs() {
        return reuseDelayMs;
    }

    public void setReuseDelayMs(long ms) {
        if (ms < 0)
            ms = 0;
        reuseDelayMs = ms;
    }

    public boolean isOnCooldown() {
        return (System.currentTimeMillis() < (lastUseTimestamp + reuseDelayMs));
    }

    public void setLastUseNow() {
        lastUseTimestamp = System.currentTimeMillis();
    }

    public boolean isPartySkill() {
        return partySkill;
    }

    public void setPartySkill(boolean val) {
        partySkill = val;
    }

    public boolean isAllySkill() {
        return allySkill;
    }

    public void setAllySkill(boolean val) {
        allySkill = val;
    }

    public boolean isSelfBuff() {
        return selfBuff;
    }

    public void setSelfBuff(boolean val) {
        selfBuff = val;
    }

    public boolean isAutoReuse() {
        return autoReuse;
    }

    public void setAutoReuse(boolean val) {
        autoReuse = val;
    }

    public Skill getSkill(Player player) {
        if (skillId <= 0 || player == null)
            return null;
        return player.getKnownSkill(skillId);
    }

    /**
     * True if it's an HP-based heal skill (like Heal, HealPercent, ChainHeal).
     */
    public boolean isHpHealSkill(Skill s) {
        return (s instanceof Heal) ||
                (s instanceof HealPercent) ||
                (s instanceof ChainHeal);
    }

    /**
     * True if it's a CP-based heal skill (CombatPointHeal, CombatPointHealPercent).
     */
    public boolean isCpHealSkill(Skill s) {
        return (s instanceof CombatPointHeal) ||
                (s instanceof CombatPointHealPercent);
    }

    /**
     * True if it's an MP-based heal skill (ManaHeal, ManaHealPercent, or skill ID
     * 1013 a.k.a. "Recharge").
     */
    public boolean isMpHealSkill(Skill s) {
        return (s instanceof ManaHeal) ||
                (s instanceof ManaHealPercent) ||
                (s.getId() == 1013); // "Recharge"
    }

    /**
     * Our “when to cast” logic:
     *
     * - If partySkill or allySkill => let AutoFarmTask handle. (We just return
     * TRUE.)
     * - If selfBuff => we do 2 branches:
     * 1) If skill is HP/MP/CP heal => check caster’s HP/MP/CP threshold.
     * 2) Else => TOTEM or normal buff => always cast if missing (AutoFarmTask
     * checks if missing if autoReuse).
     *
     * - Otherwise => we do a single-target or offensive skill => requires target’s
     * HP% check.
     */
    public boolean checkConditions(Player player) {
        if (player == null || player.isDead())
            return false;

        Skill skillObj = getSkill(player);
        if (skillObj == null)
            return false;

        // If party or ally => we let the Task do that logic.
        if (partySkill || allySkill) {
            return true;
        }

        if (selfBuff) {
            // If it's any of the Heal skill types, do threshold checks
            if (isHpHealSkill(skillObj)) {
                double hpPerc = (player.getCurrentHp() / player.getMaxHp()) * 100.0;
                if (hpPerc > targetHpPercent) {
                    return false;
                }
            } else if (isMpHealSkill(skillObj)) {
                double mpPerc = (player.getCurrentMp() / player.getMaxMp()) * 100.0;
                if (mpPerc > targetMpPercent) {
                    return false;
                }
            } else if (isCpHealSkill(skillObj)) {
                double cpPerc = (player.getCurrentCp() / player.getMaxCp()) * 100.0;
                if (cpPerc > targetCpPercent) {
                    return false;
                }
            }
            // else TOTEM or normal buff => always cast (the “autoReuse => isMissingEffect”
            // check
            // is done in AutoFarmTask).
            return true;
        }

        // else => single-target logic
        Creature t = (player.getTarget() instanceof Creature) ? (Creature) player.getTarget() : null;
        if (t == null || t.isDead())
            return false;

        // For attack/damage type skills, we interpret 'targetHpPercent' as "cast if
        // target has <= that HP%".
        double tHpPerc = (t.getCurrentHp() / t.getMaxHp()) * 100.0;
        if (tHpPerc > targetHpPercent) {
            return false;
        }
        return true;
    }
}
