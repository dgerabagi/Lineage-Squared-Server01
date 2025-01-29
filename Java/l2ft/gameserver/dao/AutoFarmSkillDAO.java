package l2ft.gameserver.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import l2ft.commons.dbutils.DbUtils;
import l2ft.gameserver.database.DatabaseFactory;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.autofarm.AutoFarmSkillSlot;
import l2ft.gameserver.autofarm.AutoFarmState;

/**
 * Persists and loads the "AutoFarmSkillSlot" data for each player's
 * stackclass combo, but only modifies individual slots as needed
 * (instead of dumping all 72 each time).
 */
public final class AutoFarmSkillDAO {
    private static final Logger _log = LoggerFactory.getLogger(AutoFarmSkillDAO.class);

    // Our table:
    // CREATE TABLE IF NOT EXISTS `character_autofarm_skills` (
    // `object_id` INT NOT NULL,
    // `slot_index` INT NOT NULL,
    // `class_index` INT NOT NULL,
    // `second_class_index` INT NOT NULL,
    // `skill_id` INT NOT NULL,
    // `hp_percent` DOUBLE NOT NULL,
    // `mp_percent` DOUBLE NOT NULL,
    // `cp_percent` DOUBLE NOT NULL,
    // `reuse_delay_ms` BIGINT NOT NULL,
    // `partySkill` TINYINT NOT NULL,
    // `allySkill` TINYINT NOT NULL,
    // `selfBuff` TINYINT NOT NULL,
    // `autoReuse` TINYINT NOT NULL,
    // PRIMARY KEY (`object_id`,`slot_index`,`class_index`,`second_class_index`)
    // );
    //
    // We always sort the two class IDs so that if they are (77, 98),
    // switching them around won't break the logic.

    private static final AutoFarmSkillDAO _instance = new AutoFarmSkillDAO();

    public static AutoFarmSkillDAO getInstance() {
        return _instance;
    }

    private AutoFarmSkillDAO() {
    }

    // -------------------------------------------------------------------------
    // LOAD: read from DB and fill up to 72 "skill slots"
    // -------------------------------------------------------------------------
    private static final String SELECT_SQL = "SELECT slot_index, skill_id, hp_percent, mp_percent, cp_percent, reuse_delay_ms, "
            +
            "       partySkill, allySkill, selfBuff, autoReuse " +
            "  FROM character_autofarm_skills " +
            " WHERE object_id=? AND class_index=? AND second_class_index=?";

