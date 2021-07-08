package mapati;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.slee.ActivityContextInterface;
import javax.slee.CreateException;
import javax.slee.RolledBackContext;
import javax.slee.SbbContext;

import com.opencloud.slee.resources.cgin.CGINActivityContextInterfaceFactory;
import com.opencloud.slee.resources.cgin.CGINProvider;
import com.opencloud.slee.resources.cgin.DialogCloseEvent;
import com.opencloud.slee.resources.cgin.DialogOpenAcceptEvent;
import com.opencloud.slee.resources.cgin.DialogOpenRefuseEvent;
import com.opencloud.slee.resources.cgin.DialogProviderAbortEvent;
import com.opencloud.slee.resources.cgin.DialogUserAbortEvent;
import com.opencloud.slee.resources.cgin.OpenRefuseReason;
import com.opencloud.slee.resources.cgin.OperationErrorEvent;
import com.opencloud.slee.resources.cgin.ProtocolException;
import com.opencloud.slee.resources.cgin.SccpAddress;
import com.opencloud.slee.resources.cgin.TooManyInvokesException;
import com.opencloud.slee.resources.cgin.map.MAPAnyTimeInterrogationArg;
import com.opencloud.slee.resources.cgin.map.MAPAnyTimeInterrogationRes;
import com.opencloud.slee.resources.cgin.map.MAPAnyTimeSubscriptionInterrogationArg;
import com.opencloud.slee.resources.cgin.map.MAPAnyTimeSubscriptionInterrogationRes;
import com.opencloud.slee.resources.cgin.map.MAPDialog;
import com.opencloud.slee.resources.cgin.map.MAPLocationInformation;
import com.opencloud.slee.resources.cgin.map.MAPMAP_DialoguePDU;
import com.opencloud.slee.resources.cgin.map.MAPMAP_OpenInfo;
import com.opencloud.slee.resources.cgin.map.MAPRequestedCAMEL_SubscriptionInfo;
import com.opencloud.slee.resources.cgin.map.MAPRequestedInfo;
import com.opencloud.slee.resources.cgin.map.MAPRequestedSubscriptionInfo;
import com.opencloud.slee.resources.cgin.map.MAPSubscriberIdentity;
import com.opencloud.slee.resources.cgin.map.MAPSubscriberInfo;
import com.opencloud.slee.resources.cgin.map.events.MAPAnyTimeInterrogationResultEvent;
import com.opencloud.slee.resources.cgin.map.events.MAPAnyTimeSubscriptionInterrogationResultEvent;
import com.opencloud.slee.resources.cgin.map.metadata.MAPApplicationContexts;
import com.opencloud.slee.resources.http.HttpActivityContextInterfaceFactory;
import com.opencloud.slee.resources.http.HttpProvider;
import com.opencloud.slee.resources.http.HttpRequest;
import com.opencloud.slee.resources.http.HttpResponse;
import com.opencloud.slee.resources.http.IncomingHttpRequestActivity;
import com.opencloud.slee.resources.in.datatypes.cc.AddressString;
import com.opencloud.slee.resources.in.datatypes.cc.CellGlobalId;

public abstract class HTTPListener extends BaseSbb {

	private HttpProvider httpProvider;
	private HttpActivityContextInterfaceFactory aciFactory;
	private static Logger logger = Logger.getLogger(HTTPListener.class);
	private Properties propXML = new Properties();

	private MAPRequestedInfo atiReqInfo;
    private MAPRequestedSubscriptionInfo atsiReqInfo;
	private CGINProvider cginprovider;
	private CGINActivityContextInterfaceFactory mapaciFactory;
	
	
	private String response = "";
	private String msisdn = "";
	private String MM6Url = "";
	private String MM6Request = "";
	private XMLMM6 XML = new XMLMM6();
	private String MM6Response[] = new String[8];
	private ConnectionImpl connDb = new ConnectionImpl();
	private Object[][] results_inquiry = new Object[1][10];
	private Map <Integer, String> field_inquiry = new HashMap<Integer, String>();
	private String longitude = "Location not found";
	private String latitude = "Location not found";
	private String provinsi = "";
	private String kabupaten = "";
	private String kecamatan = "";
	private String kelurahan = "";
	private String age = "";
	private String lac = "";
	private String ci = "";
	private String mcc = "";
	private String mnc = "";
	private String imei = "";
	private String imsi = "";
	private ActivityContextInterface httpaci;

