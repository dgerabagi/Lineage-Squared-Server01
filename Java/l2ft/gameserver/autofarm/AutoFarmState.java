package l2ft.gameserver.autofarm;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

import l2ft.gameserver.autofarm.AutoFarmState.EMoveMethod;
import l2ft.gameserver.model.Party;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.network.l2.s2c.ExServerPrimitive;

public class AutoFarmState {
    private final Player _player;
    private boolean _active;
    private ScheduledFuture<?> _task;
    private ESearchType _searchType = ESearchType.OFF;

    public enum EMoveMethod {
        OFF,
        FOLLOW,
        PATH
    }

    private EMoveMethod _moveMethod = EMoveMethod.OFF;
    private int _followTargetObjId;
    private int _zRangeLimit = 500;

    private int _pathIndex = 0;

    // Now we have 72 skill slots (4 rows × 18 columns).
    private final AutoFarmSkillSlot[] _skillSlots = new AutoFarmSkillSlot[72];

    // Arbitrary custom data
    private final Map<String, Object> _customData = new HashMap<String, Object>();

    // NEW: spoil toggle
    private boolean _spoilEnabled = false;

    public AutoFarmState(Player player) {
        _player = player;
        _active = false;
        for (int i = 0; i < 72; i++) {
            _skillSlots[i] = new AutoFarmSkillSlot();
        }
    }

    public Player getPlayer() {
        return _player;
    }

    public boolean isActive() {
        return _active;
    }

    public void setActive(boolean val) {
        _active = val;
    }

    public ScheduledFuture<?> getTask() {
        return _task;
    }

    public void setTask(ScheduledFuture<?> task) {
        _task = task;
    }

    public void cancelTask() {
        if (_task != null) {
            _task.cancel(false);
            _task = null;
        }
    }

    public ESearchType getSearchType() {
        return _searchType;
    }

    public void setSearchType(ESearchType newType) {
        _searchType = newType;
        renderRange();
    }

    public EMoveMethod getMoveMethod() {
        return _moveMethod;
    }

    public void setMoveMethod(EMoveMethod method) {
        _moveMethod = method;
    }

    public int getFollowTargetObjId() {
        return _followTargetObjId;
    }

    public void setFollowTargetObjId(int objId) {
        _followTargetObjId = objId;
    }

    public Player getFollowTargetPlayer() {
        if (_followTargetObjId <= 0)
            return null;
        Party p = _player.getParty();
        if (p == null)
            return null;
        for (Player pm : p.getPartyMembers()) {
            if (pm.getObjectId() == _followTargetObjId) {
                return pm;
            }
        }
        return null;
    }

    public AutoFarmSkillSlot getSkillSlot(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= _skillSlots.length)
            return null;
        return _skillSlots[slotIndex];
    }

    public int getZRangeLimit() {
        return _zRangeLimit;
    }

    public void setZRangeLimit(int val) {
        _zRangeLimit = Math.max(0, Math.min(val, 9999));
    }

    public int getPathIndex() {
        return _pathIndex;
    }

    public void setPathIndex(int idx) {
        _pathIndex = idx;
    }

    public Object getCustomData(String key) {
        return _customData.get(key);
    }

    public void setCustomData(String key, Object value) {
        _customData.put(key, value);
    }

    public boolean isSpoilEnabled() {
        return _spoilEnabled;
    }

    public void setSpoilEnabled(boolean val) {
        _spoilEnabled = val;
    }

    /**
     * Draw a circle in green if active, otherwise red; skip if OFF or ASSIST.
     */
    public void renderRange() {
        Player pl = getPlayer();
        if (pl == null)
            return;

        if (_searchType == ESearchType.OFF || _searchType == ESearchType.ASSIST) {
            clearCircle();
            return;
        }

        int range = _searchType.getRange();
        if (range < 1) {
            clearCircle();
            return;
        }

        Color circleColor = (_active ? Color.GREEN : Color.RED);
        int centerX = pl.getX();
        int centerY = pl.getY();
        int centerZ = -65535;
        ExServerPrimitive sp = new ExServerPrimitive("AutoFarmRange", centerX, centerY, centerZ);
        int circleZ = pl.getZ();

        for (int i = 0; i < 360; i++) {
            double rad1 = Math.toRadians(i);
            double rad2 = Math.toRadians(i + 1);
            int x1 = pl.getX() + (int) (range * Math.cos(rad1));
            int y1 = pl.getY() + (int) (range * Math.sin(rad1));
            int x2 = pl.getX() + (int) (range * Math.cos(rad2));
            int y2 = pl.getY() + (int) (range * Math.sin(rad2));
            sp.addLine("", circleColor, false, x1, y1, circleZ, x2, y2, circleZ);
        }
        pl.sendPacket(sp);
    }

    /**
     * Clears all 72 skill slots to default (skillId=0, etc.).
     */
    public void resetAllSkillSlots() {
        for (int i = 0; i < 72; i++) {
            AutoFarmSkillSlot slot = _skillSlots[i];
            slot.setSkillId(0);
            slot.setTargetHpPercent(100.0);
            slot.setTargetMpPercent(100.0);
            slot.setTargetCpPercent(100.0);
            slot.setReuseDelayMs(0);
            slot.setPartySkill(false);
            slot.setAllySkill(false);
            slot.setSelfBuff(false);
            slot.setAutoReuse(false);
        }
    }

    public void clearCircle() {
        Player pl = getPlayer();
        if (pl == null)
            return;
        ExServerPrimitive sp = new ExServerPrimitive("AutoFarmRange", pl.getX(), pl.getY(), -200000);
        sp.addLine("", Color.DARK_GRAY, false,
                pl.getX(), pl.getY(), -200000,
                pl.getX() + 1, pl.getY(), -200000);
        pl.sendPacket(sp);
    }
}
