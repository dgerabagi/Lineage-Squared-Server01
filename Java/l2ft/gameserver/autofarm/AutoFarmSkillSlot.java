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
        long now = System.currentTimeMillis();
        long nextReady = lastUseTimestamp + reuseDelayMs;
        return (now < nextReady);
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
     * - If partySkill or allySkill => let AutoFarmTask handle (just return true).
     * - If selfBuff => check HP/MP/CP threshold if it's a recognized healing skill,
     * otherwise if targetHpPercent < 100.0, treat it as requiring HP < threshold
     * (for e.g. Guts).
     * - Otherwise => single-target or offensive skill => requires target’s HP%
     * check.
     *
     * If the checks pass, return true; otherwise false.
     */
    public boolean checkConditions(Player player) {
        System.out.println("[AutoFarmSkillSlot] checkConditions() called for skillId=" + skillId);

        if (player == null || player.isDead()) {
            System.out.println("[AutoFarmSkillSlot] -> false: player is null or dead.");
            return false;
        }

        Skill skillObj = getSkill(player);
        if (skillObj == null) {
            System.out.println("[AutoFarmSkillSlot] -> false: skillObj is null (skillId=" + skillId + ").");
            return false;
        }

        // If party or ally => let the Task do that logic.
        if (partySkill || allySkill) {
            System.out.println("[AutoFarmSkillSlot] -> true: partySkill/allySkill. Task will handle target.");
            return true;
        }

        // Handle selfBuff logic
        if (selfBuff) {
            System.out.println(
                    "[AutoFarmSkillSlot] skillId=" + skillObj.getId() + " is selfBuff. Checking thresholds...");
            // If it's any of the Heal skill types, do threshold checks
            if (isHpHealSkill(skillObj)) {
                double hpPerc = (player.getCurrentHp() / player.getMaxHp()) * 100.0;
                System.out.println("[AutoFarmSkillSlot] isHpHealSkill => player's HP%=" + hpPerc
                        + ", threshold=" + targetHpPercent);
                if (hpPerc > targetHpPercent) {
                    System.out.println("[AutoFarmSkillSlot] -> false: HP is above threshold for heal skill.");
                    return false;
                }
            } else if (isMpHealSkill(skillObj)) {
                double mpPerc = (player.getCurrentMp() / player.getMaxMp()) * 100.0;
                System.out.println("[AutoFarmSkillSlot] isMpHealSkill => player's MP%=" + mpPerc
                        + ", threshold=" + targetMpPercent);
                if (mpPerc > targetMpPercent) {
                    System.out.println("[AutoFarmSkillSlot] -> false: MP is above threshold for MP-heal skill.");
                    return false;
                }
            } else if (isCpHealSkill(skillObj)) {
                double cpPerc = (player.getCurrentCp() / player.getMaxCp()) * 100.0;
                System.out.println("[AutoFarmSkillSlot] isCpHealSkill => player's CP%=" + cpPerc
                        + ", threshold=" + targetCpPercent);
                if (cpPerc > targetCpPercent) {
                    System.out.println("[AutoFarmSkillSlot] -> false: CP is above threshold for CP-heal skill.");
                    return false;
                }
            } else {
                // Normal buff (like Guts, etc.)
                System.out.println("[AutoFarmSkillSlot] Not recognized as heal/totem. Checking if targetHp% < 100...");
                // If the user explicitly set targetHpPercent < 100, let's require that player's
                // HP be below threshold
                if (targetHpPercent < 100.0) {
                    double hpPerc = (player.getCurrentHp() / player.getMaxHp()) * 100.0;
                    System.out.println("[AutoFarmSkillSlot] 'normal' self buff but HP% trigger = " + targetHpPercent
                            + ", current HP%=" + hpPerc);
                    if (hpPerc > targetHpPercent) {
                        System.out.println(
                                "[AutoFarmSkillSlot] -> false: HP is above threshold for normal selfBuff skill.");
                        return false;
                    }
                } else {
                    // If user left HP% at 100, we won't do an HP-based check
                    System.out.println(
                            "[AutoFarmSkillSlot] targetHpPercent=100 => no HP check for normal buff. Allowed to cast.");
                }
            }

            System.out.println("[AutoFarmSkillSlot] -> true: selfBuff skill meets threshold conditions.");
            return true;
        }

        // else => single-target or offensive skill => requires target’s HP% check
        Creature t = (player.getTarget() instanceof Creature) ? (Creature) player.getTarget() : null;
        if (t == null || t.isDead()) {
            System.out.println("[AutoFarmSkillSlot] -> false: no valid creature target or it's dead.");
            return false;
        }

        double tHpPerc = (t.getCurrentHp() / t.getMaxHp()) * 100.0;
        System.out.println("[AutoFarmSkillSlot] Offensive skill => target HP%=" + tHpPerc
                + ", threshold=" + targetHpPercent);
        if (tHpPerc > targetHpPercent) {
            System.out.println("[AutoFarmSkillSlot] -> false: target's HP% above threshold for offensive skill.");
            return false;
        }

        System.out.println("[AutoFarmSkillSlot] -> true: conditions met for offensive skill usage.");
        return true;
    }
}