	public void setSbbContext(SbbContext ctx) {

		super.setSbbContext(ctx);
		try {
			final Context env = (Context) new InitialContext().lookup("java:comp/env");
			httpProvider = (HttpProvider) env.lookup("slee/resources/http/2.2/provider");
			aciFactory = (HttpActivityContextInterfaceFactory) env.lookup("slee/resources/http/2.2/acifactory");
			cginprovider = (CGINProvider) env.lookup("slee/resources/map/provider");
			mapaciFactory = (CGINActivityContextInterfaceFactory) env.lookup("slee/resources/map/acifactory");
	        propXML.load(new FileInputStream(System.getProperty("user.dir")+"/config/properties.prop"));
			PropertyConfigurator.configure(System.getProperty("user.dir")+"/config/log4j.properties");    			

			logger.info(HTTPListener.class.getName()+" setSbbContext");        
		} 
		catch (NamingException e) {
			logger.error(this.getClass().getName()+" "+e.getMessage());
		}
		catch (IOException e) {
			logger.error(this.getClass().getName()+" "+e.getMessage());
		}	
	}

	public void sbbPostCreate() {
		try {
			super.sbbPostCreate();

			
			MM6Url = propXML.getProperty("mm6url");			

			field_inquiry = new TreeMap<Integer, String>();
			field_inquiry.put(0, "longd");
			field_inquiry.put(1, "longm");
			field_inquiry.put(2, "longs");
			field_inquiry.put(3, "latd");
			field_inquiry.put(4, "latm");
			field_inquiry.put(5, "lats");
			field_inquiry.put(6, "longpole");
			field_inquiry.put(7, "latpole");
			field_inquiry.put(8, "kelurahan");
			field_inquiry.put(9, "provinsi");
						
		} catch (CreateException e) {
			logger.error(this.getClass().getName()+" "+e.getMessage());
		}
	}


	public void sbbRolledBack(RolledBackContext context) {}
	public void sbbExceptionThrown(Exception exception, Object event, ActivityContextInterface aci) {}

