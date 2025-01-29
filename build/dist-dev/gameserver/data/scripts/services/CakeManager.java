package services;

import handler.items.Cake;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map.Entry;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import l2ft.commons.dbutils.DbUtils;
import l2ft.gameserver.database.DatabaseFactory;
import l2ft.gameserver.model.instances.NpcInstance;
import l2ft.gameserver.scripts.Functions;
import l2ft.gameserver.scripts.ScriptFile;
import l2ft.gameserver.utils.Location;

public class CakeManager extends Functions implements ScriptFile
{
	public static final Logger _log = LoggerFactory.getLogger(CakeManager.class);

	@Override
	public void onLoad()
	{
		int index = 0;
		Connection con = null;
		PreparedStatement statement = null;
		ResultSet rset = null;
		try
		{
			con = DatabaseFactory.getInstance().getConnection();
			statement = con.prepareStatement("SELECT * FROM spawned_npcs");
			rset = statement.executeQuery();
			while(rset.next())
			{
				int npcId = rset.getInt("npc_id");
				Location loc = new Location(rset.getInt("loc_x"), rset.getInt("loc_y"), rset.getInt("loc_z"));
				long timeLeft = rset.getLong("time_left")*1000;
				
				Cake.spawnNpc(npcId, loc, "", timeLeft);
				index ++;
			}
			
			DbUtils.close(statement);
			statement = con.prepareStatement("DELETE FROM spawned_npcs");
			statement.execute();
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		finally
		{
			DbUtils.closeQuietly(con, statement, rset);
		}
		_log.info("We have succesfully restered "+index+" Cakes!");
	}
	
	@Override
	public void onReload()
	{
		
	}
	
	@Override
	public void onShutdown()
	{
		int index = 0;
		Connection con = null;
		PreparedStatement statement = null;
		try
		{
			con = DatabaseFactory.getInstance().getConnection();
			statement = con.prepareStatement("INSERT INTO spawned_npcs SET npc_id=?, loc_x=?, loc_y=?, loc_z=?, time_left=?");
			for(Entry<NpcInstance, ScheduledFuture<?>> cake : Cake.getCakeTasks().entrySet())
			{
				if(cake.getKey() == null || cake.getValue().isDone())
					continue;
				
				NpcInstance npc = cake.getKey();
				statement.setInt(1, npc.getNpcId());
				statement.setInt(2, npc.getLoc().getX());
				statement.setInt(3, npc.getLoc().getY());
				statement.setInt(4, npc.getLoc().getZ());
				statement.setLong(5, cake.getValue().getDelay(TimeUnit.SECONDS));
				statement.execute();
				index++;
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		finally
		{
			DbUtils.closeQuietly(con, statement);
		}
		_log.info(index+" Cakes have been stored!");
		
	}
}