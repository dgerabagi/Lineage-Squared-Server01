package l2ft.gameserver.autofarm;

import l2ft.gameserver.ai.CtrlIntention;
import l2ft.gameserver.model.*;
import l2ft.gameserver.model.instances.MonsterInstance;
import l2ft.gameserver.model.items.ItemInstance;
import l2ft.gameserver.model.pledge.Alliance;
import l2ft.gameserver.model.pledge.Clan;
import l2ft.gameserver.model.EffectList;
import l2ft.gameserver.skills.effects.EffectTemplate;
import l2ft.gameserver.templates.item.WeaponTemplate;
import l2ft.gameserver.utils.Location;

import java.util.List;

/**
 * Revised AutoFarmTask with:
 * 1) Highest priority on sweeping any dead/spoiled mobs within ~350 range
 * 2) Spoil logic if no sweep needed
 * 3) Normal attacking logic last
 */
public class AutoFarmTask implements Runnable {
    private final AutoFarmState _farmState;

    // Required skill IDs
    private static final int SPOIL_ID = 254;
    private static final int SPOIL_FESTIVAL_ID = 302;
    private static final int SWEEPER_ID = 42;
    private static final int FESTIVE_SWEEPER_ID = 444; // newly recognized Festive Sweeper

    // We set a 350 range for sweep detection
    private static final int SWEEP_DETECTION_RANGE = 350;

    public AutoFarmTask(AutoFarmState farmState) {
        _farmState = farmState;
    }

    @Override
    public void run() {
        Player player = (_farmState != null) ? _farmState.getPlayer() : null;
        if (player == null || player.isLogoutStarted() || player.isDead()) {
            System.out.println("[AutoFarmTask] Player is null/dead/logout. Stopping autofarm.");
            if (_farmState != null) {
                _farmState.setActive(false);
                _farmState.renderRange();
                _farmState.cancelTask();
            }
            return;
        }

        // Skip if currently casting a skill
        if (player.isCastingNow()) {
            return;
        }

        // Re-draw the search circle (green if active, red if not)
        _farmState.renderRange();

        // If not active, skip
        if (!_farmState.isActive()) {
            return;
        }

        // 0) Highest Priority: Sweep any dead/spoiled mob (within 350 range)
        if (tryImmediateSweep(player)) {
            return;
        }

        // 1) Self-buffs
        if (trySelfBuff(player)) {
            return;
        }

        // 2) Party-buffs
        if (tryPartySkills(player)) {
            return;
        }

        // 3) Ally-buffs
        if (tryAllySkills(player)) {
            return;
        }

        // 4) Spoil logic
        if (trySpoilLogic(player)) {
            return;
        }

        // 5) Normal searching for a target
        ESearchType searchType = _farmState.getSearchType();
        if (searchType != ESearchType.OFF) {
            Creature target = validateTarget();
            if (target == null) {
                if (searchType == ESearchType.ASSIST) {
                    target = findAssistTarget(player);
                } else {
                    target = findLocalTarget(player, searchType);
                }
                if (target != null) {
                    player.setTarget(target);
                }
            }

            if (target != null && !target.isDead()) {
                // Attack skill usage
                boolean usedSkill = tryAttackSkills(player, target);
                if (!usedSkill) {
                    // If no skill used, we do normal physical attack
                    double dist = player.getDistance(target);
                    double range = computeAttackRange(player);
                    if (dist <= range + 10.0) {
                        player.getAI().setIntention(CtrlIntention.AI_INTENTION_ATTACK, target);
                    } else {
                        player.getAI().Attack(target, false, false);
                    }
                }
                return;
            }
        }

        // 6) Movement (FOLLOW or PATH)
        if (_farmState.getMoveMethod() == AutoFarmState.EMoveMethod.FOLLOW) {
            Player followTgt = _farmState.getFollowTargetPlayer();
            if (followTgt != null && !followTgt.isDead()) {
                double dist = player.getDistance(followTgt);
                if (dist > 200.0) {
                    player.getAI().Attack(followTgt, false, false);
                }
            }
        } else if (_farmState.getMoveMethod() == AutoFarmState.EMoveMethod.PATH) {
            PathData pd = PathData.get(_farmState);
            String selectedName = pd.getSelectedPath();
            if (selectedName != null) {
                PathData.PathInfo pi = pd.getPath(selectedName);
                if (pi != null && !pi.points.isEmpty()) {
                    int idx = _farmState.getPathIndex();
                    if (idx >= pi.points.size()) {
                        idx = 0;
                        _farmState.setPathIndex(idx);
                    }
                    Location loc = pi.points.get(idx);
                    double dist = player.getDistance(loc.x, loc.y, loc.z);
                    if (dist < 64.0) {
                        idx++;
                        _farmState.setPathIndex(idx);
                    } else {
                        player.moveToLocation(loc, 0, false);
                    }
                } else {
                    player.sendMessage("Selected path is empty or invalid; can't do path movement.");
                }
            } else {
                player.sendMessage("No path selected for PATH movement mode. Please select one.");
            }
        }
    }