	public void onGetRequest(HttpRequest request, ActivityContextInterface aci) 
	{
		this.httpaci = aci;
		
		logger.info(this.getClass().getName()+" Received Request: [" + request.getRequestURL()+"] from : ["+request.getClientAddress().getHostAddress()+"]");
		try {
			if(request.getMethod()==HttpRequest.GET)
			{				
				String uri = request.getRequestURL().getQuery().toString();

				Map<String, String> map = getQueryMap(uri);

				if (map.containsKey("MSISDN"))
				{
					msisdn = map.get("MSISDN");

					MM6Request = XML.mm6Request(new String[]{msisdn,"1"});
					
					HttpRequest newRequest = httpProvider.createRequest(HttpRequest.POST, new URL(MM6Url));
					newRequest.setContentType("text/plain; charset=\"utf-8\"");
					newRequest.setContent(MM6Request.getBytes());
										
			        HttpResponse activity = httpProvider.sendSyncRequest(newRequest);	        
								        
					logger.info(this.getClass().getName()+" Send Request To : "+MM6Url+" ["+MM6Request+"]");
			        
			        if(activity.getStatusCode()==200)
			        {
						response = activity.getContentAsString();
						
						logger.info(this.getClass().getName()+" Get Response : ["+response+"]");
						
						if(response.contains("Error") && response.contains("TxId") && response.contains("Version"))
						{
							MM6Response = XML.mm6Response2(response);
							if(MM6Response[1].compareTo("MSISDN record not found")==0 || MM6Response[1].compareTo("value of MSISDN is too short")==0)
							{	
								response = XML.mapatiResponse(new String[]{"95",MM6Response[1],"","","","","","","","","","",msisdn,""});

								httpSendResponse(response,httpaci);
							}
//							else if(MM6Response[1].compareTo("missing one of: MSISDN or IMSI")==0)
							else
							{
								sendATI(msisdn);
//								response = XML.mapatiResponse(new String[]{"02","MM6 Error Response ["+MM6Response[1]+"]","","","","","","","","","","",msisdn,""});									
							}
						}
						else if(response.contains("Error") && response.contains("Version"))
						{
//							MM6Response = XML.mm6Response2(response);
//							response = XML.mapatiResponse(new String[]{"03","MM6 Error Response ["+MM6Response[1]+"]","","","","","","","","","","",msisdn,""});
							sendATI(msisdn);
						}
						else if(response.contains("Error") && response.contains("TxId"))
						{
//							MM6Response = XML.mm6Response3(response);
//							response = XML.mapatiResponse(new String[]{"04","MM6 Error Response ["+MM6Response[1]+"]","","","","","","","","","","",msisdn,""});
							sendATI(msisdn);
						}
						else if(response.contains("Error"))
						{
//							MM6Response = XML.mm6Response4(response);
//							response = XML.mapatiResponse(new String[]{"05","MM6 Error Response ["+MM6Response[0]+"]","","","","","","","","","","",msisdn,""});								
							sendATI(msisdn);
						}
						else if(response.trim().length()>0)
						{
							MM6Response = XML.mm6Response1(response);
							imsi=MM6Response[2];
							imei=MM6Response[4];
							sendATI(msisdn);
						}
						else
						{
							sendATI(msisdn);
						}
			        	
			        }
			        else
			        {
						response = XML.mapatiResponse(new String[]{"99","MM6 Error Response ["+activity.getStatusReason()+"]","","","","","","","","","","",msisdn,""});										        	

						httpSendResponse(response,httpaci);
			        }
					
				}
				else
				{
					response = XML.mapatiResponse(new String[]{"98","MISSING REQUIRED PARAMETER : MSISDN","","","","","","","","","","",msisdn,""});																		

					httpSendResponse(response,httpaci);
				}
				
			}


		} catch (Exception e) {
			logger.error(this.getClass().getName()+" "+e.getMessage());
		}
	}

	
	public void onHttpResponse(HttpResponse response, ActivityContextInterface aci)
	{
		info("HttpResponse");
	}
	
	
	
	public String Convert(String degree, String minute, String second, String pole)
	{
		String result = "";
		float d = 0;
		float m = 0;
		int s = 0;
		float decimal =  0;

		d = new Float(degree).floatValue();
		m = new Float(minute).floatValue();
		s = new Float(second).intValue();

		decimal = d+((m * 60)+s) / 3600;

		if(pole.compareTo("S")==0 || pole.compareTo("W")==0)	decimal=decimal*-1;

		result = new Float(decimal).toString();

		return result;
	}

	public int getLatLong(String lacci)
	{
		int result = 0;
		
		while(connDb.isConnected()==false)
		{	
			connDb.setDataSource();
		}				
						
		String longd = "";
		String longm = "";
		String longs = "";
		String latd = "";
		String latm = "";
		String lats = "";
		String longpole = "";
		String latpole = "";

		try
		{
			if(connDb.getConnection().isClosed()==true)
			{
				connDb.setDataSource();
			}
		}
		catch (SQLException e) {
			logger.error(this.getClass().getName()+" "+e.getMessage());
		}		
		
		results_inquiry=connDb.getQuery("SELECT longd, longm, longs, latd, latm, lats, longpole, latpole, kelurahan, provinsi FROM bts WHERE lacci='"+lacci+"'", new Object[]{"","","","","","","","","",""}, field_inquiry, new Object[]{},0);
		if(connDb.getRowCount(0)>0)
		{    		
			longd = results_inquiry[0][0].toString();
			longm = results_inquiry[0][1].toString();
			longs = results_inquiry[0][2].toString();
			latd = results_inquiry[0][3].toString();
			latm = results_inquiry[0][4].toString();
			lats = results_inquiry[0][5].toString();
			longpole = results_inquiry[0][6].toString();
			latpole = results_inquiry[0][7].toString();
			kelurahan = results_inquiry[0][8].toString();
			provinsi = results_inquiry[0][9].toString();

			longitude = Convert(longd, longm, longs, longpole);
			latitude = Convert(latd, latm, lats, latpole);
			result = 1;
		}        		

//		connDb.Close();
		
		return result;	
	}
	
	
	public static Map<String, String> getQueryMap(String query) 
	{
		query = query.replaceFirst("/path\\?", "");
		String[] params = query.split("&");
		Map<String, String> map = new HashMap<String, String>();
		for (String param : params) {
			String paramPair[] = param.split("=");
			String name = paramPair[0].toUpperCase();
			String value = "";				
			if(paramPair.length>1)
			{	
				value = paramPair[1];
			}	
			map.put(name, value);
		}
		return map;
	}		

