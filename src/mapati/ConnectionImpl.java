package mapati;

import java.util.*;
import java.sql.*;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.ConnectionPoolDataSource ;
import javax.sql.DataSource;
import javax.sql.PooledConnection;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

public class ConnectionImpl {
	
	private String url = "";
	private String userName = "";
	private String password = "";
	private String className = "";
	private Connection conn = null;
//	private PooledConnection pconn = null;
	private DataSource ds = null;
//	private ConnectionPoolDataSource cpds = null;
	private String database = "";
	private String hostName = "";
	private String port = "";
	private int engine = 0;
	private int isUrl =0;
	private Properties prop = new Properties();
	private int[] rowCount = new int[100];
	private int fail = 0;
	private boolean isConnected = false;
	private Logger logger = Logger.getLogger(ConnectionImpl.class);
	
	public ConnectionImpl()
	{
		PropertyConfigurator.configure(System.getProperty("user.dir")+"/config/log4j.properties");    			
	}
	
	public void setDataSource()
	{
		try
		{
			Context env = (Context) new InitialContext();		
			ds = (DataSource) env.lookup("java:resource/jdbc/MysqlDB");
			conn = ds.getConnection();
			
			isConnected = true;
			logger.info("Database Connected");				
		}	
		catch(NamingException e)
		{
			fail = 1;
			isConnected = false;
			logger.error(this.getClass().getName()+" "+e.getMessage());
		}
		catch(SQLException e)
		{
			fail = 1;
			isConnected = false;
			logger.error(this.getClass().getName()+" "+e.getMessage());
		}
	}
	
	public void setConnection(String className, String userName, String password)
	{
		try
		{
			this.className=className;
			this.userName=userName;
			this.password=password;

			Class.forName(className).newInstance();
			if(this.isUrl>0)
			{	
				logger.error(this.url);
				conn = DriverManager.getConnection(this.url);					
		        isConnected = true;
				this.conn.setAutoCommit(true);
				logger.info("Database Connected");	
			}
			else
			{
				fail = 1;
				isConnected = false;
				logger.warn(this.getClass().getName()+" "+"Please set your URL first");
			}
		}
		catch(ClassNotFoundException e)
		{
			fail = 1;
			isConnected = false;
			logger.error(this.getClass().getName()+" "+e.getMessage());
		}
		catch(IllegalAccessException e)
		{
			fail = 1;
			isConnected = false;
			logger.error(this.getClass().getName()+" "+e.getMessage());
		}
		catch(InstantiationException e)
		{
			fail = 1;
			isConnected = false;
			logger.error(this.getClass().getName()+" "+e.getMessage());
		}
		catch(SQLException e)
		{
			fail = 1;
			isConnected = false;
			logger.error(this.getClass().getName()+" "+e.getMessage());
		}
	}
	
	public void setConnection()
	{
		try
		{
			this.className=this.prop.getProperty("db.className").trim();
			this.userName=this.prop.getProperty("db.userName").trim();
			this.password=this.prop.getProperty("db.password").trim();
						
			Class.forName(className).newInstance();
			if(this.isUrl>0)
			{	
				this.conn = DriverManager.getConnection (this.url,this.userName,this.password);
				this.conn.setAutoCommit(true);
				isConnected = true;
				logger.info("Database Connected");				
			}
			else
			{
				fail = 1;
				isConnected = false;
				logger.warn(this.getClass().getName()+" "+"Please set your URL first");
			}
		}
		catch(ClassNotFoundException e)
		{
			fail = 1;
			isConnected = false;
			logger.error(this.getClass().getName()+" "+e.getMessage());
		}
		catch(IllegalAccessException e)
		{
			fail = 1;
			isConnected = false;
			logger.error(this.getClass().getName()+" "+e.getMessage());
		}
		catch(InstantiationException e)
		{
			fail = 1;
			isConnected = false;
			logger.error(this.getClass().getName()+" "+e.getMessage());
		}
		catch(SQLException e)
		{					
			fail = 1;
			isConnected = false;
			logger.error(this.getClass().getName()+" "+e.getMessage());
		}
	}