    public void loadSkillsForStackClass(Player player, AutoFarmState farmState) {
        if (player == null || farmState == null) {
            return;
        }
        // NEW - clear them to avoid stale data from previous combo
        farmState.resetAllSkillSlots();

        int objectId = player.getObjectId();
        int[] classes = getSortedClassIds(player);

        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            con = DatabaseFactory.getInstance().getConnection();
            ps = con.prepareStatement(SELECT_SQL);
            ps.setInt(1, objectId);
            ps.setInt(2, classes[0]);
            ps.setInt(3, classes[1]);
            rs = ps.executeQuery();

            while (rs.next()) {
                int slotIndex = rs.getInt("slot_index");
                if (slotIndex < 0 || slotIndex >= 72) {
                    continue;
                }

                AutoFarmSkillSlot slot = farmState.getSkillSlot(slotIndex);
                if (slot == null) {
                    continue;
                }

                int skillId = rs.getInt("skill_id");
                double hp = rs.getDouble("hp_percent");
                double mp = rs.getDouble("mp_percent");
                double cp = rs.getDouble("cp_percent");
                long reuseMs = rs.getLong("reuse_delay_ms");

                boolean partyS = (rs.getInt("partySkill") != 0);
                boolean allyS = (rs.getInt("allySkill") != 0);
                boolean selfB = (rs.getInt("selfBuff") != 0);
                boolean autoR = (rs.getInt("autoReuse") != 0);

                slot.setSkillId(skillId);
                slot.setTargetHpPercent(hp);
                slot.setTargetMpPercent(mp);
                slot.setTargetCpPercent(cp);
                slot.setReuseDelayMs(reuseMs);
                slot.setPartySkill(partyS);
                slot.setAllySkill(allyS);
                slot.setSelfBuff(selfB);
                slot.setAutoReuse(autoR);
            }

            _log.info("AutoFarmSkillDAO: loaded skill config for player " + player.getName() +
                    " / classes=" + classes[0] + "," + classes[1]);
        } catch (Exception e) {
            _log.error("AutoFarmSkillDAO: loadSkillsForStackClass error for player " +
                    player.getName(), e);
        } finally {
            DbUtils.closeQuietly(con, ps, rs);
        }
    }

    // -------------------------------------------------------------------------
    // SINGLE SLOT SAVE: store just one slot row
    // -------------------------------------------------------------------------
    //
    // We'll do a "REPLACE INTO" if skillId > 0,
    // or a "DELETE" if skillId == 0 (i.e. cleared).
    //
    private static final String REPLACE_SINGLE_SQL = "REPLACE INTO character_autofarm_skills " +
            "(object_id, slot_index, class_index, second_class_index, " +
            " skill_id, hp_percent, mp_percent, cp_percent, reuse_delay_ms, " +
            " partySkill, allySkill, selfBuff, autoReuse) " +
            "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)";

    private static final String DELETE_SINGLE_SQL = "DELETE FROM character_autofarm_skills " +
            " WHERE object_id=? AND slot_index=? AND class_index=? AND second_class_index=?";

    /**
     * Saves exactly one slot's data for (player, sorted classes, slotIndex).
     * If skillId == 0, we DELETE. Otherwise, we REPLACE / upsert.
     */
    public void saveSingleSlotForStackClass(Player player, AutoFarmState farmState, int slotIndex) {
        if (player == null || farmState == null || slotIndex < 0 || slotIndex >= 72) {
            return;
        }

        int objectId = player.getObjectId();
        int[] classes = getSortedClassIds(player);

        AutoFarmSkillSlot slot = farmState.getSkillSlot(slotIndex);
        if (slot == null) {
            return;
        }

        Connection con = null;
        PreparedStatement ps = null;
        try {
            con = DatabaseFactory.getInstance().getConnection();

            // If skillId == 0 => user cleared that slot => delete the row
            if (slot.getSkillId() <= 0) {
                ps = con.prepareStatement(DELETE_SINGLE_SQL);
                ps.setInt(1, objectId);
                ps.setInt(2, slotIndex);
                ps.setInt(3, classes[0]);
                ps.setInt(4, classes[1]);
                ps.executeUpdate();

                _log.info("AutoFarmSkillDAO: [SLOT-CLEAR] objectId=" + objectId + ", slotIndex=" + slotIndex +
                        ", classes=" + classes[0] + "," + classes[1]);
            } else {
                // REPLACE the single row
                ps = con.prepareStatement(REPLACE_SINGLE_SQL);
                ps.setInt(1, objectId);
                ps.setInt(2, slotIndex);
                ps.setInt(3, classes[0]);
                ps.setInt(4, classes[1]);

                ps.setInt(5, slot.getSkillId());
                ps.setDouble(6, slot.getTargetHpPercent());
                ps.setDouble(7, slot.getTargetMpPercent());
                ps.setDouble(8, slot.getTargetCpPercent());
                ps.setLong(9, slot.getReuseDelayMs());

                ps.setInt(10, slot.isPartySkill() ? 1 : 0);
                ps.setInt(11, slot.isAllySkill() ? 1 : 0);
                ps.setInt(12, slot.isSelfBuff() ? 1 : 0);
                ps.setInt(13, slot.isAutoReuse() ? 1 : 0);

                ps.executeUpdate();

                _log.info("AutoFarmSkillDAO: [SLOT-SAVE] objectId=" + objectId + ", slotIndex=" + slotIndex +
                        ", skillId=" + slot.getSkillId() +
                        " classes=" + classes[0] + "," + classes[1]);
            }
        } catch (Exception e) {
            _log.error("AutoFarmSkillDAO: saveSingleSlotForStackClass error for slotIndex=" +
                    slotIndex + " player " + player.getName(), e);
        } finally {
            DbUtils.closeQuietly(ps);
            DbUtils.closeQuietly(con);
        }
    }

    // -------------------------------------------------------------------------
    // Old method that saved all 72. We do not call it anymore, but you can keep
    // it if you wish for debugging or for a "batch save" scenario.
    // If you want to remove it completely, you can.
    // -------------------------------------------------------------------------
    /*
     * public void saveSkillsForStackClass(Player player, AutoFarmState farmState) {
     * // DEPRECATED: We only do single-slot updates now.
     * // If you want to keep an "all 72" approach, you can re-implement.
     * }
     */

    // -------------------------------------------------------------------------
    // Helper: returns the player's two class IDs sorted ascending
    // -------------------------------------------------------------------------
    private int[] getSortedClassIds(Player player) {
        int c1 = player.getFirstClassId();
        int c2 = player.getSecondaryClassId();
        int[] arr = { c1, c2 };
        Arrays.sort(arr);
        return arr;
    }
}