	public void sendATI(String msisdn) 
	{
		try {


			SccpAddress cceSccpAddr = new SccpAddress(SccpAddress.Type.C7);
			cceSccpAddr.setRouteOnPC(false);
			cceSccpAddr.setSSN(new Integer(propXML.getProperty("mapatiSSN")).intValue());
			cceSccpAddr.setNational(false);
			cceSccpAddr.setEncodingScheme(((propXML.getProperty("mapatiGT").length()%2) == 0)? 2 : 1);//(gt.length & 1 == 0? 2 : 1)
			cceSccpAddr.setNatureOfAddress(SccpAddress.NATURE_INTERNATIONAL_NUMBER);
			cceSccpAddr.setNumberingPlan(SccpAddress.NUMBERING_PLAN_ISDN);
			cceSccpAddr.setTranslationType(0);
			cceSccpAddr.setGlobalTitleIndicator(SccpAddress.GTIndicator.GT_0100);
			cceSccpAddr.setAddress(propXML.getProperty("mapatiGT"));    	

			AddressString cceAddress = new AddressString();
			cceAddress.setAddress(propXML.getProperty("mapatiGT"));
			cceAddress.setNature(AddressString.Nature.INTERNATIONAL);
			cceAddress.setNumberingPlan(AddressString.NumberingPlan.ISDN);

			SccpAddress destAddress = new SccpAddress(SccpAddress.Type.C7);
			destAddress.setRouteOnPC(false);
			destAddress.setSSN(6);
			destAddress.setNational(false);
			destAddress.setEncodingScheme(((msisdn.length()%2) == 0)? 2 : 1);//(gt.length & 1 == 0? 2 : 1)
			destAddress.setNatureOfAddress(SccpAddress.NATURE_INTERNATIONAL_NUMBER);
			destAddress.setNumberingPlan(SccpAddress.NUMBERING_PLAN_ISDN);
			destAddress.setTranslationType(0);
			destAddress.setGlobalTitleIndicator(SccpAddress.GTIndicator.GT_0100);
			destAddress.setAddress(msisdn);    	

			atiReqInfo = new MAPRequestedInfo();
			atiReqInfo.setCurrentLocationPresent(true);
			atiReqInfo.setLocationInformationPresent(true);
			atiReqInfo.setSubscriberStatePresent(true);
//			atiReqInfo.setMs_classmarkPresent(true);
//			atiReqInfo.setMnpRequestedInfoPresent(true);
//			atiReqInfo.setT_adsDataPresent(true);

			int mapTimeout = Integer.parseInt(propXML.getProperty("mapatiTimeout"));

			final MAPMAP_DialoguePDU pdu = new MAPMAP_DialoguePDU();
			final MAPMAP_OpenInfo openInfo = new MAPMAP_OpenInfo();
			pdu.setMap_open(openInfo);
			

			MAPDialog dialog = (MAPDialog) cginprovider.issueOpenRequest(MAPApplicationContexts.anyTimeInfoEnquiryContext_v3_ac,null,cceSccpAddr, destAddress, mapTimeout, pdu);
//			MAPDialog dialog = (MAPDialog) cginprovider.issueOpenRequest(MAPApplicationContexts.anyTimeInfoEnquiryContext_v3_ac,cceSccpAddr);
			
			
			ActivityContextInterface dialogACI = mapaciFactory.getActivityContextInterface(dialog);
			dialogACI.attach(getSbbLocalObject());

			MAPSubscriberIdentity subIdentity = new MAPSubscriberIdentity();
			AddressString ms = new AddressString();
			ms.setAddress(msisdn);
			ms.setNature(AddressString.Nature.INTERNATIONAL);
			ms.setNumberingPlan(AddressString.NumberingPlan.ISDN);
			subIdentity.setMsisdn(ms);

			MAPAnyTimeInterrogationArg atiArg = new MAPAnyTimeInterrogationArg();
			atiArg.setRequestedInfo(atiReqInfo);
			atiArg.setGsmSCF_Address(cceAddress);
			atiArg.setSubscriberIdentity(subIdentity);

			dialog.sendAnyTimeInterrogation(mapTimeout, atiArg);
			dialog.sendDelimiter();

			logger.info("HLRValidation.sendATI: " + atiArg.toString());
		} 
		catch (TooManyInvokesException e) 
		{
			logger.error(this.getClass().getName()+" "+e.getMessage());
		} catch (ProtocolException e) {
			logger.error(this.getClass().getName()+" "+e.getMessage());
		}
	}
	