    // -------------------------------------------------------------------------
    // STEP 0: Highest priority -> Sweep any dead/spoiled mob within 350 range
    // -------------------------------------------------------------------------
    private boolean tryImmediateSweep(Player player) {
        // Must have spoil enabled
        if (!_farmState.isSpoilEnabled()) {
            return false;
        }

        // Player must have sweeper skill
        Skill sweeper = player.getKnownSkill(SWEEPER_ID);
        Skill sweepFest = player.getKnownSkill(FESTIVE_SWEEPER_ID);
        if (sweeper == null) {
            return false;
        }

        MonsterInstance sweepTarget = findNearestSpoiledDead(player, SWEEP_DETECTION_RANGE);
        if (sweepTarget == null) {
            return false;
        }

        int spoiledCount = countSpoiledDead(player, SWEEP_DETECTION_RANGE);
        Skill toUse = sweeper;
        if (sweepFest != null && spoiledCount > 2) {
            toUse = sweepFest;
        }

        // Remember old target
        GameObject oldTarget = player.getTarget();

        double dist = player.getDistance(sweepTarget);
        double castRange = toUse.getCastRange();
        if (castRange < 1) {
            castRange = 40.0;
        }

        if (dist > castRange + 20.0) {
            System.out.println("[AutoFarmTask] Sweeper target found, moving closer...");
            player.moveToLocation(sweepTarget.getLoc(), 10, false);
        } else {
            System.out.println("[AutoFarmTask] Casting sweeper on spoiled corpse " + sweepTarget.getName());
            player.setTarget(sweepTarget);
            player.getAI().Cast(toUse, sweepTarget, false, false);
        }

        // Restore old target after
        if (oldTarget != null && oldTarget != sweepTarget) {
            player.setTarget(oldTarget);
        }

        return true;
    }

    private MonsterInstance findNearestSpoiledDead(Player player, int range) {
        MonsterInstance best = null;
        double bestDist = Double.MAX_VALUE;
        for (Creature c : World.getAroundCharacters(player, range, 256)) {
            if (!c.isMonster()) {
                continue;
            }
            MonsterInstance mon = (MonsterInstance) c;
            if (!mon.isDead()) {
                continue;
            }
            if (!mon.isSpoiled(player)) {
                continue;
            }
            double dist = player.getDistance(mon);
            if (dist < bestDist) {
                bestDist = dist;
                best = mon;
            }
        }
        return best;
    }

    private int countSpoiledDead(Player player, int range) {
        int count = 0;
        for (Creature c : World.getAroundCharacters(player, range, 256)) {
            if (c.isMonster()) {
                MonsterInstance mon = (MonsterInstance) c;
                if (mon.isDead() && mon.isSpoiled(player)) {
                    count++;
                }
            }
        }
        return count;
    }

    // -------------------------------------------------------------------------
    // STEP 4: Spoil logic
    // -------------------------------------------------------------------------
    private boolean trySpoilLogic(Player player) {
        if (!_farmState.isSpoilEnabled()) {
            return false;
        }

        Skill spoilSkill = player.getKnownSkill(SPOIL_ID);
        Skill spoilFest = player.getKnownSkill(SPOIL_FESTIVAL_ID);
        Skill sweeper = player.getKnownSkill(SWEEPER_ID);

        if (spoilSkill == null || spoilFest == null || sweeper == null) {
            System.out.println("[AutoFarmTask] Missing spoil or sweeper skill. Disabling spoil mode.");
            _farmState.setSpoilEnabled(false);
            return false;
        }

        int livingCount = countNearbyLivingMobs(player, 700);
        Skill spoilToUse = (livingCount > 2 ? spoilFest : spoilSkill);

        MonsterInstance spoilTarget = findNearestUnspoiledMonster(player, spoilToUse.getCastRange());
        if (spoilTarget != null) {
            System.out.println("[AutoFarmTask] Attempting to spoil target " + spoilTarget.getName());
            player.setTarget(spoilTarget);
            player.getAI().Cast(spoilToUse, spoilTarget, false, false);
            return true;
        }

        return false;
    }

