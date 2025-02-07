package services.community;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

import l2ft.gameserver.Config;
import l2ft.gameserver.dao.CommunityPathsDAO;
import l2ft.gameserver.data.htm.HtmCache;
import l2ft.gameserver.handler.bbs.CommunityBoardManager;
import l2ft.gameserver.handler.bbs.ICommunityBoardHandler;
import l2ft.gameserver.listener.actor.player.OnAnswerListener;
import l2ft.gameserver.model.Party;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.Skill;
import l2ft.gameserver.network.l2.components.SystemMsg;
import l2ft.gameserver.network.l2.s2c.ConfirmDlg;
import l2ft.gameserver.network.l2.s2c.ShowBoard;
import l2ft.gameserver.scripts.Functions;
import l2ft.gameserver.scripts.ScriptFile;
import l2ft.gameserver.utils.BbsUtil;

// Added for immediate DB saves
import l2ft.gameserver.dao.AutoFarmSkillDAO;

import l2ft.gameserver.autofarm.AutoFarmConfig;
import l2ft.gameserver.autofarm.AutoFarmEngine;
import l2ft.gameserver.autofarm.AutoFarmSkillSlot;
import l2ft.gameserver.autofarm.AutoFarmState;
import l2ft.gameserver.autofarm.AutoFarmState.EMoveMethod;
import l2ft.gameserver.autofarm.ESearchType;
import l2ft.gameserver.autofarm.PathData;

/**
 * The main Community Board handler for the AutoFarm system.
 */
public class AutoFarmPage extends Functions implements ScriptFile, ICommunityBoardHandler {
    private static final String MAIN_HTML = "autofarm/bbs_autoFarmMain.htm";
    private static final String SKILL_HTML = "autofarm/bbs_autoFarmSkill.htm";

    // Skills to exclude from the "available list"
    private static final Integer[] EXCLUDED_IDS_ARRAY = {
            246, 247, 326, 623, 624, 780, 813, 933, 994, 995, 1321, 1322, 1521, 2099, 22042
    };
    private static final Set<Integer> EXCLUDED_SKILL_IDS = new HashSet<Integer>(Arrays.asList(EXCLUDED_IDS_ARRAY));

    // For spoil check
    private static final int SPOIL_ID = 254;
    private static final int SPOIL_FESTIVAL_ID = 302;
    private static final int SWEEPER_ID = 42;

    @Override
    public String[] getBypassCommands() {
        return new String[] { "_bbsautofarm" };
    }

