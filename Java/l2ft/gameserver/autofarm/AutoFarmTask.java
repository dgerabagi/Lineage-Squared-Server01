package l2ft.gameserver.autofarm;

import l2ft.gameserver.ai.CtrlIntention;
import l2ft.gameserver.model.*;
import l2ft.gameserver.model.instances.MonsterInstance;
import l2ft.gameserver.model.items.ItemInstance;
import l2ft.gameserver.model.pledge.Alliance;
import l2ft.gameserver.model.pledge.Clan;
import l2ft.gameserver.skills.effects.EffectTemplate;
import l2ft.gameserver.templates.item.WeaponTemplate;
import l2ft.gameserver.utils.Location;

import java.util.List;

/**
 * Revised AutoFarmTask with:
 * 1) Highest priority on sweeping any dead/spoiled mobs within ~350 range,
 * then resume old target if any.
 * 2) Spoil logic if no sweep needed.
 * 3) Normal attacking logic last.
 */
public class AutoFarmTask implements Runnable {
    private final AutoFarmState _farmState;

    // Required skill IDs
    private static final int SPOIL_ID = 254;
    private static final int SPOIL_FESTIVAL_ID = 302;
    private static final int SWEEPER_ID = 42;
    private static final int FESTIVE_SWEEPER_ID = 444; // newly recognized Festive Sweeper

    // We set a 350 range for sweep detection, as requested
    private static final int SWEEP_DETECTION_RANGE = 350;

    public AutoFarmTask(AutoFarmState farmState) {
        _farmState = farmState;
    }