    private MonsterInstance findNearestUnspoiledMonster(Player player, double castRange) {
        MonsterInstance best = null;
        double bestDist = Double.MAX_VALUE;
        for (Creature c : World.getAroundCharacters(player, (int) castRange, 256)) {
            if (!c.isMonster() || c.isDead()) {
                continue;
            }
            MonsterInstance mon = (MonsterInstance) c;
            if (mon.isSpoiled()) {
                continue;
            }
            if (!mon.isAutoAttackable(player)) {
                continue;
            }

            TargetFilterData tfData = TargetFilterData.get(_farmState);
            if (tfData.isIgnored(mon.getNpcId())) {
                continue;
            }

            double dist = player.getDistance(mon);
            if (dist < bestDist) {
                bestDist = dist;
                best = mon;
            }
        }
        return best;
    }

    private int countNearbyLivingMobs(Player player, int range) {
        int count = 0;
        for (Creature c : World.getAroundCharacters(player, range, 256)) {
            if (c.isMonster() && !c.isDead()) {
                count++;
            }
        }
        return count;
    }

    // -------------------------------------------------------------------------
    // Self-buffs, Party/Ally-buffs, Attack logic
    // -------------------------------------------------------------------------
    private boolean trySelfBuff(Player player) {
        // We check the first 76 slots, matching the internal logic
        for (int i = 0; i < 76; i++) {
            AutoFarmSkillSlot slot = _farmState.getSkillSlot(i);
            if (slot == null || slot.getSkillId() <= 0) {
                continue;
            }
            if (!slot.isSelfBuff()) {
                continue;
            }
            if (slot.isOnCooldown()) {
                continue;
            }

            System.out.println("[AutoFarmTask] Checking self-buff slot " + i
                    + " (skillId=" + slot.getSkillId() + ") for player " + player.getName());
            Skill skillObj = slot.getSkill(player);
            if (skillObj == null) {
                continue;
            }
            if (player.isSkillDisabled(skillObj)) {
                System.out.println("[AutoFarmTask] Skill " + skillObj.getName() + " is currently disabled.");
                continue;
            }

            // Let's see if checkConditions passes
            if (!slot.checkConditions(player)) {
                System.out
                        .println("[AutoFarmTask] -> checkConditions() returned false for skillId=" + slot.getSkillId());
                continue;
            }

            System.out.println("[AutoFarmTask] -> checkConditions() returned true for " + skillObj.getName()
                    + ". Attempting to cast self-buff...");
            boolean isHealSkill = slot.isHpHealSkill(skillObj)
                    || slot.isMpHealSkill(skillObj)
                    || slot.isCpHealSkill(skillObj);
            if (slot.isAutoReuse() && !isHealSkill) {
                if (!isMissingEffect(player, skillObj)) {
                    System.out.println("[AutoFarmTask] -> The buff effect is still present. Skipping re-cast.");
                    continue;
                }
            }

            // All checks pass, cast it
            player.getAI().Cast(skillObj, player, false, false);
            slot.setLastUseNow();
            return true;
        }
        return false;
    }

    private boolean tryPartySkills(Player player) {
        Party party = player.getParty();
        if (party == null) {
            return false;
        }

        for (int i = 0; i < 76; i++) {
            AutoFarmSkillSlot slot = _farmState.getSkillSlot(i);
            if (slot == null || slot.getSkillId() <= 0) {
                continue;
            }
            if (!slot.isPartySkill()) {
                continue;
            }
            if (slot.isOnCooldown()) {
                continue;
            }

            System.out.println("[AutoFarmTask] Checking party-skill slot " + i
                    + " (skillId=" + slot.getSkillId() + ") for player " + player.getName());
            Skill skillObj = slot.getSkill(player);
            if (skillObj == null) {
                continue;
            }
            if (player.isSkillDisabled(skillObj)) {
                System.out.println("[AutoFarmTask] Skill " + skillObj.getName() + " is currently disabled.");
                continue;
            }

            // Let the slot's checkConditions do a quick pass
            if (!slot.checkConditions(player)) {
                System.out
                        .println("[AutoFarmTask] -> checkConditions() returned false for skillId=" + slot.getSkillId());
                continue;
            }

            Creature tgt = findPartyMemberToSupport(player, slot, skillObj);
            if (tgt == null) {
                continue;
            }

            double castRange = skillObj.getCastRange();
            if (castRange < 1) {
                castRange = 40.0;
            }
            double dist = player.getDistance(tgt);
            if (dist <= castRange + 20.0) {
                System.out.println("[AutoFarmTask] Casting party skill " + skillObj.getName()
                        + " on " + tgt.getName());
                player.setTarget(tgt);
                player.getAI().Cast(skillObj, tgt, false, false);
                slot.setLastUseNow();
                return true;
            } else {
                System.out.println("[AutoFarmTask] Moving to party member " + tgt.getName()
                        + " to cast " + skillObj.getName());
                player.getAI().Attack(tgt, false, false);
                return true;
            }
        }
        return false;
    }

