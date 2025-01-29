package l2ft.gameserver.handler.usercommands.impl;

import l2ft.gameserver.handler.usercommands.IUserCommandHandler;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.entity.events.impl.IslandAssaultManager;

/**
 * IslandAssaultJoin
 *
 * Cloned from ForgottenBattlegroundsJoin, but hooking to IslandAssault logic.
 * Typically used if you have a custom user command (like /joinia or something).
 */
public class IslandAssaultJoin implements IUserCommandHandler {
    private static final int[] COMMAND_IDS = { 119 }; // an example new command ID

    @Override
    public boolean useUserCommand(int id, Player activeChar) {
        if (id != COMMAND_IDS[0])
            return false;

        IslandAssaultManager manager = IslandAssaultManager.getInstance();
        if (!manager.isEventRunning()) {
            activeChar.sendMessage("Island Assault is not currently running.");
            return false;
        }

        // Directly register player to Team A or B as you see fit.
        // For example, always Team A for now:
        manager.registerPlayer(activeChar, 1);
        activeChar.sendMessage("You have joined the Island Assault queue for Team A.");
        return true;
    }

    @Override
    public int[] getUserCommandList() {
        return COMMAND_IDS;
    }
}
