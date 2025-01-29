package l2ft.gameserver.model.instances;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import l2ft.commons.dbutils.DbUtils;
import l2ft.gameserver.database.DatabaseFactory;
import l2ft.gameserver.model.Creature;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.entity.Hero;
import l2ft.gameserver.model.entity.HeroDiary;
import l2ft.gameserver.templates.npc.NpcTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents GrandBosses (epic/GrandBoss).
 * Logs kills into `raidboss_history` but **no longer** includes respawn_date.
 */
public class BossInstance extends RaidBossInstance {
	private static final Logger _log = LoggerFactory.getLogger(BossInstance.class);

	private boolean _teleportedToNest;

	public BossInstance(int objectId, NpcTemplate template) {
		super(objectId, template);
	}

	@Override
	public boolean isBoss() {
		return true; // It's an epic/GrandBoss by definition
	}

	@Override
	public final boolean isMovementDisabled() {
		// Example special case: Core is immobile
		return (getNpcId() == 29006) || super.isMovementDisabled();
	}

	@Override
	protected void onDeath(Creature killer) {
		// Standard hero-diary logic
		if (killer.isPlayable()) {
			Player player = killer.getPlayer();
			if (player != null) {
				if (player.isInParty()) {
					for (Player member : player.getParty().getPartyMembers()) {
						if (member.isNoble()) {
							Hero.getInstance().addHeroDiary(
									member.getObjectId(),
									HeroDiary.ACTION_RAID_KILLED,
									getNpcId());
						}
					}
				} else if (player.isNoble()) {
					Hero.getInstance().addHeroDiary(
							player.getObjectId(),
							HeroDiary.ACTION_RAID_KILLED,
							getNpcId());
				}
			}
		}

		// Insert epic kills into 'raidboss_history' (but no respawn_date)
		Player lastHitPlayer = (killer != null) ? killer.getPlayer() : null;
		if (lastHitPlayer != null) {
			insertUnifiedRaidBossHistory(lastHitPlayer);
		}

		super.onDeath(killer);
	}

	/**
	 * Insert row into `raidboss_history` for epic bosses,
	 * but we no longer store respawn_date.
	 */
	private void insertUnifiedRaidBossHistory(Player lastHitPlayer) {
		Connection con = null;
		PreparedStatement ps = null;
		ResultSet rs = null;

		long now = System.currentTimeMillis() / 1000L; // current UNIX time

		_log.info("[BossInstance] insertUnifiedRaidBossHistory: BossID="
				+ getNpcId() + ", killer=" + lastHitPlayer.getName() + ", timeNowSec=" + now);

		try {
			con = DatabaseFactory.getInstance().getConnection();

			// Gather last-hitter data
			int charId = lastHitPlayer.getObjectId();
			String charName = lastHitPlayer.getName();
			String clanName = "";
			String allyName = "";
			if (lastHitPlayer.getClan() != null) {
				clanName = lastHitPlayer.getClan().getName();
				if (lastHitPlayer.getClan().getAlliance() != null) {
					allyName = lastHitPlayer.getClan().getAlliance().getAllyName();
				}
			}

			// Insert row into raidboss_history (omitting respawn_date entirely)
			ps = con.prepareStatement(
					"INSERT INTO raidboss_history ("
							+ "boss_id, boss_name, kill_timestamp, "
							+ "last_hit_char_id, last_hit_char_name, "
							+ "last_hit_char_clan_name, last_hit_char_ally_name"
							+ ") VALUES (?,?,?,?,?,?,?)");

			ps.setInt(1, getNpcId());
			ps.setString(2, getName());
			ps.setLong(3, now);
			ps.setInt(4, charId);
			ps.setString(5, charName);
			ps.setString(6, clanName);
			ps.setString(7, allyName);

			ps.executeUpdate();

			_log.info("[BossInstance] Inserted epic boss kill for bossId="
					+ getNpcId() + " (no respawn_date logged).");

		} catch (SQLException e) {
			_log.warn("[BossInstance] Failed to insert raidboss_history for bossId="
					+ getNpcId() + ", charId=" + lastHitPlayer.getObjectId(), e);
		} finally {
			DbUtils.closeQuietly(con, ps, rs);
		}
	}

	public void setTeleported(boolean flag) {
		_teleportedToNest = flag;
	}

	public boolean isTeleported() {
		return _teleportedToNest;
	}

	@Override
	public boolean hasRandomAnimation() {
		return false;
	}
}