    private boolean tryAllySkills(Player player) {
        Alliance alliance = (player.getClan() != null) ? player.getClan().getAlliance() : null;
        if (alliance == null) {
            return false;
        }

        for (int i = 0; i < 76; i++) {
            AutoFarmSkillSlot slot = _farmState.getSkillSlot(i);
            if (slot == null || slot.getSkillId() <= 0) {
                continue;
            }
            if (!slot.isAllySkill()) {
                continue;
            }
            if (slot.isOnCooldown()) {
                continue;
            }

            System.out.println("[AutoFarmTask] Checking ally-skill slot " + i
                    + " (skillId=" + slot.getSkillId() + ") for player " + player.getName());
            Skill skillObj = slot.getSkill(player);
            if (skillObj == null) {
                continue;
            }
            if (player.isSkillDisabled(skillObj)) {
                System.out.println("[AutoFarmTask] Skill " + skillObj.getName() + " is currently disabled.");
                continue;
            }

            if (!slot.checkConditions(player)) {
                System.out
                        .println("[AutoFarmTask] -> checkConditions() returned false for skillId=" + slot.getSkillId());
                continue;
            }

            Creature tgt = findAllyMemberToSupport(player, slot, skillObj, alliance);
            if (tgt == null) {
                continue;
            }

            double castRange = skillObj.getCastRange();
            if (castRange < 1) {
                castRange = 40.0;
            }
            double dist = player.getDistance(tgt);
            if (dist <= castRange + 20.0) {
                System.out.println("[AutoFarmTask] Casting ally skill " + skillObj.getName()
                        + " on " + tgt.getName());
                player.setTarget(tgt);
                player.getAI().Cast(skillObj, tgt, false, false);
                slot.setLastUseNow();
                return true;
            } else {
                System.out.println("[AutoFarmTask] Moving to ally member " + tgt.getName()
                        + " to cast " + skillObj.getName());
                player.getAI().Attack(tgt, false, false);
                return true;
            }
        }
        return false;
    }