    @Override
    public void onBypassCommand(Player player, String bypass) {
        if (player == null)
            return;

        StringTokenizer st = new StringTokenizer(bypass, " ");
        if (!st.hasMoreTokens())
            return;

        String cmd = st.nextToken();
        if (!"_bbsautofarm".equals(cmd))
            return;

        String subCmd = st.hasMoreTokens() ? st.nextToken().toLowerCase() : "main";

        if ("main".equals(subCmd)) {
            showMainPage(player);
        } else if ("start".equals(subCmd)) {
            confirmStart(player);
        } else if ("stop".equals(subCmd)) {
            doStopAutoFarm(player);
        } else if ("refresh".equals(subCmd)) {
            showMainPage(player);
        } else if ("movetype".equals(subCmd)) {
            if (!st.hasMoreTokens())
                return;
            String mType = st.nextToken().toLowerCase();

            AutoFarmState farmState = AutoFarmEngine.getInstance().getOrCreateState(player);
            PathData pd = PathData.get(farmState);

            if ("off".equals(mType)) {
                farmState.setMoveMethod(EMoveMethod.OFF);
                player.sendMessage("Movement mode set to OFF.");
                pd.renderAllPaths(player, farmState.isActive());
            } else if ("follow".equals(mType)) {
                farmState.setMoveMethod(EMoveMethod.FOLLOW);
                player.sendMessage("Movement mode set to FOLLOW (party).");
                pd.renderAllPaths(player, farmState.isActive());
            } else if ("path".equals(mType)) {
                farmState.setMoveMethod(EMoveMethod.PATH);
                if (pd.getSelectedPath() == null) {
                    player.sendMessage("No path selected! Please select one in the Path Editor.");
                } else {
                    player.sendMessage("Movement mode set to PATH. Using path: " + pd.getSelectedPath());
                }
                pd.renderAllPaths(player, farmState.isActive());
            } else {
                farmState.setMoveMethod(EMoveMethod.OFF);
                player.sendMessage("Unknown movement mode: " + mType + ". Setting to OFF as fallback.");
                pd.renderAllPaths(player, farmState.isActive());
            }

            showMainPage(player);
        } else if ("setparty".equals(subCmd)) {
            if (!st.hasMoreTokens())
                return;
            String partyMemberName = st.nextToken();
            Party p = player.getParty();
            if (p == null) {
                player.sendMessage("You are not in a party.");
                showMainPage(player);
                return;
            }

            Player foundPm = null;
            for (Player pm : p.getPartyMembers()) {
                if (pm == player)
                    continue;
                if (pm.getName().equalsIgnoreCase(partyMemberName)) {
                    foundPm = pm;
                    break;
                }
            }
            if (foundPm == null) {
                player.sendMessage("Invalid party member name: " + partyMemberName);
                showMainPage(player);
                return;
            }

            AutoFarmState farmState = AutoFarmEngine.getInstance().getOrCreateState(player);
            farmState.setFollowTargetObjId(foundPm.getObjectId());
            player.sendMessage("Party target set to: " + foundPm.getName());
            showMainPage(player);
        } else if ("search".equals(subCmd)) {
            if (!st.hasMoreTokens())
                return;
            String searchStr = st.nextToken();
            ESearchType newType;
            try {
                newType = ESearchType.valueOf(searchStr.toUpperCase());
            } catch (Exception e) {
                newType = ESearchType.OFF;
            }

            AutoFarmState farmState = AutoFarmEngine.getInstance().getOrCreateState(player);
            farmState.setSearchType(newType);
            String displayName = capitalizeSearchType(newType);
            player.sendMessage("AutoFarm search type changed to: " + displayName);

            if (newType == ESearchType.CLOSE
                    || newType == ESearchType.NEAR
                    || newType == ESearchType.FAR) {
                AutoFarmEngine.getInstance().scheduleOrResumeTask(player);
            }
            showMainPage(player);
        } else if ("setz".equals(subCmd)) {
            if (!st.hasMoreTokens())
                return;
            String valStr = st.nextToken();
            try {
                int val = Integer.parseInt(valStr);
                if (val < 0)
                    val = 0;
                if (val > 9999)
                    val = 9999;

                AutoFarmState farmState = AutoFarmEngine.getInstance().getOrCreateState(player);
                farmState.setZRangeLimit(val);
                player.sendMessage("AutoFarm Z-range set to: " + val);
            } catch (NumberFormatException ex) {
                player.sendMessage("Invalid Z-range input. Must be an integer 0–9999.");
            }
            showMainPage(player);
        } else if ("spoil".equals(subCmd)) {
            AutoFarmState farmState = AutoFarmEngine.getInstance().getOrCreateState(player);
            boolean hasSpoil = (player.getKnownSkill(SPOIL_ID) != null);
            boolean hasSpoilFest = (player.getKnownSkill(SPOIL_FESTIVAL_ID) != null);
            boolean hasSweeper = (player.getKnownSkill(SWEEPER_ID) != null);
            if (!(hasSpoil && hasSpoilFest && hasSweeper)) {
                player.sendMessage("You do not have the required spoil/sweeper skills. Cannot enable spoil mode.");
                farmState.setSpoilEnabled(false);
                showMainPage(player);
                return;
            }
            farmState.setSpoilEnabled(!farmState.isSpoilEnabled());
            String msg = "Spoil is now " + (farmState.isSpoilEnabled() ? "ENABLED." : "DISABLED.");
            player.sendMessage(msg);
            showMainPage(player);
        } else if ("skill".equals(subCmd)) {
            if (!st.hasMoreTokens())
                return;
            int slotIndex = Integer.parseInt(st.nextToken());
            int pageNum = 0;
            if (st.hasMoreTokens()) {
                pageNum = Integer.parseInt(st.nextToken());
            }
            showSkillPage(player, slotIndex, pageNum);
        } else if ("skillset".equals(subCmd)) {
            if (st.countTokens() < 2)
                return;
            int slotIdx = Integer.parseInt(st.nextToken());
            int skillId = Integer.parseInt(st.nextToken());

            AutoFarmState farmState = AutoFarmEngine.getInstance().getOrCreateState(player);
            AutoFarmSkillSlot slot = farmState.getSkillSlot(slotIdx);
            if (slot != null) {
                slot.setSkillId(skillId);
                // Immediately save changes to DB
                AutoFarmSkillDAO.getInstance().saveSingleSlotForStackClass(player, farmState, slotIdx);
            }
            showSkillPage(player, slotIdx, 0);
        } else if ("skillclear".equals(subCmd)) {
            if (!st.hasMoreTokens())
                return;
            int slotIdx = Integer.parseInt(st.nextToken());

            AutoFarmState farmState = AutoFarmEngine.getInstance().getOrCreateState(player);
            AutoFarmSkillSlot slot = farmState.getSkillSlot(slotIdx);
            if (slot != null) {
                slot.setSkillId(0);
                // Immediately save changes to DB
                AutoFarmSkillDAO.getInstance().saveSingleSlotForStackClass(player, farmState, slotIdx);
            }
            showSkillPage(player, slotIdx, 0);
        } else if ("skillhp".equals(subCmd)) {
            if (st.countTokens() < 3)
                return;
            int slotIdx = Integer.parseInt(st.nextToken());
            double val = Double.parseDouble(st.nextToken());
            st.nextToken(); // skip 'user'/'target'

            AutoFarmState farmState = AutoFarmEngine.getInstance().getOrCreateState(player);
            AutoFarmSkillSlot sSlot = farmState.getSkillSlot(slotIdx);
            if (sSlot != null) {
                sSlot.setTargetHpPercent(val);
                AutoFarmSkillDAO.getInstance().saveSingleSlotForStackClass(player, farmState, slotIdx);
            }
            showSkillPage(player, slotIdx, 0);
        } else if ("skillmid".equals(subCmd)) {
            if (st.countTokens() < 2)
                return;
            int slotIdx = Integer.parseInt(st.nextToken());
            double val = Double.parseDouble(st.nextToken());

            AutoFarmState farmState = AutoFarmEngine.getInstance().getOrCreateState(player);
            AutoFarmSkillSlot sSlot = farmState.getSkillSlot(slotIdx);
            if (sSlot != null) {
                sSlot.setTargetMpPercent(val);
                AutoFarmSkillDAO.getInstance().saveSingleSlotForStackClass(player, farmState, slotIdx);
            }
            showSkillPage(player, slotIdx, 0);
        } else if ("skillcp".equals(subCmd)) {
            if (st.countTokens() < 2)
                return;
            int slotIdx = Integer.parseInt(st.nextToken());
            double cpVal = Double.parseDouble(st.nextToken());

            AutoFarmState farmState = AutoFarmEngine.getInstance().getOrCreateState(player);
            AutoFarmSkillSlot sSlot = farmState.getSkillSlot(slotIdx);
            if (sSlot != null) {
                sSlot.setTargetCpPercent(cpVal);
                AutoFarmSkillDAO.getInstance().saveSingleSlotForStackClass(player, farmState, slotIdx);
            }
            showSkillPage(player, slotIdx, 0);
        } else if ("skillreuse".equals(subCmd)) {
            if (st.countTokens() < 2)
                return;
            int slotIdx = Integer.parseInt(st.nextToken());
            long reuseSec = Long.parseLong(st.nextToken());

            AutoFarmState farmState = AutoFarmEngine.getInstance().getOrCreateState(player);
            AutoFarmSkillSlot sSlot = farmState.getSkillSlot(slotIdx);
            if (sSlot != null) {
                sSlot.setReuseDelayMs(reuseSec * 1000L);
                AutoFarmSkillDAO.getInstance().saveSingleSlotForStackClass(player, farmState, slotIdx);
            }
            showSkillPage(player, slotIdx, 0);
        } else if ("skillparty".equals(subCmd)) {
            if (st.countTokens() < 2)
                return;
            int slotIdx = Integer.parseInt(st.nextToken());
            boolean isParty = Boolean.parseBoolean(st.nextToken());

            AutoFarmState farmState = AutoFarmEngine.getInstance().getOrCreateState(player);
            AutoFarmSkillSlot sSlot = farmState.getSkillSlot(slotIdx);
            if (sSlot != null) {
                sSlot.setPartySkill(isParty);
                AutoFarmSkillDAO.getInstance().saveSingleSlotForStackClass(player, farmState, slotIdx);
            }
            showSkillPage(player, slotIdx, 0);
        } else if ("skillally".equals(subCmd)) {
            if (st.countTokens() < 2)
                return;
            int slotIdx = Integer.parseInt(st.nextToken());
            boolean isAlly = Boolean.parseBoolean(st.nextToken());

            AutoFarmState farmState = AutoFarmEngine.getInstance().getOrCreateState(player);
            AutoFarmSkillSlot sSlot = farmState.getSkillSlot(slotIdx);
            if (sSlot != null) {
                sSlot.setAllySkill(isAlly);
                AutoFarmSkillDAO.getInstance().saveSingleSlotForStackClass(player, farmState, slotIdx);
            }
            showSkillPage(player, slotIdx, 0);
        } else if ("skillself".equals(subCmd)) {
            if (st.countTokens() < 2)
                return;
            int slotIdx = Integer.parseInt(st.nextToken());
            boolean isSelfBuff = Boolean.parseBoolean(st.nextToken());

            AutoFarmState farmState = AutoFarmEngine.getInstance().getOrCreateState(player);
            AutoFarmSkillSlot sSlot = farmState.getSkillSlot(slotIdx);
            if (sSlot != null) {
                sSlot.setSelfBuff(isSelfBuff);
                AutoFarmSkillDAO.getInstance().saveSingleSlotForStackClass(player, farmState, slotIdx);
            }
            showSkillPage(player, slotIdx, 0);
        } else if ("skillauto".equals(subCmd)) {
            if (st.countTokens() < 2)
                return;
            int slotIdx = Integer.parseInt(st.nextToken());
            boolean isAutoReuse = Boolean.parseBoolean(st.nextToken());

            AutoFarmState farmState = AutoFarmEngine.getInstance().getOrCreateState(player);
            AutoFarmSkillSlot sSlot = farmState.getSkillSlot(slotIdx);
            if (sSlot != null) {
                sSlot.setAutoReuse(isAutoReuse);
                AutoFarmSkillDAO.getInstance().saveSingleSlotForStackClass(player, farmState, slotIdx);
            }
            showSkillPage(player, slotIdx, 0);
        } else {
            showMainPage(player);
        }
    }