	public int getEngine()
	{
		return this.engine;
	}

	public String getDatabase()
	{
		return this.database;
	}

	public String getHostName()
	{
		return this.hostName;
	}

	public String getPort()
	{
		return this.port;
	}

	public String getClassName()
	{
		return this.className;
	}
		
	public String getUserName()
	{
		return this.userName;
	}
	
	public String getPassword()
	{
		return this.password;
	}
	
	public String getUrl()
	{
		return this.url;
	}

	public int getRowCount(int thread)
	{
		return this.rowCount[thread];
	}
	
	public Properties getProperties()
	{
		return this.prop;
	}

	public Connection getConnection()
	{
		return this.conn;		
	}

	public Object[][] getQuery(String query, Object[] output, Map<Integer, String> field, Object[] values, int thread)
	{
		Object[][] querie = new Object[1000][50]; 
		
		int j=0;
		try
		{
			PreparedStatement prestmt = null;
			ResultSet rs = null;
			prestmt = this.conn.prepareStatement(query);
//			prestmt = this.pconn.getConnection().prepareStatement(query);

			for(int i=1;i<=values.length;i++)
			{	
				
				if(values[i-1].getClass().getName().compareTo("java.lang.Integer")==0)
				{
					prestmt.setInt(i, new Integer(values[i-1].toString()));
				}
				else if(values[i-1].getClass().getName().compareTo("java.lang.String")==0)
				{
					prestmt.setString(i, values[i-1].toString());					
				}
				else if(values[i-1].getClass().getName().compareTo("java.lang.Boolean")==0)
				{
					prestmt.setBoolean(i, new Boolean(values[i-1].toString()));					
				}
			}
			

			rs = prestmt.executeQuery();
			while (rs.next())
			{
				for(int i=0;i<field.size();i++)
				{
					if(output[i].getClass().getName().compareTo("java.lang.Integer")==0)
					{	
						querie[j][i]=rs.getInt(field.get(i).toString());
					}
					else if(output[i].getClass().getName().compareTo("java.lang.String")==0)
					{
						querie[j][i]=rs.getString(field.get(i).toString());						
					}
					else if(output[i].getClass().getName().compareTo("java.lang.Boolean")==0)
					{
						querie[j][i]=rs.getBoolean(field.get(i).toString());						
					}
				}
				j++;
			}
			if(j>0)	logger.info("[Get "+j+" Rows] from Query ["+query+"]");								
			rs.close();	
			prestmt.close();
		}
		catch(SQLException e)
		{
			if(e.getMessage().compareTo("Server shutdown in progress")==0)	fail = 1;
			logger.error(this.getClass().getName()+" "+e.getMessage()+" from ["+query+"]");
		}
		this.rowCount[thread]=j;	
		return querie;
	}

	public void setProperties(Properties prop)
	{
		this.prop=prop;
	}

	public void setUrl(int engine, String hostName, String port, String database)
	{
		this.database=database.trim();
		this.port=port.trim();
		this.hostName=hostName.trim();
		this.engine=engine;
		this.isUrl=0;

		if(this.hostName.compareTo("")==0)	this.hostName="localhost";
		if(this.engine==1)
		{
			isUrl=1;
			if(port.compareTo("")==0)
			{
				this.url="jdbc:mysql://"+hostName+"/"+database+"?user="+userName+"&password="+password+"&dontTrackOpenResources=true";				
			}
			else
			{	
				this.url="jdbc:mysql://"+hostName+":"+port+"/"+database+"?user="+userName+"&password="+password+"&dontTrackOpenResources=true";
			}	
		}
		else if(this.engine==2)
		{
			isUrl=1;
			if(port.compareTo("")==0)
			{
				this.url="jdbc:sqlserver://"+hostName+";DatabaseName="+this.database+";user="+this.userName+";password="+this.password;				
			}
			else
			{	
				this.url="jdbc:sqlserver://"+hostName+":"+port+";DatabaseName="+this.database+";user="+this.userName+";password="+this.password;
			}	
		}
		else if(this.engine==3)
		{
			isUrl=1;
			if(port.compareTo("")==0)
			{
				this.url="jdbc:oracle:thin:@"+hostName+":"+database;				
			}
			else
			{	
				this.url="jdbc:oracle:thin:@"+hostName+":"+port+":"+database;
			}	
		}
	}
	