	public void onAtiResp(MAPAnyTimeInterrogationResultEvent event, ActivityContextInterface aci) 
	{
		logger.info("HLRValidation.onAtiResp: " + event);

		MAPLocationInformation locInfo = null;
		MAPAnyTimeInterrogationRes atiRes = event.getResult();
		if (atiRes.hasSubscriberInfo()) 
		{
			MAPSubscriberInfo subsInfo = atiRes.getSubscriberInfo();
			if(subsInfo.hasLocationInformation())
			{
				locInfo = subsInfo.getLocationInformation();
			}	
			else
			{
				response = XML.mapatiResponse(new String[]{"94","No Location Information from HLR","","",imsi,imei,"","","","",longitude,latitude,msisdn,""});																										

				httpSendResponse(response,httpaci);

				aci.detach(getSbbLocalObject());

				return;
				
			}
		} 
		else 
		{
			response = XML.mapatiResponse(new String[]{"96","No Subscriber Information from HLR","","",imsi,imei,"","","","",longitude,latitude,msisdn,""});																										

			httpSendResponse(response,httpaci);

			aci.detach(getSbbLocalObject());
			
			return;
		}



		if (locInfo.hasCellGlobalIdOrServiceAreaIdOrLAI()) 
		{ // Validate location based on MCC/MNC

			CellGlobalId cid = locInfo.getCellGlobalIdOrServiceAreaIdOrLAI().getCellGlobalIdOrServiceAreaIdFixedLength();
			if(cid==null)	
			{	
				/*if(locInfo.hasVlr_number())
				{	
					String vlr_number = locInfo.getVlr_number().getAddress();

					logger.info("VLR Number : "+vlr_number);
					
					if(vlr_number.startsWith("62"))
					{
						if(locInfo.getCellGlobalIdOrServiceAreaIdOrLAI().isLaiFixedLengthChosen())
						{
							String mcc = locInfo.getCellGlobalIdOrServiceAreaIdOrLAI().getLaiFixedLength().getMobileCountryCode();
							String mnc = locInfo.getCellGlobalIdOrServiceAreaIdOrLAI().getLaiFixedLength().getMobileNetworkCode();
							String lac = new Integer(locInfo.getCellGlobalIdOrServiceAreaIdOrLAI().getLaiFixedLength().getLocationAreaCode()).toString();

							response = XML.mapatiResponse(new String[]{"0","Operation is Successfull",lac,"",imsi,imei,"","","","","","",msisdn,getAge()});						
						}
						else
						{
							response = XML.mapatiResponse(new String[]{"98","No CI Response from HLR","","",imsi,imei,"","","","","","",msisdn,getAge()});																
						}
					}
					else
					{
						response = XML.mapatiResponse(new String[]{"0","VLR Number : "+vlr_number,"","",imsi,imei,"","","","","","",msisdn,getAge()});						
					}
				}			
				else if(locInfo.hasMsc_Number())
				{	
					String msc_number = locInfo.getMsc_Number().getAddress();
					
					logger.info("MSC Number : "+msc_number);
					
					if(msc_number.startsWith("62"))
					{
						if(locInfo.getCellGlobalIdOrServiceAreaIdOrLAI().isLaiFixedLengthChosen())
						{
							String mcc = locInfo.getCellGlobalIdOrServiceAreaIdOrLAI().getLaiFixedLength().getMobileCountryCode();
							String mnc = locInfo.getCellGlobalIdOrServiceAreaIdOrLAI().getLaiFixedLength().getMobileNetworkCode();
							String lac = new Integer(locInfo.getCellGlobalIdOrServiceAreaIdOrLAI().getLaiFixedLength().getLocationAreaCode()).toString();

							response = XML.mapatiResponse(new String[]{"0","Operation is Successfull",lac,"",imsi,imei,"","","","","","",msisdn,getAge()});						
						}
						else
						{
							response = XML.mapatiResponse(new String[]{"98","No CI Response from HLR","","",imsi,imei,"","","","","","",msisdn,getAge()});																
						}
					}
					else
					{
						response = XML.mapatiResponse(new String[]{"0","MSC Number : "+msc_number,"","",imsi,imei,"","","","","","",msisdn,getAge()});						
					}
				}
				else
				{
					response = XML.mapatiResponse(new String[]{"98","No CI Response from HLR","","",imsi,imei,"","","","","","",msisdn,getAge()});																					
				}*/
				response = XML.mapatiResponse(new String[]{"98","No CI Response from HLR","","",imsi,imei,"","","","","","",msisdn,getAge()});																					
			}
			else
			{	
				setMcc(cid.getMobileCountryCode());
				setMnc(cid.getMobileNetworkCode());					
				setLac(new Integer(cid.getLocationAreaCode()).toString());
				setCi(new Integer(cid.getCellId()).toString());
				if(locInfo.hasAgeOfLocationInformation())
				{
					setAge(new Integer(atiRes.getSubscriberInfo().getLocationInformation().getAgeOfLocationInformation()).toString());
				}
	
				if(getLatLong(getMcc()+"-"+getMnc()+"-"+getLac()+"-"+getCi())==1)
				{
					response = XML.mapatiResponse(new String[]{"0","Operation is Successfull",getLac(),getCi(),imsi,imei,provinsi,kabupaten,kecamatan,kelurahan,longitude,latitude,msisdn,getAge()});
				}
				else
				{
					if(cid.getMobileCountryCode().compareTo("510")==0 && cid.getMobileNetworkCode().compareTo("11")==0)
					{	
						response = XML.mapatiResponse(new String[]{"97","No Data in Database",getLac(),getCi(),imsi,imei,"","","","","","",msisdn,getAge()});									
					}
					else
					{
						response = XML.mapatiResponse(new String[]{"97","No Data in Database",getLac(),getCi(),imsi,imei,"","","","","","",msisdn,getAge()});									
					}
				}
			}	

			httpSendResponse(response,httpaci);

		}
		/*else if(locInfo.hasVlr_number())
		{
			String vlr_number = locInfo.getVlr_number().getAddress();

			logger.info("VLR Number : "+vlr_number);
			
			if(vlr_number.startsWith("62"))
			{
				response = XML.mapatiResponse(new String[]{"98","No CI Response from HLR","","",imsi,imei,"","","","","","",msisdn,getAge()});																
			}
			else
			{
				response = XML.mapatiResponse(new String[]{"0","VLR Number : "+vlr_number,"","",imsi,imei,"","","","","","",msisdn,getAge()});						
			}			
			httpSendResponse(response,httpaci);			 
		}
		else if(locInfo.hasMsc_Number())
		{
			String msc_number = locInfo.getMsc_Number().getAddress();
			
			logger.info("MSC Number : "+msc_number);
			
			if(msc_number.startsWith("62"))
			{
				response = XML.mapatiResponse(new String[]{"98","No CI Response from HLR","","",imsi,imei,"","","","","","",msisdn,getAge()});																
			}
			else
			{
				response = XML.mapatiResponse(new String[]{"0","MSC Number : "+msc_number,"","",imsi,imei,"","","","","","",msisdn,getAge()});						
			}
			httpSendResponse(response,httpaci);			 			
		}*/
		else
		{
			response = XML.mapatiResponse(new String[]{"98","No CI Response from HLR","","",imsi,imei,"","","","","","",msisdn,getAge()});									
			httpSendResponse(response,httpaci);			 
		}

		aci.detach(getSbbLocalObject());

	}