    private void showMainPage(Player player) {
        if (player == null)
            return;

        AutoFarmState farmState = AutoFarmEngine.getInstance().getOrCreateState(player);
        boolean running = farmState.isActive();
        ESearchType currType = farmState.getSearchType();
        int zRange = farmState.getZRangeLimit();
        boolean spoilEnabled = farmState.isSpoilEnabled();

        String html = HtmCache.getInstance().getNotNull(Config.BBS_HOME_DIR + MAIN_HTML, player);

        String status = running
                ? "<font color=\"00FF00\">Running</font>"
                : "<font color=\"FF0000\">Stopped</font>";
        html = html.replace("%AF_STATUS%", status);

        String displayTargetType = capitalizeSearchType(currType);
        html = html.replace("%AF_TARGET_TYPE%", displayTargetType);

        html = html.replace("%AF_Z_RANGE%", String.valueOf(zRange));

        String moveMethodStr;
        switch (farmState.getMoveMethod()) {
            case OFF:
                moveMethodStr = "Off";
                break;
            case FOLLOW:
                moveMethodStr = "Follow";
                break;
            case PATH:
                moveMethodStr = "Path";
                break;
            default:
                moveMethodStr = "Off";
                break;
        }
        html = html.replace("%AF_MOVE_METHOD%", moveMethodStr);

        // Spoil checkbox
        boolean hasSpoil = (player.getKnownSkill(SPOIL_ID) != null &&
                player.getKnownSkill(SPOIL_FESTIVAL_ID) != null &&
                player.getKnownSkill(SWEEPER_ID) != null);

        String spoilCheck = "L2UI.CheckBox";
        if (hasSpoil && spoilEnabled)
            spoilCheck = "L2UI.CheckBox_checked";
        html = html.replace("%SPOIL_CHECK%", spoilCheck);

        // We have 72 skill slots in total (4 rows × 18 columns)
        StringBuilder[] rowBuffers = new StringBuilder[4];
        for (int r = 0; r < 4; r++) {
            rowBuffers[r] = new StringBuilder();
        }

        for (int i = 0; i < 72; i++) {
            AutoFarmSkillSlot slot = farmState.getSkillSlot(i);
            String icon = "icon.skill0000";
            if (slot != null && slot.getSkillId() > 0) {
                icon = String.format("icon.skill%04d", slot.getSkillId());
            }
            String btn = "<td align=\"center\">"
                    + "<button action=\"bypass _bbsautofarm skill " + i + " 0\" "
                    + "width=\"32\" height=\"32\" "
                    + "back=\"" + icon + "\" fore=\"" + icon + "\">"
                    + "</td>";
            int rowIndex = i / 18; // 0..3
            rowBuffers[rowIndex].append(btn);
        }

        html = html.replace("%SKILL_BUTTONS_ROW0%", rowBuffers[0].toString());
        html = html.replace("%SKILL_BUTTONS_ROW1%", rowBuffers[1].toString());
        html = html.replace("%SKILL_BUTTONS_ROW2%", rowBuffers[2].toString());
        html = html.replace("%SKILL_BUTTONS_ROW3%", rowBuffers[3].toString());

        // Party combobox
        StringBuilder sbParty = new StringBuilder();
        Party party = player.getParty();
        if (party != null) {
            int count = 0;
            for (Player pm : party.getPartyMembers()) {
                if (pm == player)
                    continue;
                sbParty.append(pm.getName()).append(";");
                count++;
            }
            if (count == 0)
                sbParty.append("No Party");
        } else {
            sbParty.append("No Party");
        }
        String partyList = sbParty.toString();
        if (partyList.endsWith(";")) {
            partyList = partyList.substring(0, partyList.length() - 1);
        }
        html = html.replace("%PARTY_MEMBER_LIST%", partyList);

        // Send to client
        html = BbsUtil.htmlAll(html, player);
        ShowBoard.separateAndSend(html, player);
    }

