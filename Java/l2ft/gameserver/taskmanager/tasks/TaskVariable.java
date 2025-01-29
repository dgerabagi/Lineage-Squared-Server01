package l2ft.gameserver.taskmanager.tasks;

import l2ft.gameserver.database.mysql;
import l2ft.gameserver.instancemanager.ReflectionManager;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.taskmanager.PlayerTaskManager;

public class TaskVariable extends PlayerTaskManager
{
	private Player _player;
	private TaskType _type;
	private long _taskEnd;
	
	public static enum TaskType
	{
		Chat_ban,
		Trade_ban,
		Jail,
		PartySummon
	}
	
	public TaskVariable(Player player, TaskType type, long time)
	{
		_player = player;
		_type = type;
		_taskEnd = time;
	}
	
	public void shutdown()
	{
		mysql.set("INSERT INTO character_variables (obj_id, type, name, value, expire_time) VALUES (?,'user-var',?,?,?)", _player.getObjectId(), _type.toString(), "true", _taskEnd-System.currentTimeMillis());
	}
	
	public void over()
	{
		removeEffect();
	}
	
	public TaskType getType()
	{
		return _type;
	}
	
	private void removeEffect()
	{
		_player.removeTaskFromList(_type);
		_player.unsetVar(_type.toString());
		switch(_type)
		{
		case Chat_ban:
			_player.sendMessage("You can speak again!");
			break;
		case Trade_ban:
			_player.sendMessage("You can use private shop again!");
			break;
		case Jail:
			_player.unblock();
			_player.standUp();
			_player.teleToLocation(17817, 170079, -3530, ReflectionManager.DEFAULT);
			break;
		case PartySummon:
			break;
		}
	}
}