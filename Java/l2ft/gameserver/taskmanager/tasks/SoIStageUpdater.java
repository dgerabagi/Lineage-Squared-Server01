package l2ft.gameserver.taskmanager.tasks;

import java.util.Calendar;

import l2ft.gameserver.instancemanager.SoIManager;
import l2ft.gameserver.taskmanager.Task;
import l2ft.gameserver.taskmanager.TaskManager;
import l2ft.gameserver.taskmanager.TaskManager.ExecutedTask;
import l2ft.gameserver.taskmanager.TaskTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * @author pchayka
 */
public class SoIStageUpdater extends Task
{
	private static final Logger _log = LoggerFactory.getLogger(SoIStageUpdater.class);
	private static final String NAME = "soi_update";

	@Override
	public void initializate()
	{
		TaskManager.addUniqueTask(getName(), TaskTypes.TYPE_GLOBAL_TASK, "1", "12:00:00", "");
	}

	@Override
	public String getName()
	{
		return NAME;
	}

	@Override
	public void onTimeElapsed(ExecutedTask task)
	{
		if(Calendar.getInstance().get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY)
		{
			SoIManager.setCurrentStage(1);
			_log.info("Seed of Infinity update Task: Seed updated successfuly.");
		}
	}
}