	/*private void sendATSI(String msisdn) {
        try {

			SccpAddress cceSccpAddr = new SccpAddress(SccpAddress.Type.C7);
			cceSccpAddr.setRouteOnPC(false);
			cceSccpAddr.setSSN(new Integer(propXML.getProperty("mapatiSSN")).intValue());
			cceSccpAddr.setNational(false);
			cceSccpAddr.setEncodingScheme(((propXML.getProperty("mapatiGT").length()%2) == 0)? 2 : 1);//(gt.length & 1 == 0? 2 : 1)
			cceSccpAddr.setNatureOfAddress(SccpAddress.NATURE_INTERNATIONAL_NUMBER);
			cceSccpAddr.setNumberingPlan(SccpAddress.NUMBERING_PLAN_ISDN);
			cceSccpAddr.setTranslationType(0);
			cceSccpAddr.setGlobalTitleIndicator(SccpAddress.GTIndicator.GT_0100);
			cceSccpAddr.setAddress(propXML.getProperty("mapatiGT"));    	

			AddressString cceAddress = new AddressString();
			cceAddress.setAddress(propXML.getProperty("mapatiGT"));
			cceAddress.setNature(AddressString.Nature.INTERNATIONAL);
			cceAddress.setNumberingPlan(AddressString.NumberingPlan.ISDN);
        	
        	SccpAddress destAddress = new SccpAddress(SccpAddress.Type.C7);
			destAddress.setRouteOnPC(false);
			destAddress.setSSN(6);
			destAddress.setNational(false);
			destAddress.setEncodingScheme(((msisdn.length()%2) == 0)? 2 : 1);//(gt.length & 1 == 0? 2 : 1)
			destAddress.setNatureOfAddress(SccpAddress.NATURE_INTERNATIONAL_NUMBER);
			destAddress.setNumberingPlan(SccpAddress.NUMBERING_PLAN_ISDN);
			destAddress.setTranslationType(0);
			destAddress.setGlobalTitleIndicator(SccpAddress.GTIndicator.GT_0100);
			destAddress.setAddress(msisdn);    	

	        atsiReqInfo = new MAPRequestedSubscriptionInfo();
	        atsiReqInfo.setRequestedCAMEL_SubscriptionInfo(MAPRequestedCAMEL_SubscriptionInfo.o_CSI);
	        atsiReqInfo.setOdbPresent(true);
			
			
			int mapTimeout = Integer.parseInt(propXML.getProperty("mapatiTimeout"));

			final MAPMAP_DialoguePDU pdu = new MAPMAP_DialoguePDU();
            final MAPMAP_OpenInfo openInfo = new MAPMAP_OpenInfo();
            pdu.setMap_open(openInfo);

			MAPDialog dialog = (MAPDialog) cginprovider.issueOpenRequest(MAPApplicationContexts.anyTimeInfoEnquiryContext_v3_ac,cceSccpAddr, destAddress, mapTimeout, pdu);

			ActivityContextInterface dialogACI = mapaciFactory.getActivityContextInterface(dialog);

            
            dialogACI.attach(getSbbLocalObject());

            MAPAnyTimeSubscriptionInterrogationArg atsiarg = new MAPAnyTimeSubscriptionInterrogationArg();
            atsiarg.setRequestedSubscriptionInfo(atsiReqInfo);
            atsiarg.setGsmSCF_Address(cceAddress);

            MAPSubscriberIdentity subIdentity = new MAPSubscriberIdentity();
            AddressString ms = new AddressString();
            ms.setAddress(msisdn);
            ms.setNature(AddressString.Nature.INTERNATIONAL);
            ms.setNumberingPlan(AddressString.NumberingPlan.ISDN);
            subIdentity.setMsisdn(ms);
            atsiarg.setSubscriberIdentity(subIdentity);

            dialog.sendAnyTimeSubscriptionInterrogation(mapTimeout, atsiarg);
            dialog.sendDelimiter();

			logger.info("HLRValidation.sendATI: " + atsiarg.toString());

        } catch (TooManyInvokesException e) {

			logger.error(this.getClass().getName()+" "+e.getMessage());
        } catch (ProtocolException e) {

			logger.error(this.getClass().getName()+" "+e.getMessage());
        }

    }	
	
	public void onAtsiResp(MAPAnyTimeSubscriptionInterrogationResultEvent event, ActivityContextInterface aci) 
	{	
		logger.info("HLRValidation.onAtsiResp: " + event);
		
		aci.detach(getSbbLocalObject());
	}*/
	