    private boolean tryAttackSkills(Player player, Creature target) {
        for (int i = 0; i < 76; i++) {
            AutoFarmSkillSlot slot = _farmState.getSkillSlot(i);
            if (slot == null || slot.getSkillId() <= 0) {
                continue;
            }
            // skip non-offensive
            if (slot.isPartySkill() || slot.isAllySkill() || slot.isSelfBuff()) {
                continue;
            }
            if (slot.isOnCooldown()) {
                continue;
            }

            Skill skillObj = slot.getSkill(player);
            if (skillObj == null) {
                continue;
            }
            if (player.isSkillDisabled(skillObj)) {
                continue;
            }

            // Debug logging
            System.out.println("[AutoFarmTask] Checking attack-skill slot " + i
                    + " (skillId=" + slot.getSkillId() + ") on target " + target.getName());

            // Check autoReuse buff logic
            if (slot.isAutoReuse()) {
                if (!isMissingEffect(target, skillObj)) {
                    System.out.println("[AutoFarmTask] -> The effect is present on target. Skipping re-cast.");
                    continue;
                }
            }

            if (!slot.checkConditions(player)) {
                System.out
                        .println("[AutoFarmTask] -> checkConditions() returned false for skillId=" + slot.getSkillId());
                continue;
            }

            double castRange = skillObj.getCastRange();
            if (castRange < 1) {
                castRange = 40.0;
            }
            double dist = player.getDistance(target);
            if (dist > castRange + 20.0) {
                System.out.println("[AutoFarmTask] Moving closer to cast " + skillObj.getName());
                player.getAI().Attack(target, false, false);
                return true;
            } else {
                System.out.println("[AutoFarmTask] Casting attack skill " + skillObj.getName()
                        + " on target " + target.getName());
                player.setTarget(target);
                player.getAI().Cast(skillObj, target, false, false);
                slot.setLastUseNow();
                return true;
            }
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Helpers to find party/ally members to support
    // -------------------------------------------------------------------------
    private Creature findPartyMemberToSupport(Player player, AutoFarmSkillSlot slot, Skill skillObj) {
        Party party = player.getParty();
        if (party == null) {
            return null;
        }

        double bestDist = Double.MAX_VALUE;
        Creature best = null;

        boolean isHpHeal = slot.isHpHealSkill(skillObj);
        boolean isMpHeal = slot.isMpHealSkill(skillObj);
        boolean isCpHeal = slot.isCpHealSkill(skillObj);

        for (Player pm : party.getPartyMembers()) {
            if (pm.isDead()) {
                continue;
            }
            // if slot is not a selfBuff, skip the player itself if we want to buff others
            if (!slot.isSelfBuff() && pm == player) {
                continue;
            }

            double dist = player.getDistance(pm);
            if (dist > 3000.0) {
                continue;
            }

            if (!isHpHeal && !isMpHeal && !isCpHeal) {
                // normal buff
                if (slot.isAutoReuse()) {
                    if (!isMissingEffect(pm, skillObj)) {
                        continue;
                    }
                }
                if (dist < bestDist) {
                    bestDist = dist;
                    best = pm;
                }
            } else {
                // Heal logic
                if (isHpHeal) {
                    double hpPerc = pm.getCurrentHp() / pm.getMaxHp() * 100.0;
                    if (hpPerc > slot.getTargetHpPercent()) {
                        continue;
                    }
                } else if (isMpHeal) {
                    double mpPerc = pm.getCurrentMp() / pm.getMaxMp() * 100.0;
                    if (mpPerc > slot.getTargetMpPercent()) {
                        continue;
                    }
                } else if (isCpHeal) {
                    double cpPerc = pm.getCurrentCp() / pm.getMaxCp() * 100.0;
                    if (cpPerc > slot.getTargetCpPercent()) {
                        continue;
                    }
                }
                if (dist < bestDist) {
                    bestDist = dist;
                    best = pm;
                }
            }
        }
        return best;
    }

    private Creature findAllyMemberToSupport(Player player, AutoFarmSkillSlot slot, Skill skillObj, Alliance alliance) {
        double bestDist = Double.MAX_VALUE;
        Creature best = null;

        boolean isHpHeal = slot.isHpHealSkill(skillObj);
        boolean isMpHeal = slot.isMpHealSkill(skillObj);
        boolean isCpHeal = slot.isCpHealSkill(skillObj);

        for (Clan clan : alliance.getMembers()) {
            for (Player ally : clan.getOnlineMembers(0)) {
                if (ally == null || ally.isDead() || ally == player) {
                    continue;
                }
                double dist = player.getDistance(ally);
                if (dist > 3000.0) {
                    continue;
                }

                if (!isHpHeal && !isMpHeal && !isCpHeal) {
                    if (slot.isAutoReuse()) {
                        if (!isMissingEffect(ally, skillObj)) {
                            continue;
                        }
                    }
                    if (dist < bestDist) {
                        bestDist = dist;
                        best = ally;
                    }
                } else {
                    // heal logic
                    if (isHpHeal) {
                        double hpPerc = ally.getCurrentHp() / ally.getMaxHp() * 100.0;
                        if (hpPerc > slot.getTargetHpPercent()) {
                            continue;
                        }
                    } else if (isMpHeal) {
                        double mpPerc = ally.getCurrentMp() / ally.getMaxMp() * 100.0;
                        if (mpPerc > slot.getTargetMpPercent()) {
                            continue;
                        }
                    } else if (isCpHeal) {
                        double cpPerc = ally.getCurrentCp() / ally.getMaxCp() * 100.0;
                        if (cpPerc > slot.getTargetCpPercent()) {
                            continue;
                        }
                    }
                    if (dist < bestDist) {
                        bestDist = dist;
                        best = ally;
                    }
                }
            }
        }
        return best;
    }

    // -------------------------------------------------------------------------
    // Validate normal attack target
    // -------------------------------------------------------------------------
    private Creature validateTarget() {
        Player player = _farmState.getPlayer();
        if (player == null) {
            return null;
        }

        GameObject obj = player.getTarget();
        if (obj == null || !obj.isCreature()) {
            return null;
        }
        Creature c = (Creature) obj;
        if (c.isDead() || !obj.isMonster()) {
            return null;
        }

        MonsterInstance mon = (MonsterInstance) c;
        TargetFilterData tfData = TargetFilterData.get(_farmState);
        if (tfData.isIgnored(mon.getNpcId())) {
            return null;
        }

        if (!mon.isAutoAttackable(player)) {
            return null;
        }

        int zLimit = _farmState.getZRangeLimit();
        if (Math.abs(player.getZ() - c.getZ()) > zLimit) {
            return null;
        }

        return c;
    }

    private Creature findAssistTarget(Player player) {
        Party p = player.getParty();
        if (p == null) {
            return null;
        }
        Player assistPm = _farmState.getFollowTargetPlayer();
        if (assistPm == null || assistPm.isDead()) {
            return null;
        }

        GameObject leadTgt = assistPm.getTarget();
        if (leadTgt != null && leadTgt.isCreature()) {
            Creature c = (Creature) leadTgt;
            if (c.isMonster() && !c.isDead()) {
                MonsterInstance mon = (MonsterInstance) c;
                TargetFilterData tfData = TargetFilterData.get(_farmState);
                if (tfData.isIgnored(mon.getNpcId())) {
                    return null;
                }
                int zLimit = _farmState.getZRangeLimit();
                if (Math.abs(player.getZ() - c.getZ()) <= zLimit) {
                    if (mon.isAutoAttackable(player)) {
                        return c;
                    }
                }
            }
        }
        return null;
    }

    private Creature findLocalTarget(Player player, ESearchType searchType) {
        int range = searchType.getRange();
        int zLimit = _farmState.getZRangeLimit();
        Creature best = null;
        double bestDist = Double.MAX_VALUE;

        TargetFilterData tfData = TargetFilterData.get(_farmState);

        for (Creature c : World.getAroundCharacters(player)) {
            if (!c.isMonster() || c.isDead()) {
                continue;
            }
            MonsterInstance mon = (MonsterInstance) c;
            if (tfData.isIgnored(mon.getNpcId())) {
                continue;
            }
            if (Math.abs(player.getZ() - c.getZ()) > zLimit) {
                continue;
            }
            double dist = player.getDistance(c);
            if (dist <= range && dist < bestDist) {
                if (mon.isAutoAttackable(player)) {
                    best = c;
                    bestDist = dist;
                }
            }
        }
        return best;
    }

    // -------------------------------------------------------------------------
    // Utility checks
    // -------------------------------------------------------------------------
    private boolean isMissingEffect(Creature creature, Skill skillObj) {
        if (skillObj == null || creature == null) {
            return true;
        }
        if (!skillObj.hasEffects()) {
            return true;
        }
        if (!creature.isPlayer()) {
            return true;
        }

        Player pl = creature.getPlayer();
        if (pl.getEffectList().getEffectsBySkillId(skillObj.getId()) != null) {
            return false;
        }

        EffectTemplate[] templates = skillObj.getEffectTemplates();
        if (templates == null || templates.length == 0) {
            return true;
        }

        EffectTemplate myTemplate = templates[0];
        if (myTemplate == null) {
            return true;
        }

        int mySkillLevel = skillObj.getLevel();
        int myStackOrder = myTemplate._stackOrder;
        List<Effect> existing = pl.getEffectList().getAllEffects();
        if (existing.isEmpty()) {
            return true;
        }

        for (Effect e : existing) {
            if (e == null || e.getSkill() == null) {
                continue;
            }
            Skill existingSkill = e.getSkill();
            if (!EffectList.checkStackType(myTemplate, e.getTemplate())) {
                continue;
            }
            if (existingSkill.getLevel() >= mySkillLevel) {
                return false;
            }
            if (e.getStackOrder() >= myStackOrder) {
                return false;
            }
        }
        return true;
    }

    private double computeAttackRange(Player player) {
        ItemInstance weapon = player.getActiveWeaponInstance();
        if (weapon == null) {
            return 100.0;
        }
        if (weapon.getTemplate() instanceof WeaponTemplate) {
            WeaponTemplate wItem = (WeaponTemplate) weapon.getTemplate();
            String wTypeName = wItem.getItemType().toString().toUpperCase();
            if (wTypeName.contains("BOW") || wTypeName.contains("CROSSBOW")) {
                return 800.0;
            }
        }
        return 100.0;
    }
}