    private void confirmStart(final Player player) {
        ConfirmDlg dlg = new ConfirmDlg(SystemMsg.S1, 30000).addString("Start AutoFarm now?");
        player.ask(dlg, new OnAnswerListener() {
            @Override
            public void sayYes() {
                AutoFarmEngine.getInstance().startAutoFarm(player);
                showMainPage(player);
            }

            @Override
            public void sayNo() {
            }
        });
    }

    private void doStopAutoFarm(Player player) {
        AutoFarmEngine.getInstance().stopAutoFarm(player);
        showMainPage(player);
    }

    /**
     * Displays the skill editor page for the given slotIndex & pageNum.
     * We now label pages as “Page 1,” “Page 2,” etc. so the client does not
     * interpret single digits as item codes.
     */
    private void showSkillPage(Player player, int slotIndex, int pageNum) {
        AutoFarmState farmState = AutoFarmEngine.getInstance().getOrCreateState(player);
        AutoFarmSkillSlot slot = farmState.getSkillSlot(slotIndex);

        String html = HtmCache.getInstance().getNotNull(Config.BBS_HOME_DIR + SKILL_HTML, player);

        // Gather all non-passive, non-toggle, non-excluded
        List<Skill> allSkills = new ArrayList<Skill>(player.getAllSkills());
        for (Iterator<Skill> itr = allSkills.iterator(); itr.hasNext();) {
            Skill s = itr.next();
            if (s.isPassive() || s.isToggle() || EXCLUDED_SKILL_IDS.contains(s.getId())) {
                itr.remove();
            }
        }

        final int PAGE_SIZE = 12;
        int totalSkillCount = allSkills.size();
        // total pages
        int totalPages = (int) Math.ceil((double) totalSkillCount / PAGE_SIZE);

        // clamp
        if (pageNum < 0)
            pageNum = 0;
        if (totalPages == 0) {
            totalPages = 1;
            pageNum = 0;
        } else if (pageNum >= totalPages) {
            pageNum = totalPages - 1;
        }

        int startIdx = pageNum * PAGE_SIZE;
        int endIdx = Math.min(totalSkillCount, startIdx + PAGE_SIZE);

        // Debug
        System.out.println("[AutoFarmPage:showSkillPage] skill count=" + totalSkillCount
                + ", PAGE_SIZE=" + PAGE_SIZE
                + ", requested pageNum=" + pageNum
                + ", totalPages=" + totalPages
                + ", startIdx=" + startIdx
                + ", endIdx=" + endIdx);

        // build skill table
        StringBuilder skillTable = new StringBuilder();
        int curIndex = startIdx;
        for (int row = 0; row < 3; row++) {
            skillTable.append("<tr>");
            for (int col = 0; col < 4; col++) {
                if (curIndex < endIdx) {
                    Skill sk = allSkills.get(curIndex);
                    int skillId = sk.getId();
                    skillTable.append("<td align=\"center\" height=\"70\" width=\"188\">");
                    skillTable.append("<button action=\"bypass _bbsautofarm skillset ")
                            .append(slotIndex).append(" ").append(skillId)
                            .append("\" width=\"32\" height=\"32\" ")
                            .append("back=\"icon.skill").append(String.format("%04d", skillId)).append("\" ")
                            .append("fore=\"icon.skill").append(String.format("%04d", skillId)).append("\">");

                    // no closing </button> to avoid black edges
                    skillTable.append("<br><font color=\"808080\">");
                    skillTable.append("<center>").append(sk.getName()).append("</center>");
                    skillTable.append("</font></td>");

                    curIndex++;
                } else {
                    skillTable.append("<td width=\"60\"></td>");
                }
            }
            skillTable.append("</tr>");
        }
        html = html.replace("%SKILL_LIST%", skillTable.toString());

        // page links labeled as “Page X”
        StringBuilder pages = new StringBuilder("<table border=0 cellpadding=0 cellspacing=0><tr>");
        for (int p = 0; p < totalPages; p++) {
            pages.append("<td>");
            String pageLabel = "Page " + (p + 1); // e.g. "Page 1", "Page 2", ...
            if (p == pageNum) {
                // highlight
                pages.append("<font color=\"LEVEL\">").append(pageLabel).append("</font>");
            } else {
                pages.append("<a action=\"bypass _bbsautofarm skill ")
                        .append(slotIndex).append(" ").append(p)
                        .append("\">")
                        .append(pageLabel)
                        .append("</a>");
            }
            pages.append("</td>");

            // put a pipe and forced width except after last page
            if (p < (totalPages - 1)) {
                pages.append("<td width=\"15\" align=\"center\" valign=\"middle\">|</td>");
            }

            // debug
            System.out.println("[AutoFarmPage:showSkillPage] building link for p=" + p
                    + " => link text='" + pageLabel + "'");
        }
        pages.append("</tr></table>");
        System.out.println("[AutoFarmPage:showSkillPage] pages html snippet = " + pages.toString());
        html = html.replace("%PAGE_LIST%", pages.toString());

        // fill placeholders for selected skill
        String selectedIcon = "icon.skill0000";
        String selectedName = "(No skill)";
        if (slot != null && slot.getSkillId() > 0) {
            Skill s = player.getKnownSkill(slot.getSkillId());
            if (s != null) {
                selectedName = s.getName();
                selectedIcon = String.format("icon.skill%04d", s.getId());
            }
        }
        html = html.replace("%SELECTED_ICON%", selectedIcon);
        html = html.replace("%SELECTED_SKILL_ID%", selectedName);

        double cpVal = (slot != null) ? slot.getTargetCpPercent() : 100.0;
        double hpVal = (slot != null) ? slot.getTargetHpPercent() : 100.0;
        double mpVal = (slot != null) ? slot.getTargetMpPercent() : 100.0;
        long reuseSec = (slot != null) ? (slot.getReuseDelayMs() / 1000L) : 0;

        html = html.replace("%SLOT%", String.valueOf(slotIndex));
        html = html.replace("%TARGET_CP%", String.format("%.0f%%", cpVal));
        html = html.replace("%TARGET_HP%", String.format("%.0f%%", hpVal));
        html = html.replace("%MID_VALUE%", String.format("%.0f%%", mpVal));
        html = html.replace("%REUSE_SEC%", String.valueOf(reuseSec));

        // toggles
        boolean partySkill = (slot != null && slot.isPartySkill());
        String partyCheck = partySkill ? "L2UI.CheckBox_checked" : "L2UI.CheckBox";
        html = html.replace("%PARTY_SKILL_CHECK%", partyCheck);
        String partyToggleVal = partySkill ? "false" : "true";
        html = html.replace("%PARTY_SKILL_TOGGLE%", partyToggleVal);

        boolean allySkill = (slot != null && slot.isAllySkill());
        String allyCheck = allySkill ? "L2UI.CheckBox_checked" : "L2UI.CheckBox";
        html = html.replace("%ALLY_SKILL_CHECK%", allyCheck);
        String allyToggleVal = allySkill ? "false" : "true";
        html = html.replace("%ALLY_SKILL_TOGGLE%", allyToggleVal);

        boolean selfBuff = (slot != null && slot.isSelfBuff());
        String selfCheck = selfBuff ? "L2UI.CheckBox_checked" : "L2UI.CheckBox";
        html = html.replace("%SELF_SKILL_CHECK%", selfCheck);
        String selfToggleVal = selfBuff ? "false" : "true";
        html = html.replace("%SELF_SKILL_TOGGLE%", selfToggleVal);

        boolean autoReuse = (slot != null && slot.isAutoReuse());
        String autoCheck = autoReuse ? "L2UI.CheckBox_checked" : "L2UI.CheckBox";
        html = html.replace("%AUTO_SKILL_CHECK%", autoCheck);
        String autoToggleVal = autoReuse ? "false" : "true";
        html = html.replace("%AUTO_SKILL_TOGGLE%", autoToggleVal);

        // final
        html = BbsUtil.htmlAll(html, player);
        ShowBoard.separateAndSend(html, player);
    }

    /** Capitalize ESearchType. */
    private String capitalizeSearchType(ESearchType st) {
        if (st == null)
            return "Off";
        switch (st) {
            case OFF:
                return "Off";
            case ASSIST:
                return "Assist";
            case CLOSE:
                return "Close";
            case NEAR:
                return "Near";
            case FAR:
                return "Far";
        }
        return "Off";
    }

    @Override
    public void onWriteCommand(Player player, String bypass,
            String arg1, String arg2, String arg3,
            String arg4, String arg5) {
        // Not used
    }

    @Override
    public void onLoad() {
        CommunityBoardManager.getInstance().registerHandler(this);
        // load DB data
        CommunityPathsDAO.getInstance().selectAll();
    }

    @Override
    public void onReload() {
        CommunityBoardManager.getInstance().removeHandler(this);
    }

    @Override
    public void onShutdown() {
        // no-op
    }
}