	public void setUrl()
	{
		this.database=prop.getProperty("db.database").trim();
		this.port=prop.getProperty("db.port").trim();
		this.hostName=prop.getProperty("db.hostName").trim();
		this.engine=new Integer(prop.getProperty("db.engine").trim());
		this.isUrl=0;
		this.userName=this.prop.getProperty("db.userName").trim();
		this.password=this.prop.getProperty("db.password").trim();
		
		if(this.hostName.compareTo("")==0)	
			this.hostName="localhost";
		if(this.engine==1)
		{
			isUrl=1;
			if(port.compareTo("")==0)
			{
				this.url="jdbc:mysql://"+hostName+"/"+database+"?user="+userName+"&password="+password+"&dontTrackOpenResources=true";				
			}
			else
			{	
				this.url="jdbc:mysql://"+hostName+":"+port+"/"+database+"?user="+userName+"&password="+password+"&dontTrackOpenResources=true";
			}	
		}
		else if(this.engine==2)
		{
			isUrl=1;
			if(port.compareTo("")==0)
			{
				this.url="jdbc:sqlserver://"+hostName+";databaseName="+this.database+";user="+this.userName+";password="+this.password;				
			}
			else
			{	
				this.url="jdbc:sqlserver://"+hostName+":"+port+";databaseName="+this.database+";user="+this.userName+";password="+this.password;
			}	
		}
		else if(this.engine==3)
		{
			
			isUrl=1;
			if(port.compareTo("")==0)
			{
				this.url="jdbc:oracle:thin:@"+hostName+":"+database;				
			}
			else
			{	
				this.url="jdbc:oracle:thin:@"+hostName+":"+port+":"+database;
			}	
		}
	}

	public void updateQuery(String query, Object[] values)
	{
		int j=0;
		try
		{
			PreparedStatement prestmt = null;

			prestmt = this.conn.prepareStatement(query);
//			prestmt = this.pconn.getConnection().prepareStatement(query);

			for(int i=1;i<=values.length;i++)
			{	
				if(values[i-1].getClass().getName().compareTo("java.lang.Integer")==0)
				{
					prestmt.setInt(i, new Integer(values[i-1].toString()));
				}
				else if(values[i-1].getClass().getName().compareTo("java.lang.String")==0)
				{
					prestmt.setString(i, values[i-1].toString());					
				}
				else if(values[i-1].getClass().getName().compareTo("java.lang.Boolean")==0)
				{
					prestmt.setBoolean(i, new Boolean(values[i-1].toString()));					
				}
			}	
			j = prestmt.executeUpdate();
			logger.info("["+j+" Rows Were Effected] from Query ["+query+"]");				
			prestmt.close();
		}
		catch(SQLException e)
		{					
			if(e.getMessage().compareTo("Server shutdown in progress")==0)	fail = 1;
			logger.error(this.getClass().getName()+" "+e.getMessage()+" from ["+query+"]");
		}
		//this.rowCount=j;
	}
		
	public void Close()
	{
		try
		{
			isConnected=false;
			conn.close();
//			pconn.close();
		}
		catch(SQLException e)
		{		
			logger.error(this.getClass().getName()+" "+e.getMessage());
		}
	}
	
	public int getFail()
	{
		return this.fail;
	}

	public void setFail(int fail)
	{
		this.fail = fail;
	}

	public boolean isConnected() 
	{
		return this.isConnected;
	}	
}