	public void onOpenConf(DialogOpenAcceptEvent event, ActivityContextInterface aci) 
	{
		logger.info("HLRValidation.onOpenConf: " + event);

	}

	public void onOpenRefuse(DialogOpenRefuseEvent event, ActivityContextInterface aci) 
	{
		logger.info("HLRValidation.onOpenRefuse: " + event);

		MAPDialog dialog = (MAPDialog) aci.getActivity();
		if (event.getRefuseReason() == OpenRefuseReason.TIMEOUT) 
		{
			if (dialog.getApplicationContext().equals(MAPApplicationContexts.anyTimeInfoEnquiryContext_v3_ac)) 
			{
				sendATI(msisdn);
			} 
			else 
			{
				response = XML.mapatiResponse(new String[]{"96","Response Error from HLR : "+event.getRefuseReason(),"","",imsi,imei,"","","","",longitude,latitude,msisdn,""});																										
				
				httpSendResponse(response,httpaci);
			}
		} 
		else
		{
			response = XML.mapatiResponse(new String[]{"96","Response Error from HLR "+event.getRefuseReason(),"","",imsi,imei,"","","","",longitude,latitude,msisdn,""});																										
			
			httpSendResponse(response,httpaci);
		}

		aci.detach(getSbbLocalObject());

	}
	
