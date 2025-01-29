package services.community;

import java.util.StringTokenizer;

import l2ft.gameserver.handler.bbs.CommunityBoardManager;
import l2ft.gameserver.handler.bbs.ICommunityBoardHandler;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.scripts.Functions;
import l2ft.gameserver.scripts.ScriptFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import l2ft.gameserver.autofarm.AutoFarmPathEditorLogic;

/**
 * Minimal script-based UI handler for the Path Editor.
 * The actual logic has been moved to AutoFarmPathEditorLogic in the "core"
 * code.
 */
public class AutoFarmPathEditorPage extends Functions implements ScriptFile, ICommunityBoardHandler {
    private static final Logger _log = LoggerFactory.getLogger(AutoFarmPathEditorPage.class);

    @Override
    public String[] getBypassCommands() {
        return new String[] { "_bbsautofarmpath" };
    }

    @Override
    public void onBypassCommand(Player player, String bypass) {
        if (player == null)
            return;

        // Example: "bypass _bbsautofarmpath:create:open: $PNAME"
        // We'll parse by splitting on ":" to see if second token is 'create' etc.
        if (!bypass.startsWith("_bbsautofarmpath"))
            return;

        // 1) strip any trailing ";..."
        String[] semicolonSplit = bypass.split(";");
        String firstChunk = semicolonSplit[0];

        // 2) now split on ":"
        String[] tokens = firstChunk.split(":");
        // tokens[0] = "_bbsautofarmpath"
        // tokens[1] = "create" (maybe)
        // tokens[2] = "open" (maybe)
        // tokens[3] = " $PNAME"
        String subCmd = (tokens.length > 1) ? tokens[1].toLowerCase() : "open";

        if ("create".equals(subCmd)) {
            String typedName = "";
            if (tokens.length > 3) {
                typedName = tokens[3].trim(); // remove leading/trailing spaces
                if (typedName.startsWith("$")) {
                    typedName = typedName.substring(1);
                }
            }
            _log.info("DEBUG: Received create command. typedName='" + typedName + "'");

            AutoFarmPathEditorLogic.getInstance().handleCreatePath(player, typedName);
            return;
        }

        // If not "create", then we treat it like old "space-based" commands:
        // e.g. _bbsautofarmpath open
        // _bbsautofarmpath remove MyPath
        // ...
        StringTokenizer st = new StringTokenizer(bypass, " ");
        // first token = _bbsautofarmpath
        if (st.hasMoreTokens())
            st.nextToken(); // skip
        String cmd = (st.hasMoreTokens()) ? st.nextToken().toLowerCase() : "open";

        // Delegate to logic
        AutoFarmPathEditorLogic.getInstance().handleSpaceBasedBypass(player, cmd, st);
    }

    @Override
    public void onWriteCommand(Player player, String bypass, String arg1, String arg2, String arg3, String arg4,
            String arg5) {
        // no-op
    }

    @Override
    public void onLoad() {
        CommunityBoardManager.getInstance().registerHandler(this);
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