    @Override
    public void run() {
        Player player = (_farmState != null) ? _farmState.getPlayer() : null;
        if (player == null || player.isLogoutStarted() || player.isDead()) {
            if (_farmState != null) {
                _farmState.setActive(false);
                _farmState.renderRange();
                _farmState.cancelTask();
            }
            return;
        }

        // Skip if casting
        if (player.isCastingNow())
            return;

        // Re-draw search circle
        _farmState.renderRange();

        // If not active, skip
        if (!_farmState.isActive())
            return;

        // 0) Highest Priority: Sweep any dead/spoiled mob (within 350 range)
        if (tryImmediateSweep(player))
            return;

        // 1) Self-buffs
        if (trySelfBuff(player))
            return;

        // 2) Party-buffs
        if (tryPartySkills(player))
            return;

        // 3) Ally-buffs
        if (tryAllySkills(player))
            return;

        // 4) Spoil logic
        if (trySpoilLogic(player))
            return;

        // 5) Normal searching for a target
        ESearchType searchType = _farmState.getSearchType();
        if (searchType != ESearchType.OFF) {
            Creature target = validateTarget();
            if (target == null) {
                if (searchType == ESearchType.ASSIST)
                    target = findAssistTarget(player);
                else
                    target = findLocalTarget(player, searchType);

                if (target != null)
                    player.setTarget(target);
            }

            if (target != null && !target.isDead()) {
                // Attack skill usage
                boolean usedSkill = tryAttackSkills(player, target);
                if (!usedSkill) {
                    // Physical auto-attack
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
                if (dist > 200.0)
                    player.getAI().Attack(followTgt, false, false);
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
        // Must have spoil enabled to consider sweeping
        if (!_farmState.isSpoilEnabled())
            return false;

        // Player must have sweeper skill
        Skill sweeper = player.getKnownSkill(SWEEPER_ID);
        Skill sweepFest = player.getKnownSkill(FESTIVE_SWEEPER_ID);
        if (sweeper == null)
            return false; // can't do anything

        // Attempt to find a single nearest dead/spoiled mob within 350 range
        MonsterInstance sweepTarget = findNearestSpoiledDead(player, SWEEP_DETECTION_RANGE);
        if (sweepTarget == null)
            return false; // none to sweep

        // If we find one, pick single or festival sweeper
        int spoiledCount = countSpoiledDead(player, SWEEP_DETECTION_RANGE);
        Skill toUse = sweeper;
        if (sweepFest != null && spoiledCount > 2)
            toUse = sweepFest;

        // Save the old target so we can restore it later
        GameObject oldTarget = player.getTarget();

        // Start sweeper
        double dist = player.getDistance(sweepTarget);
        double castRange = toUse.getCastRange();
        if (castRange < 1)
            castRange = 40.0;

        // If out of cast range, let's move to the corpse
        if (dist > castRange + 20.0) {
            // We'll do a simple approach
            player.moveToLocation(sweepTarget.getLoc(), 10, false);
            // We won't forcibly "return" here, we let next cycle handle it after we move.
            // Or we can forcibly cast now if in range next tick
        } else {
            // We can cast immediately
            player.setTarget(sweepTarget);
            player.getAI().Cast(toUse, sweepTarget, false, false);
        }

        // After we attempt sweeping, restore old target
        if (oldTarget != null && oldTarget != sweepTarget)
            player.setTarget(oldTarget);

        return true; // We did something
    }

    private MonsterInstance findNearestSpoiledDead(Player player, int range) {
        MonsterInstance best = null;
        double bestDist = Double.MAX_VALUE;
        for (Creature c : World.getAroundCharacters(player, range, 256)) {
            if (!c.isMonster())
                continue;
            MonsterInstance mon = (MonsterInstance) c;
            if (!mon.isDead())
                continue;
            if (!mon.isSpoiled(player))
                continue;

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
                if (mon.isDead() && mon.isSpoiled(player))
                    count++;
            }
        }
        return count;
    }

    // -------------------------------------------------------------------------
    // STEP 4: Spoil logic
    // -------------------------------------------------------------------------
    private boolean trySpoilLogic(Player player) {
        if (!_farmState.isSpoilEnabled())
            return false;

        // Check if player actually has these spoil skills
        Skill spoilSkill = player.getKnownSkill(SPOIL_ID);
        Skill spoilFest = player.getKnownSkill(SPOIL_FESTIVAL_ID);
        Skill sweeper = player.getKnownSkill(SWEEPER_ID);

        // If missing fundamental skills => disable
        if (spoilSkill == null || spoilFest == null || sweeper == null) {
            _farmState.setSpoilEnabled(false);
            player.sendMessage("Missing spoil or sweeper skill. Disabling spoil mode.");
            return false;
        }

        // Decide single or festival spoil
        int livingCount = countNearbyLivingMobs(player, 700);
        Skill spoilToUse = (livingCount > 2 ? spoilFest : spoilSkill);

        MonsterInstance spoilTarget = findNearestUnspoiledMonster(player, spoilToUse.getCastRange());
        if (spoilTarget != null) {
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
            if (!c.isMonster() || c.isDead())
                continue;
            MonsterInstance mon = (MonsterInstance) c;
            if (mon.isSpoiled())
                continue;
            if (!mon.isAutoAttackable(player))
                continue;

            TargetFilterData tfData = TargetFilterData.get(_farmState);
            if (tfData.isIgnored(mon.getNpcId()))
                continue;

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
            if (c.isMonster() && !c.isDead())
                count++;
        }
        return count;
    }

    // -------------------------------------------------------------------------
    // Self-buffs, Party/Ally-buffs, Attack logic (unchanged)
    // -------------------------------------------------------------------------
    private boolean trySelfBuff(Player player) {
        // 0..75 skill slots
        for (int i = 0; i < 76; i++) {
            AutoFarmSkillSlot slot = _farmState.getSkillSlot(i);
            if (slot == null || slot.getSkillId() <= 0)
                continue;
            if (!slot.isSelfBuff())
                continue;
            if (slot.isOnCooldown())
                continue;

            Skill skillObj = slot.getSkill(player);
            if (skillObj == null)
                continue;
            if (player.isSkillDisabled(skillObj))
                continue;
            if (!slot.checkConditions(player))
                continue;

            boolean isHealSkill = isAnyHealSkill(skillObj);
            if (slot.isAutoReuse() && !isHealSkill) {
                if (!isMissingEffect(player, skillObj))
                    continue;
            }

            player.getAI().Cast(skillObj, player, false, false);
            slot.setLastUseNow();
            return true;
        }
        return false;
    }

    private boolean tryPartySkills(Player player) {
        Party party = player.getParty();
        if (party == null)
            return false;

        for (int i = 0; i < 76; i++) {
            AutoFarmSkillSlot slot = _farmState.getSkillSlot(i);
            if (slot == null || slot.getSkillId() <= 0)
                continue;
            if (!slot.isPartySkill())
                continue;
            if (slot.isOnCooldown())
                continue;

            Skill skillObj = slot.getSkill(player);
            if (skillObj == null)
                continue;
            if (player.isSkillDisabled(skillObj))
                continue;

            Creature tgt = findPartyMemberToSupport(player, slot, skillObj);
            if (tgt == null)
                continue;

            double castRange = skillObj.getCastRange();
            if (castRange < 1)
                castRange = 40.0;

            double dist = player.getDistance(tgt);
            if (dist <= castRange + 20.0) {
                player.setTarget(tgt);
                player.getAI().Cast(skillObj, tgt, false, false);
                slot.setLastUseNow();
                return true;
            } else {
                player.getAI().Attack(tgt, false, false);
                return true;
            }
        }
        return false;
    }

    private boolean tryAllySkills(Player player) {
        Alliance alliance = (player.getClan() != null) ? player.getClan().getAlliance() : null;
        if (alliance == null)
            return false;

        for (int i = 0; i < 76; i++) {
            AutoFarmSkillSlot slot = _farmState.getSkillSlot(i);
            if (slot == null || slot.getSkillId() <= 0)
                continue;
            if (!slot.isAllySkill())
                continue;
            if (slot.isOnCooldown())
                continue;

            Skill skillObj = slot.getSkill(player);
            if (skillObj == null)
                continue;
            if (player.isSkillDisabled(skillObj))
                continue;

            Creature tgt = findAllyMemberToSupport(player, slot, skillObj, alliance);
            if (tgt == null)
                continue;

            double castRange = skillObj.getCastRange();
            if (castRange < 1)
                castRange = 40.0;

            double dist = player.getDistance(tgt);
            if (dist <= castRange + 20.0) {
                player.setTarget(tgt);
                player.getAI().Cast(skillObj, tgt, false, false);
                slot.setLastUseNow();
                return true;
            } else {
                player.getAI().Attack(tgt, false, false);
                return true;
            }
        }
        return false;
    }

    private boolean tryAttackSkills(Player player, Creature target) {
        for (int i = 0; i < 76; i++) {
            AutoFarmSkillSlot slot = _farmState.getSkillSlot(i);
            if (slot == null || slot.getSkillId() <= 0)
                continue;
            if (slot.isPartySkill() || slot.isAllySkill() || slot.isSelfBuff())
                continue;
            if (slot.isOnCooldown())
                continue;

            Skill skillObj = slot.getSkill(player);
            if (skillObj == null)
                continue;
            if (player.isSkillDisabled(skillObj))
                continue;

            // Check autoReuse buff logic
            if (slot.isAutoReuse()) {
                if (!isMissingEffect(target, skillObj))
                    continue;
            }

            if (!slot.checkConditions(player))
                continue;

            double castRange = skillObj.getCastRange();
            if (castRange < 1)
                castRange = 40.0;

            double dist = player.getDistance(target);
            if (dist > castRange + 20.0) {
                player.getAI().Attack(target, false, false);
                return true;
            } else {
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
        if (party == null)
            return null;

        double bestDist = Double.MAX_VALUE;
        Creature best = null;

        boolean isHpHeal = slot.isHpHealSkill(skillObj);
        boolean isMpHeal = slot.isMpHealSkill(skillObj);
        boolean isCpHeal = slot.isCpHealSkill(skillObj);

        for (Player pm : party.getPartyMembers()) {
            if (pm.isDead())
                continue;
            if (!slot.isSelfBuff() && pm == player)
                continue;

            double dist = player.getDistance(pm);
            if (dist > 3000.0)
                continue;

            // normal buff or HP/MP/CP checks
            if (!isHpHeal && !isMpHeal && !isCpHeal) {
                if (slot.isAutoReuse()) {
                    if (!isMissingEffect(pm, skillObj))
                        continue;
                }
                if (dist < bestDist) {
                    bestDist = dist;
                    best = pm;
                }
            } else {
                // heal logic
                if (isHpHeal) {
                    double hpPerc = pm.getCurrentHp() / pm.getMaxHp() * 100.0;
                    if (hpPerc > slot.getTargetHpPercent())
                        continue;
                } else if (isMpHeal) {
                    double mpPerc = pm.getCurrentMp() / pm.getMaxMp() * 100.0;
                    if (mpPerc > slot.getTargetMpPercent())
                        continue;
                } else if (isCpHeal) {
                    double cpPerc = pm.getCurrentCp() / pm.getMaxCp() * 100.0;
                    if (cpPerc > slot.getTargetCpPercent())
                        continue;
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
                if (ally == null || ally.isDead() || ally == player)
                    continue;

                double dist = player.getDistance(ally);
                if (dist > 3000.0)
                    continue;

                if (!isHpHeal && !isMpHeal && !isCpHeal) {
                    if (slot.isAutoReuse()) {
                        if (!isMissingEffect(ally, skillObj))
                            continue;
                    }
                    if (dist < bestDist) {
                        bestDist = dist;
                        best = ally;
                    }
                } else {
                    // heal logic
                    if (isHpHeal) {
                        double hpPerc = ally.getCurrentHp() / ally.getMaxHp() * 100.0;
                        if (hpPerc > slot.getTargetHpPercent())
                            continue;
                    } else if (isMpHeal) {
                        double mpPerc = ally.getCurrentMp() / ally.getMaxMp() * 100.0;
                        if (mpPerc > slot.getTargetMpPercent())
                            continue;
                    } else if (isCpHeal) {
                        double cpPerc = ally.getCurrentCp() / ally.getMaxCp() * 100.0;
                        if (cpPerc > slot.getTargetCpPercent())
                            continue;
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
        if (player == null)
            return null;

        GameObject obj = player.getTarget();
        if (obj == null || !obj.isCreature())
            return null;

        Creature c = (Creature) obj;
        if (c.isDead() || !obj.isMonster())
            return null;

        MonsterInstance mon = (MonsterInstance) c;

        // check ignore filter
        TargetFilterData tfData = TargetFilterData.get(_farmState);
        if (tfData.isIgnored(mon.getNpcId()))
            return null;

        // must be auto-attackable
        if (!mon.isAutoAttackable(player))
            return null;

        int zLimit = _farmState.getZRangeLimit();
        if (Math.abs(player.getZ() - c.getZ()) > zLimit)
            return null;

        return c;
    }

    private Creature findAssistTarget(Player player) {
        Party p = player.getParty();
        if (p == null)
            return null;

        Player assistPm = _farmState.getFollowTargetPlayer();
        if (assistPm == null || assistPm.isDead())
            return null;

        GameObject leadTgt = assistPm.getTarget();
        if (leadTgt != null && leadTgt.isCreature()) {
            Creature c = (Creature) leadTgt;
            if (c.isMonster() && !c.isDead()) {
                MonsterInstance mon = (MonsterInstance) c;

                TargetFilterData tfData = TargetFilterData.get(_farmState);
                if (tfData.isIgnored(mon.getNpcId()))
                    return null;

                int zLimit = _farmState.getZRangeLimit();
                if (Math.abs(player.getZ() - c.getZ()) <= zLimit) {
                    if (mon.isAutoAttackable(player))
                        return c;
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
            if (!c.isMonster() || c.isDead())
                continue;
            MonsterInstance mon = (MonsterInstance) c;

            if (tfData.isIgnored(mon.getNpcId()))
                continue;
            if (Math.abs(player.getZ() - c.getZ()) > zLimit)
                continue;

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
    private boolean isAnyHealSkill(Skill s) {
        if (s == null)
            return false;
        int id = s.getId();
        String className = s.getClass().getSimpleName().toLowerCase();
        if (className.contains("heal"))
            return true;
        if (id == 1013) // "Recharge"
            return true;
        return false;
    }

    private boolean isMissingEffect(Creature creature, Skill skillObj) {
        if (skillObj == null || creature == null)
            return true;
        if (!skillObj.hasEffects())
            return true;
        if (!creature.isPlayer())
            return true;

        Player pl = creature.getPlayer();
        if (pl.getEffectList().getEffectsBySkillId(skillObj.getId()) != null)
            return false;

        EffectTemplate[] templates = skillObj.getEffectTemplates();
        if (templates == null || templates.length == 0)
            return true;

        EffectTemplate myTemplate = templates[0];
        if (myTemplate == null)
            return true;

        int mySkillLevel = skillObj.getLevel();
        int myStackOrder = myTemplate._stackOrder;
        List<Effect> existing = pl.getEffectList().getAllEffects();
        if (existing.isEmpty())
            return true;

        for (Effect e : existing) {
            if (e == null || e.getSkill() == null)
                continue;
            Skill existingSkill = e.getSkill();
            if (!EffectList.checkStackType(myTemplate, e.getTemplate()))
                continue;
            if (existingSkill.getLevel() >= mySkillLevel)
                return false;
            if (e.getStackOrder() >= myStackOrder)
                return false;
        }
        return true;
    }

    private double computeAttackRange(Player player) {
        ItemInstance weapon = player.getActiveWeaponInstance();
        if (weapon == null)
            return 100.0;

        if (weapon.getTemplate() instanceof WeaponTemplate) {
            WeaponTemplate wItem = (WeaponTemplate) weapon.getTemplate();
            String wTypeName = wItem.getItemType().toString().toUpperCase();
            if (wTypeName.contains("BOW") || wTypeName.contains("CROSSBOW"))
                return 800.0;
        }
        return 100.0;
    }
}
