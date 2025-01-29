package l2ft.gameserver.handler.usercommands.impl;

import l2ft.gameserver.handler.usercommands.IUserCommandHandler;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.entity.events.impl.ForgottenBattlegroundsManager;

public class ForgottenBattlegroundsJoin implements IUserCommandHandler {
    private static final int[] COMMAND_IDS = { 118 }; // Example command ID

    @Override
    public boolean useUserCommand(int id, Player activeChar) {
        if (id != COMMAND_IDS[0])
            return false;

        ForgottenBattlegroundsManager manager = ForgottenBattlegroundsManager.getInstance();
        if (!manager.isEventRunning()) {
            activeChar.sendMessage("The event is not currently running.");
            return false;
        }

        // Directly register player to first available team
        manager.registerPlayer(activeChar, 0);
        activeChar.sendMessage("You have joined the Forgotten Battlegrounds queue.");
        return true;
    }

    @Override
    public int[] getUserCommandList() {
        return COMMAND_IDS;
    }
}
