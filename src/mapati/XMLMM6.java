package mapati;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

public class XMLMM6 
{
	private String mm6RequestXML = "";
	private String mapatiResponseXML = "";
	private String mm6Response1TAG[] = new String[8];
	private String mm6Response2TAG[] = new String[3];
	private String mm6Response3TAG[] = new String[2];
	private String mm6Response4TAG[] = new String[1];
	private Properties propXML = new Properties();
	private XMLRead xml;
	private static Logger logger = Logger.getLogger(HTTPListener.class);
	
	/*Load Configuration File XML.txt*/
	public XMLMM6()
	{
		try
		{
	        propXML.load(new FileInputStream(System.getProperty("user.dir")+"/config/properties.prop"));
			PropertyConfigurator.configure(System.getProperty("user.dir")+"/config/log4j.properties");    			
	        mm6Response1TAG=propXML.getProperty("mm6response1TAG").split(",");
	        mm6Response2TAG=propXML.getProperty("mm6response2TAG").split(",");
	        mm6Response3TAG=propXML.getProperty("mm6response3TAG").split(",");
	        mm6Response4TAG[0]=propXML.getProperty("mm6response4TAG");
		}
		catch(IOException e)
		{
			logger.error(this.getClass().getName()+" "+e.getMessage());			
		}
	}
	
	/*Extract Bank XML cash In Response*/ 
	public String[] mm6Response1(String input)
	{
		xml = new XMLRead();

		return xml.execute(input,"GetCellIDResponse",mm6Response1TAG);
		
	}
	
	public String[] mm6Response2(String input)
	{
		xml = new XMLRead();

		return xml.execute(input,"GetDeviceResponse",mm6Response2TAG);
		
	}

	public String[] mm6Response3(String input)
	{
		xml = new XMLRead();

		return xml.execute(input,"GetDeviceResponse",mm6Response3TAG);
		
	}

	public String[] mm6Response4(String input)
	{
		xml = new XMLRead();

		return xml.execute(input,"GetDeviceResponse",mm6Response4TAG);
		
	}
	
	/*Create Bank XML cash In Request*/ 
	public String mm6Request(String[] input)
	{
				
		mm6RequestXML = propXML.getProperty("mm6requestXML");
		
        mm6RequestXML = mm6RequestXML.replaceAll("_MSISDN_", input[0]);
        mm6RequestXML = mm6RequestXML.replaceAll("_TX_ID_", input[1]);

		return mm6RequestXML;
	
	}
	
	public String mapatiResponse(String[] input)
	{
				
		mapatiResponseXML = propXML.getProperty("mapatiresponseXML");
		
        mapatiResponseXML = mapatiResponseXML.replaceAll("_ResultCode_", input[0]);
        mapatiResponseXML = mapatiResponseXML.replaceAll("_ResultDesc_", input[1]);
        mapatiResponseXML = mapatiResponseXML.replaceAll("_LAC_", input[2]);
        mapatiResponseXML = mapatiResponseXML.replaceAll("_CI_", input[3]);
        mapatiResponseXML = mapatiResponseXML.replaceAll("_IMSI_", input[4]);
        mapatiResponseXML = mapatiResponseXML.replaceAll("_IMEI_", input[5]);
        mapatiResponseXML = mapatiResponseXML.replaceAll("_PROVINSI_", input[6]);
        mapatiResponseXML = mapatiResponseXML.replaceAll("_KABUPATEN_", input[7]);
        mapatiResponseXML = mapatiResponseXML.replaceAll("_KECAMATAN_", input[8]);
        mapatiResponseXML = mapatiResponseXML.replaceAll("_KELURAHAN_", input[9]);
        mapatiResponseXML = mapatiResponseXML.replaceAll("_LONGITUDE_", input[11]);
        mapatiResponseXML = mapatiResponseXML.replaceAll("_LATITUDE_", input[10]);
        mapatiResponseXML = mapatiResponseXML.replaceAll("_MSISDN_", input[12]);
        mapatiResponseXML = mapatiResponseXML.replaceAll("_AGE_", input[13]);

		return mapatiResponseXML;
	
	}

}