	public void httpSendResponse(String response, ActivityContextInterface aci)
	{
		IncomingHttpRequestActivity activity = 
		(IncomingHttpRequestActivity) aci.getActivity();

		HttpResponse responses = activity.createResponse(200, "OK");
		try
		{
			
			StringBuffer content = new StringBuffer(response);
			responses.setContentAsString("text/html; charset=\"utf-8\"", content.toString());
			
			logger.info("onGetRequest: Sending Response: " + response);
			activity.sendResponse(responses);
		}
		catch(IOException e)
		{
			logger.error(this.getClass().getName()+" "+e.getMessage());			
		}
		
	}

	public void onOperationError(OperationErrorEvent error, ActivityContextInterface aci) 
	{
		logger.error("HLRValidation.onOperationError: " + error);

		response = XML.mapatiResponse(new String[]{"96","Response Error from HLR : "+error.getName(),"","",imsi,imei,"","","","","","",msisdn,""});																										
		
		httpSendResponse(response,httpaci);
	
		aci.detach(getSbbLocalObject());

	}

	public void onProviderAbort(DialogProviderAbortEvent event, ActivityContextInterface aci)
	{
		logger.error("HLRValidation.onProviderAbort: event = "+ event);    	

		response = XML.mapatiResponse(new String[]{"96","Response Error from HLR : "+event.getName(),"","",imsi,imei,"","","","",longitude,latitude,msisdn,""});																										
		
		httpSendResponse(response,httpaci);
	
		aci.detach(getSbbLocalObject());

	}

	public void onClose(DialogCloseEvent event, ActivityContextInterface aci) 
	{
		logger.info("HLRValidation.onClose: event="+ event);

		aci.detach(getSbbLocalObject());
	}

	public void onUserAbort(DialogUserAbortEvent event, ActivityContextInterface aci) 
	{
		logger.info("HLRValidation.onUserAbort: event=" + event);

		response = XML.mapatiResponse(new String[]{"96","Response Error from HLR : "+event.getName(),"","",imsi,imei,"","","","",longitude,latitude,msisdn,""});																										
		
		httpSendResponse(response,httpaci);
	
		aci.detach(getSbbLocalObject());

	}
	
    public String getLac()
    {
    	return lac;
    }
    
    public String getCi()
    {
    	return ci;
    }

    public String getMnc()
    {
    	return mnc;
    }

    public String getMcc()
    {
    	return mcc;
    }

    public String getAge()
    {
    	return age;
    }


    public void setLac(String lac)
    {
    	this.lac = lac;
    }
    
    public void setCi(String ci)
    {
    	this.ci = ci;
    }

    public void setAge(String age)
    {
    	this.age = age;
    }

    public void setMnc(String mnc)
    {
    	this.mnc = mnc;
    }

    public void setMcc(String mcc)
    {
    	this.mcc = mcc;
    }

}
