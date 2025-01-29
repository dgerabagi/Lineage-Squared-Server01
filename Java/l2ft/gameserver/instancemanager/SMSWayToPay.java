package l2ft.gameserver.instancemanager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.StringTokenizer;
import java.util.logging.Logger;

import l2ft.gameserver.Config;
import l2ft.gameserver.ThreadPoolManager;

public class SMSWayToPay
{
	private static Logger _log = Logger.getLogger(SMSWayToPay.class.getName());
	BufferedReader reader;

	private static SMSWayToPay _instance;

	public static SMSWayToPay getInstance()
	{
		if(_instance == null && Config.SMS_PAYMENT_MANAGER_ENABLED)
			_instance = new SMSWayToPay();
		return _instance;
	}

	public SMSWayToPay()
	{
		ThreadPoolManager.getInstance().scheduleAtFixedRate(new ConnectAndUpdate(), Config.SMS_PAYMENT_MANAGER_INTERVAL, Config.SMS_PAYMENT_MANAGER_INTERVAL);
		ThreadPoolManager.getInstance().scheduleAtFixedRate(new Clean(), Config.SMS_PAYMENT_MANAGER_INTERVAL, Config.SMS_PAYMENT_MANAGER_INTERVAL);
		ThreadPoolManager.getInstance().scheduleAtFixedRate(new GiveReward(), Config.SMS_PAYMENT_MANAGER_INTERVAL, Config.SMS_PAYMENT_MANAGER_INTERVAL);
		_log.info("SMSWayToPay: loaded sucesfully");
	}

	public void getPage(String address)
	{
		try
		{
			URL url = new URL(address);
			reader = new BufferedReader(new InputStreamReader(url.openStream(), "UTF8"));
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
	}

	public void parse()
	{
		try
		{
			String line;
			while((line = reader.readLine()) != null)
			{
				StringTokenizer st = new StringTokenizer(line, "\t. :");
				while(st.hasMoreTokens())
					try
					{
						//id	service_id	status	time	curr_id	sum	profit	email	client_id	client_params
						//st.nextToken();
						int id = Integer.parseInt(st.nextToken());
						int service_id = Integer.parseInt(st.nextToken());
						int status = Integer.parseInt(st.nextToken());
						int time = Integer.parseInt(st.nextToken());
						int curr_id = Integer.parseInt(st.nextToken());
						double sum = Integer.parseInt(st.nextToken());
						double profit = Integer.parseInt(st.nextToken());
						String email = st.nextToken();
						int client_id = Integer.parseInt(st.nextToken());
						String client_params = st.nextToken();

						_log.info("id = " + id);
						_log.info("service_id = " + service_id);
						_log.info("status = " + status);
						_log.info("time = " + time);
						_log.info("curr_id = " + curr_id);
						_log.info("sum = " + sum);
						_log.info("profit = " + profit);
						_log.info("email = " + email);
						_log.info("client_id = " + client_id);
						_log.info("client_params = " + client_params);
						
						//if(voteTime + Config.MMO_TOP_SAVE_DAYS * 86400 > System.currentTimeMillis() / 1000)
							//checkAndSave(voteTime, charName, voteType);
					}
					catch(Exception e)
					{}
			}
		}
		catch(Exception e)
		{
			_log.warning("SMSWayToPay: Cant store SMS data.");
			e.printStackTrace();
		}
	}

	public void checkAndSave(long voteTime, String charName, int voteType)
	{
		/*Connection con = null;
		PreparedStatement statement = null;
		ResultSet rset = null;
		try
		{
			con = DatabaseFactory.getInstance().getConnection();
			statement = con.prepareStatement("SELECT obj_Id FROM characters WHERE char_name=?");
			statement.setString(1, charName);
			rset = statement.executeQuery();
			int objId = 0;
			if(rset.next())
				objId = rset.getInt("obj_Id");
			if(objId > 0)
			{
				DbUtils.closeQuietly(statement, rset);
				statement = con.prepareStatement("SELECT * FROM character_mmotop_votes WHERE id=? AND date=? AND multipler=?");
				statement.setInt(1, objId);
				statement.setLong(2, voteTime);
				statement.setInt(3, voteType);
				rset = statement.executeQuery();
				if(!rset.next())
				{
					DbUtils.closeQuietly(statement, rset);
					statement = con.prepareStatement("INSERT INTO character_mmotop_votes (date, id, nick, multipler) values (?,?,?,?)");
					statement.setLong(1, voteTime);
					statement.setInt(2, objId);
					statement.setString(3, charName);
					statement.setInt(4, voteType);
					rset = statement.executeQuery();
				}
			}
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		finally
		{
			DbUtils.closeQuietly(con, statement, rset);
		}*/
	}

	private synchronized void clean()
	{
		/*Calendar calendar = Calendar.getInstance();
		calendar.add(Calendar.DAY_OF_YEAR, -Config.MMO_TOP_SAVE_DAYS);
		Connection con = null;
		PreparedStatement statement = null;
		ResultSet rset = null;
		try
		{
			con = DatabaseFactory.getInstance().getConnection();
			statement = con.prepareStatement("DELETE FROM character_mmotop_votes WHERE date<?");
			statement.setLong(1, calendar.getTimeInMillis() / 1000);
			rset = statement.executeQuery();
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		finally
		{
			DbUtils.closeQuietly(con, statement, rset);
		}*/
	}

	private synchronized void giveReward()
	{
		/*Connection con = null;
		PreparedStatement statement = null;
		ResultSet rset = null;
		try
		{
			con = DatabaseFactory.getInstance().getConnection();
			for(Player player : GameObjectsStorage.getAllPlayers())
			{
				int objId = player.getObjectId();
				int mult = 0;
				statement = con.prepareStatement("SELECT multipler FROM character_mmotop_votes WHERE id=? AND has_reward=0");
				statement.setInt(1, objId);
				rset = statement.executeQuery();

				while(rset.next())
					mult += rset.getInt("multipler");

				DbUtils.closeQuietly(statement, rset);

				if(mult > 0)
				{
					statement = con.prepareStatement("UPDATE character_mmotop_votes SET has_reward=1 WHERE id=?");
					statement.setInt(1, objId);
					statement.executeUpdate();

					if(player.getVar("lang@").equalsIgnoreCase("ru"))
						player.sendMessage("Администрация сервера " + Config.MMO_TOP_SERVER_ADDRESS + " благодарит вас за голосование в MMOTop рейтинге");
					else
						player.sendMessage("Thank you for your vote in MMOTop raiting");
					for(int i = 0; i < Config.MMO_TOP_REWARD.length; i += 2)
					{
						player.getInventory().addItem(Config.MMO_TOP_REWARD[i], Config.MMO_TOP_REWARD[i + 1] * mult);
						Log.add(player.getName() + " | " + player.getObjectId() + " | MMOTop reward item ID | " + Config.MMO_TOP_REWARD[i] + " | MMOTop reward count | " + Config.MMO_TOP_REWARD[i + 1] * mult + " |", "mmotop");
					}
				}
				DbUtils.closeQuietly(statement);
			}
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		finally
		{
			DbUtils.closeQuietly(con, statement, rset);
		}*/
	}

	private class ConnectAndUpdate implements Runnable
	{
		@Override
		public void run()
		{
			getPage(Config.SMS_PAYMENT_WEB_ADDRESS);
			parse();
		}
	}

	private class Clean implements Runnable
	{
		@Override
		public void run()
		{
			clean();
		}
	}

	private class GiveReward implements Runnable
	{
		@Override
		public void run()
		{
			giveReward();
		}
	}
}