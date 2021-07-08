package mapati;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;

import org.apache.log4j.Logger;

public class loadData {

	private Logger logger = Logger.getLogger(loadData.class);
	private static Properties propXML = new Properties();
	private ConnectionImpl connDb = new ConnectionImpl();

	public loadData()
	{
		try 
		{
			propXML.load(new FileInputStream("conf/properties.prop"));
		} 
		catch (FileNotFoundException e) 
		{
			logger.error(this.getClass().getName()+" "+e.getMessage());
		} 
		catch (IOException e) 
		{
			logger.error(this.getClass().getName()+" "+e.getMessage());
		}

		while(connDb.isConnected()==false)
		{	
			connDb.setProperties(propXML);
			connDb.setUrl();
			connDb.setConnection();
		}
		
		
	}
	
	public void insertData()
	{

		logger.info("Truncate Old Data Start");
		connDb.updateQuery("truncate table bts",new Object[]{});
		logger.info("Truncate Old Data Finish");

		BufferedReader br = null;

		try {
 
			String sCurrentLine;
			
			br = new BufferedReader(new FileReader("data/"+propXML.getProperty("gisfilename")));
			logger.info("Insert Data Start");
 			
			while ((sCurrentLine = br.readLine()) != null) 
			{
				String[] column = sCurrentLine.split("\\|");
				if(column[7].compareTo("null")==0)
				{
					column[7]="S";
				}
				if(column[11].compareTo("null")==0)
				{
					column[11]="E";
				}
				connDb.updateQuery("insert into bts (bts_name, lacci, longd, longm, longs, longpole, latd, latm, lats, latpole, kelurahan, provinsi) values (?,?,?,?,?,?,?,?,?,?,?,?)",new Object[]{column[2],column[3],column[4],column[5],column[6],column[7],column[8],column[9],column[10],column[11],column[0],column[13]});
			}
 
			logger.info("Insert Data Finish");
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				if (br != null)br.close();
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}

	}
	
	public static void main(String[] args) 
	{
		new loadData().insertData();
	}

}
