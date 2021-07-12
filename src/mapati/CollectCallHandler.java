package mapati;

import com.opencloud.slee.resources.cgin.ProtocolException;
import com.opencloud.slee.resources.http.*;


import com.opencloud.slee.resources.cgin.map.MAPUSSD_Arg;
import com.opencloud.slee.resources.cgin.map.MAPUSSD_Res;
import com.opencloud.slee.resources.cgin.map.events.MAPProcessUnstructuredSS_RequestRequestEvent;
import com.opencloud.slee.resources.in.datatypes.sms.GSM7BitAlphabet;
import com.opencloud.slee.resources.in.datatypes.sms.GSM7BitPacking;
import com.opencloud.slee.resources.in.datatypes.sms.GSM7PACKEDStringCodec;
import java.text.SimpleDateFormat;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.*;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.slee.ActivityContextInterface;
import javax.slee.ChildRelation;
import javax.slee.CreateException;
import javax.slee.SbbContext;
import javax.slee.serviceactivity.ServiceStartedEvent;
import javax.sql.DataSource;

import java.sql.*;
import java.util.*;

public abstract class CollectCallHandler extends BaseSbb {

    private IncomingHttpRequestActivity activity;
    private SSPdbConfig dbConfig;
    private SbbContext sbbContext;
    private DataSource ds;
    private HttpProvider httpProvider;
    HttpActivityContextInterfaceFactory acif;
    private Pattern msisdnPattern;
    private SimpleDateFormat sdf = new SimpleDateFormat("yyMMdd");
    private TransactionLogger tLogger;

    public void setSbbContext(SbbContext sbbContext) {
        super.setSbbContext(sbbContext);
        try {
            this.sbbContext = sbbContext;
            Context env = (Context) new InitialContext().lookup("java:comp/env");
            ds = (DataSource) env.lookup("jdbc/OracleDB");

        } catch (Exception e) {
            System.out.println("[CCE] CollecCallHandler: Could not set SBB context: ");
            e.printStackTrace();
        }
    }

    protected String getTraceType() {
        return "Call Handler";

    }

    /**
     * Handles HTTP GET request
     */
    public void onGetRequest(HttpRequest request, ActivityContextInterface aci) {

        System.out.println("[CCE] CollecCallHandler.onGetRequest: " + request.getRequestURL());

        try {
            activity = (IncomingHttpRequestActivity) aci.getActivity();

            String query = request.getRequestURL().getQuery();
            if (query != null) {

                Map<String, String> map = getQueryMap(query);
                String requesterMsisdn = map.get("MSISDN");
                String ussdCommand = map.get("COMMAND").replace("%23", "");
                String ussdResponse = processUssdCommand(requesterMsisdn, ussdCommand);

                ussdResponse = "<?xml version='1.0'?><umb><type>Content</type><first>yes</first><data>" + ussdResponse + "</data><back_code>1</back_code></umb>";
                httpResponder(ussdResponse);
            }
        } catch (Exception e) {
            System.out.println("[CCE] CollecCallHandler.onGetRequest: unable to complete request");
            e.printStackTrace();
        }
    }

    /* Handles MAP USSD request */
    public void onProcessUnstructuredSS_Request(MAPProcessUnstructuredSS_RequestRequestEvent event, ActivityContextInterface aci) {

        MAPUSSD_Arg ussdArg = event.getArgument();
        int invokeId = event.getInvokeId();
        String msisdn = ussdArg.getMsisdn().getAddress();
        String ussdCommand = gsm7BitUnpack(ussdArg.getUssd_String()).replace("#", "");

        System.out.println("[CCE] CollecCallHandler.onProcessUSSReq: msisdn=" + msisdn +
                "; ussdCommand=" + ussdCommand + "; invokeId=" + invokeId);

        String ussdResponse = processUssdCommand(msisdn, ussdCommand);

        MAPUSSD_Res ussdRes = new MAPUSSD_Res();
        ussdRes.setUssd_DataCodingScheme(ussdArg.getUssd_DataCodingScheme());
        ussdRes.setUssd_String(gsm7BitPack(ussdResponse));
        try {
            event.getDialog().sendProcessUnstructuredSS_RequestResponse(invokeId, ussdRes);
        } catch (ProtocolException ex) {
             System.out.println("[CCE] CollecCallHandler.onProcessUSSReq: send response fail");
             ex.printStackTrace();
        }

    }

    public String gsm7BitUnpack(byte[] src) {

        int unpackedSize = GSM7BitPacking.getUnpackedUSSDSize(src, 0, src.length);
        byte[] unpackedOctets = new byte[unpackedSize];

        GSM7BitPacking.unpackUSSD(src, 0, unpackedOctets, 0, unpackedSize);
        return GSM7BitAlphabet.decode(unpackedOctets, 0, unpackedSize);
    }

    public byte[] gsm7BitPack(String str) {
        int unpackedSize = GSM7BitAlphabet.countCharacters(str);
        // some input characters take up more than one output character due to escaping to the extended character set
        byte[] unpackedOctets = new byte[unpackedSize];
        GSM7BitAlphabet.encode(str, unpackedOctets, 0);

        byte[] packedOctets = new byte[GSM7BitPacking.getPackedUSSDSize(unpackedOctets, 0, unpackedSize)];
        GSM7BitPacking.packUSSD(unpackedOctets, 0, packedOctets, 0, unpackedSize);
        return packedOctets;
    }


    /* Send SMS via HTTP */
    public String processUssdCommand(String requesterMsisdn, String ussdCommand) {


        if (ussdCommand == null || !ussdCommand.startsWith("*809")) {
            System.out.println("[CCE] CollecCallHandler.processUssdCommand: Non-CC command: " + ussdCommand);
            return getLocalizedMessage("SMS.errorMSISDNFormat", false, ussdCommand);
        }
        System.out.println("[CCE] CollecCallHandler.processUssdCommand: CC command: " + ussdCommand);

        ussdCommand = ussdCommand.substring(1);
        String x[] = ussdCommand.split("\\*");

        String smsResponse = "";
        String ussdResponse = "";
        boolean englishInd = isEnglishSubscriber(requesterMsisdn);

        if (x.length == 1) { // help query
            ussdResponse = getLocalizedMessage("SMS.helpMessage", englishInd, "");

        } else if (isValidMsisdn(x[1])) {    // CC request
            System.out.println("[CCE] CollecCallHandler.processUssdCommand: CC request - req: " + requesterMsisdn + "; dest:  " + x[1]);
            String destMsisdn = x[1];
            if (destMsisdn.startsWith("0")) {
                destMsisdn = "62" + destMsisdn.substring(1);
            }

            // Create Collect Call Profile
            CollectCallProfile ccProfile = new CollectCallProfile(requesterMsisdn, destMsisdn);
            ccProfile.setEnglishRequester(englishInd);
            ccProfile.setGmscGt(dbConfig.get("CC.DEFAULT_GMSC"));
            tLogger.createLog(ccProfile);
            int status = validateCCRequest(ccProfile);
            System.out.println("[CCE] CollecCallHandler.processUssdCommand: result status code = " + status);


            if (status == CCEStatus.UNKNOWN_STATUS) {     // Continue processing
                ussdResponse = getLocalizedMessage("USSD.response", englishInd, destMsisdn);
                System.out.println("[CCE] CollecCallHandler.processUssdCommand: basic validation passed");

                //Call HLRValidation
                //getMapcceClient().startValidation(ccProfile);

                //getHttpcceClient().getBalance("123456789012345678", ccProfile.getPayerMsisdn());
                //getHttpcceClient().charge("123456789012345678", ccProfile.getPayerMsisdn());
                if (ccProfile.isScbPayer()) {
                    // temporary: call OnlineCallFlow
                    getCapcceClient().initCollectCall(ccProfile);
                } else {
                    // temporary: call OfflineCallFlow
                }

            } else {
                System.out.println("[CCE] CollecCallHandler.processUssdCommand: basic validation failed");
                tLogger.updateStatus(ccProfile.getTransId(), status, "Basic validation failed");
                if (status == CCEStatus.DAILY_REQUEST_LIMIT_EXCEEDED) {
                    ussdResponse = getLocalizedMessage("SMS.dailyRequestLimitExceeded", englishInd, destMsisdn);

                } else if (status == CCEStatus.DAILY_SAME_NUMBER_LIMIT_EXCEEDED) {
                    ussdResponse = getLocalizedMessage("SMS.dailySameNumberLimitExceeded", englishInd, destMsisdn);

                } else if (status == CCEStatus.REQUESTER_IN_BLACKLIST) {
                    ussdResponse = getLocalizedMessage("SMS.requesterInBlackList", englishInd, destMsisdn);
                } else if (status == CCEStatus.REQUESTER_BALANCE_INSUFFICIENT) {
                    ussdResponse = getLocalizedMessage("SMS.REQUESTER_BALANCE_INSUFFICIENT", englishInd, destMsisdn);
                }
            }

        } else {   // Self Service request

            int commandOption = -1;
            try {
                commandOption = Integer.parseInt(x[1]);
            } catch (NumberFormatException e) {
            }

            String targetMsisdn = "";
            if (x.length >= 3) {
                if (x[2].startsWith("0")) {
                    targetMsisdn = "62" + x[2].substring(1);
                } else {
                    targetMsisdn = x[2];
                }
            }

            int response = 0;
            if (commandOption == 1) {      // Add white list
                System.out.println("[CCE] CollecCallHandler.processUssdCommand: Add white list request");
                if (isValidMsisdn(targetMsisdn)) {
                    response = addWhiteList(requesterMsisdn, targetMsisdn);
                    if (response == 0) {
                        ussdResponse = getLocalizedMessage("SMS.errorAddWhiteList", englishInd, targetMsisdn);
                    } else {
                        ussdResponse = getLocalizedMessage("SMS.addWhiteList", englishInd, targetMsisdn);
                    }
                } else {
                    ussdResponse = getLocalizedMessage("SMS.errorMSISDNFormat", englishInd, targetMsisdn);
                }

            } else if (commandOption == 2) {  // deleteWhiteList
                System.out.println("[CCE] CollecCallHandler.processUssdCommand: Delete white list request");
                if (isValidMsisdn(targetMsisdn)) {
                    response = deleteWhiteList(requesterMsisdn, targetMsisdn);
                    if (response == 0) {
                        ussdResponse = getLocalizedMessage("SMS.errorDeleteWhiteList", englishInd, targetMsisdn);
                    } else {
                        ussdResponse = getLocalizedMessage("SMS.deleteWhiteList", englishInd, targetMsisdn);
                    }
                } else {
                    ussdResponse = getLocalizedMessage("SMS.errorMSISDNFormat", englishInd, targetMsisdn);
                }

            } else if (commandOption == 3) {   // View WhiteList
                System.out.println("[CCE] CollecCallHandler.processUssdCommand: View white list request");
                String list = viewWhiteList(requesterMsisdn);
                if (list.length() == 0) {
                    smsResponse = getLocalizedMessage("SMS.emptyWhiteList", englishInd, "");

                } else {
                    smsResponse = getLocalizedMessage("SMS.viewWhiteList", englishInd, "");
                    smsResponse = smsResponse.replace("_RESULT_", list);
                }

                System.out.println("[CCE] CollecCallHandler.processUssdCommand: SMS Response: " + smsResponse);
                ussdResponse = getLocalizedMessage("USSD.response", englishInd, "");
                getHttpcceClient().sendSMS(requesterMsisdn, smsResponse);

            } else if (commandOption == 4) {   // Add Black List
                System.out.println("[CCE] CollecCallHandler.processUssdCommand: Add black list request");
                if (isValidMsisdn(targetMsisdn)) {
                    response = addBlackList(requesterMsisdn, targetMsisdn);
                    if (response == 0) {
                        ussdResponse = getLocalizedMessage("SMS.errorAddBlackList", englishInd, targetMsisdn);
                    } else {
                        ussdResponse = getLocalizedMessage("SMS.addBlackList", englishInd, targetMsisdn);
                    }
                } else {
                    ussdResponse = getLocalizedMessage("SMS.errorMSISDNFormat", englishInd, targetMsisdn);
                }

            } else if (commandOption == 5) {  // Delete Black List
                System.out.println("[CCE] CollecCallHandler.processUssdCommand: Delete black list request");
                if (isValidMsisdn(targetMsisdn)) {
                    response = deleteBlackList(requesterMsisdn, targetMsisdn);
                    if (response == 0) {
                        ussdResponse = getLocalizedMessage("SMS.errorDeleteBlackList", englishInd, targetMsisdn);
                    } else {
                        ussdResponse = getLocalizedMessage("SMS.deleteBlackList", englishInd, targetMsisdn);
                    }
                } else {
                    ussdResponse = getLocalizedMessage("SMS.errorMSISDNFormat", englishInd, targetMsisdn);
                }

            } else if (commandOption == 6) {   // View Black List
                System.out.println("[CCE] CollecCallHandler.processUssdCommand: View black list request");
                String list = viewBlackList(requesterMsisdn);
                if (list.length() == 0) {
                    smsResponse = getLocalizedMessage("SMS.emptyBlackList", englishInd, "");

                } else {
                    smsResponse = getLocalizedMessage("SMS.viewBlackList", englishInd, "");
                    smsResponse = smsResponse.replace("_RESULT_", list);
                }

                ussdResponse = getLocalizedMessage("USSD.response", englishInd, "");
                System.out.println("[CCE] CollecCallHandler.processUssdCommand: SMS Response: " + smsResponse);

                getHttpcceClient().sendSMS(requesterMsisdn, smsResponse);


            } else if (commandOption == 8) {  // Set Language to English
                System.out.println("[CCE] CollecCallHandler.processUssdCommand: Set language to English");
                response = addEnglish(requesterMsisdn);
                if (response == 0) {
                    ussdResponse = getLocalizedMessage("SMS.errorAddEnglish", englishInd, targetMsisdn);
                } else {
                    ussdResponse = getLocalizedMessage("SMS.addEnglish", englishInd, targetMsisdn);
                }

            } else if (commandOption == 7) { // Set Language to Bahasa
                System.out.println("[CCE] CollecCallHandler.processUssdCommand: Set language to Bahasa");
                response = deleteEnglish(requesterMsisdn);
                if (response == 0) {
                    ussdResponse = getLocalizedMessage("SMS.errorDeleteEnglish", englishInd, targetMsisdn);
                } else {
                    ussdResponse = getLocalizedMessage("SMS.DeleteEnglish", englishInd, targetMsisdn);
                }


            } else if (commandOption == 9) {   // Query last Received Collect Call
                System.out.println("[CCE] CollecCallHandler.processUssdCommand: Query last received collect call");
                String response2[] = new String[4];
                response2 = viewReceivedCall(requesterMsisdn);

                if (response2[0].compareTo("") == 0) {
                    ussdResponse = getLocalizedMessage("SMS.emptyReceivedCall", englishInd, targetMsisdn);

                } else {
                    ussdResponse = getLocalizedMessage("SMS.receivedCall", englishInd, targetMsisdn);
                    ussdResponse = ussdResponse.replace("_A_NUMBER_", response2[0]);
                    ussdResponse = ussdResponse.replace("_B_NUMBER_", response2[1]);
                    ussdResponse = ussdResponse.replace("_DATE_", response2[2]);
                    ussdResponse = ussdResponse.replace("_DURATION_", response2[3]);
                }


            } else if (commandOption == 0) {  // Query last requested Collect Call
                System.out.println("[CCE] CollecCallHandler.processUssdCommand: Query last requested collect call");
                String response2[] = new String[4];
                response2 = viewRequestedCall(requesterMsisdn);
                if (response2[0].compareTo("") == 0) {
                    ussdResponse = getLocalizedMessage("SMS.emptyRequestedCall", englishInd, targetMsisdn);
                } else {
                    ussdResponse = getLocalizedMessage("SMS.requestedCall", englishInd, targetMsisdn);
                    ussdResponse = ussdResponse.replace("_A_NUMBER_", response2[0]);
                    ussdResponse = ussdResponse.replace("_B_NUMBER_", response2[1]);
                    ussdResponse = ussdResponse.replace("_DATE_", response2[2]);
                    ussdResponse = ussdResponse.replace("_DURATION_", response2[3]);
                }

            } else {    // Invalid USSD command
                ussdResponse = getLocalizedMessage("SMS.errorMSISDNFormat", englishInd, x[1]);
                System.out.println("[CCE] CollecCallHandler.processUssdCommand: Invalid MSISDN/CC request: " + ussdCommand);
            }
        }

        System.out.println("[CCE] CollecCallHandler.processUssdCommand: USSD Response: " + ussdResponse);
        return ussdResponse;


    }

    public int addWhiteList(String ownerMsisdn, String msisdn) {
        Connection conn;
        try {
            conn = ds.getConnection();
            Statement stmt = conn.createStatement();
            String whereClause = "WHERE OWNER_MSISDN='" + ownerMsisdn + "' and MSISDN ='" + msisdn + "'";
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) from CC_WHITELIST " + whereClause);
            rs.next();
            int count = rs.getInt(1);

            if (count == 0) {
                stmt.executeUpdate(
                        "INSERT INTO CC_WHITELIST (OWNER_MSISDN, MSISDN, UPDATE_TIMESTAMP) VALUES ('" +
                        ownerMsisdn + "','" + msisdn + "',sysdate)");
            }

            stmt.executeUpdate("DELETE FROM CC_BLACKLIST " + whereClause);
            stmt.close();
            return 1;   // success

        } catch (SQLException e) {
            System.out.println("[CCE] CollecCallHandler: add whitelist error");
            e.printStackTrace();
            return 0;  // error
        }

    }

    public int deleteWhiteList(String ownerMsisdn, String msisdn) {
        Connection conn;
        PreparedStatement stmt;
        try {
            conn = ds.getConnection();
            stmt = conn.prepareStatement("DELETE FROM CC_WHITELIST WHERE OWNER_MSISDN=? AND MSISDN=?");
            stmt.setString(1, ownerMsisdn);
            stmt.setString(2, msisdn);
            stmt.executeUpdate();
            stmt.close();
            return 1;  // success

        } catch (SQLException e) {
            System.out.println("[CCE] CollecCallHandler: delete whitelist error");
            e.printStackTrace();
            return 0;  // error
        }
    }

    public int addBlackList(String ownerMsisdn, String msisdn) {
        Connection conn;
        try {
            conn = ds.getConnection();
            Statement stmt = conn.createStatement();
            String whereClause = "WHERE OWNER_MSISDN='" + ownerMsisdn + "' and MSISDN ='" + msisdn + "'";
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) from CC_BLACKLIST " + whereClause);
            rs.next();
            int count = rs.getInt(1);

            if (count == 0) {
                stmt.executeUpdate(
                        "INSERT INTO CC_BLACKLIST (OWNER_MSISDN, MSISDN, UPDATE_TIMESTAMP) VALUES ('" +
                        ownerMsisdn + "','" + msisdn + "',sysdate)");
            }

            stmt.executeUpdate("DELETE FROM CC_WHITELIST " + whereClause);
            stmt.close();
            return 1;   // success

        } catch (SQLException e) {
            System.out.println("[CCE] CollecCallHandler: add blacklist error");
            e.printStackTrace();
            return 0;  // error
        }
    }

    public int deleteBlackList(String ownerMsisdn, String msisdn) {
        Connection conn;
        PreparedStatement stmt;
        try {
            conn = ds.getConnection();
            stmt = conn.prepareStatement("DELETE FROM CC_BLACKLIST WHERE OWNER_MSISDN=? AND MSISDN=?");
            stmt.setString(1, ownerMsisdn);
            stmt.setString(2, msisdn);
            stmt.executeUpdate();
            stmt.close();

            return 1; // success
        } catch (SQLException e) {
            System.out.println("[CCE] CollecCallHandler: delete blacklist error");
            e.printStackTrace();
            return 0; // error
        }
    }

    public int addEnglish(String msisdn) {
        Connection conn;
        try {
            conn = ds.getConnection();
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) from CC_ENGLISH where MSISDN='" + msisdn + "'");
            rs.next();
            int count = rs.getInt(1);

            if (count == 0) {
                stmt.executeUpdate(
                        "INSERT INTO CC_ENGLISH (MSISDN, UPDATE_TIMESTAMP) VALUES ('" +
                        msisdn + "',sysdate)");
            }
            stmt.close();
            return 1;   // success

        } catch (SQLException e) {
            System.out.println("[CCE] CollecCallHandler: add english error");
            e.printStackTrace();
            return 0;  // error
        }
    }

    public int deleteEnglish(String msisdn) {
        Connection conn;
        PreparedStatement stmt;
        try {
            conn = ds.getConnection();
            stmt = conn.prepareStatement("DELETE FROM CC_ENGLISH WHERE MSISDN=?");
            stmt.setString(1, msisdn);
            stmt.executeUpdate();
            stmt.close();
            return 1; // success
        } catch (SQLException e) {
            System.out.println("[CCE] CollecCallHandler: delete english error");
            e.printStackTrace();
            return 0; // error
        }
    }

    public String viewWhiteList(String ownerMsisdn) {
        Connection conn;
        ResultSet rset;
        String result = "";
        PreparedStatement stmt;
        try {
            conn = ds.getConnection();
            stmt = conn.prepareStatement("SELECT MSISDN FROM CC_WHITELIST WHERE OWNER_MSISDN=?");
            stmt.setString(1, ownerMsisdn);
            rset = stmt.executeQuery();
            while (rset.next()) {
                result += " \n" + rset.getString("MSISDN");
            }
            stmt.close();
        } catch (SQLException e) {
            System.out.println("[CCE] CollecCallHandler: view whitelist error");
            e.printStackTrace();
        }
        return result;
    }

    public String viewBlackList(String ownerMsisdn) {
        Connection conn;
        ResultSet rset;
        String result = "";
        PreparedStatement stmt;
        try {
            conn = ds.getConnection();
            stmt = conn.prepareStatement("SELECT MSISDN FROM CC_BLACKLIST WHERE OWNER_MSISDN=?");
            stmt.setString(1, ownerMsisdn);
            rset = stmt.executeQuery();
            while (rset.next()) {
                result += " \n" + rset.getString("MSISDN");
            }
            stmt.close();
        } catch (SQLException e) {
            System.out.println("[CCE] CollecCallHandler: view blacklist error");
            e.printStackTrace();
        }
        return result;
    }


    /* Response from outgoing to USSDGW */
    public void onHttpResponse(HttpResponse event, ActivityContextInterface aci) {
        System.out.println("[CCE] CollecCallHandler.onHttpResponse: " + event.toString() + "; status=" + event.getStatusCode());
        try {
            String data = event.getContentAsString();
            System.out.println("[CCE] CollecCallHandler.onHttpResponse: event content: " + data);
        } catch (Exception e) {
            System.out.println("[CCE] CollecCallHandler.onHttpResponse: Received ERROR response from USSDGW");
            e.printStackTrace();
            //nothing
        }
    }

    /* Response to USSDGW */
    public void httpResponder(String message) {
        // create the OK response object
        HttpResponse response = activity.createResponse(200, "OK");

        System.out.println("[CCE] CollecCallHandler.httpResponder: response text: " + message);
        try {
            response.setContentAsString("text/html; charset=\"utf-8\"", message);
            // send the response
            System.out.println("[CCE] CollecCallHandler.httpResponder: sending http response: ");
            activity.sendResponse(response);
        } catch (Exception e) {
            System.out.println("[CCE] CollecCallHandler.httpResponder: send HTTP response fail");
            e.printStackTrace();
        }

    }

    public void sbbExceptionThrown(Exception exception, Object event, ActivityContextInterface aci) {
    }
    public static final String header =
            "<html><head><title>HTTP Ping SBB</title></head><body>\n";
    public static final String footer =
            "</body></html>";

    public static Map<String, String> getQueryMap(String query) {
        String[] params = query.split("&");
        Map<String, String> map = new HashMap<String, String>();
        for (String param : params) {
            String paramPair[] = param.split("=");
            String name = paramPair[0].toUpperCase();
            String value = paramPair[1];
            map.put(name, value);
        }
        return map;
    }

    public String[] viewReceivedCall(String msisdn) {
        Connection conn;
        ResultSet rset;
        long result = 0;
        String result2[] = new String[]{"", "", "", ""};
        PreparedStatement stmt;
        try {
            conn = ds.getConnection();
            stmt = conn.prepareStatement("Select max(trans_id) as maxtransid from CC_TRANSACTION where payer_msisdn = ? and status_code=100");
            stmt.setString(1, msisdn);
            rset = stmt.executeQuery();
            while (rset.next()) {
                result = rset.getLong("maxtransid");
            }

            stmt.close();
            stmt = conn.prepareStatement("Select REQUESTER_MSISDN, PAYER_MSISDN, call_start_time, call_duration from CC_TRANSACTION where trans_id = " + result);
            rset = stmt.executeQuery();
            while (rset.next()) {
                result2[0] = rset.getString("REQUESTER_MSISDN");
                result2[1] = rset.getString("PAYER_MSISDN");
                result2[2] = rset.getString("call_start_time");
                result2[3] = rset.getString("call_duration");
            }
            stmt.close();
        } catch (SQLException e) {
            System.out.println("[CCE] CollecCallHandler: Received call query fail");
            e.printStackTrace();
        }
        return result2;
    }

    public String[] viewRequestedCall(String msisdn) {
        Connection conn;
        ResultSet rset;
        long result = 0;
        String result2[] = new String[]{"", "", "", ""};
        PreparedStatement stmt;
        try {
            conn = ds.getConnection();
            stmt = conn.prepareStatement("Select max(trans_id) as maxtransid from CC_TRANSACTION where requester_msisdn = ? and status_code=100");
            stmt.setString(1, msisdn);
            rset = stmt.executeQuery();
            while (rset.next()) {
                result = rset.getLong("maxtransid");
            }
            stmt.close();
            stmt = conn.prepareStatement("Select REQUESTER_MSISDN, PAYER_MSISDN, call_start_time, call_duration from CC_TRANSACTION where trans_id = " + result);
            rset = stmt.executeQuery();
            while (rset.next()) {
                result2[0] = rset.getString("REQUESTER_MSISDN");
                result2[1] = rset.getString("PAYER_MSISDN");
                result2[2] = rset.getString("call_start_time");
                result2[3] = rset.getString("call_duration");

            }
            stmt.close();
        } catch (SQLException e) {
            System.out.println("[CCE] CollecCallHandler: Requested call query fail");
            e.printStackTrace();
        }
        return result2;
    }

    public boolean isValidMsisdn(String msisdn) {

        Matcher m = this.msisdnPattern.matcher(msisdn);
        return m.matches();
    }

    private String getLocalizedMessage(String paramName, boolean englishFlag, String msisdn) {

        String message = "";
        if (englishFlag) {
            message = this.dbConfig.getLocEnglish(paramName);
        } else {
            message = this.dbConfig.getLocBahasa(paramName);
        }

        return message.replace("_MSISDN_", msisdn);

    }

    public void sbbPostCreate() {
        try {
            super.sbbPostCreate();

            OnlineCallFlowLocalInterface capcceClient = (OnlineCallFlowLocalInterface) getCapCCEClientChildRelation().create();
            setCapcceClient(capcceClient);

            HTTPCCELocalInterface httpcceClient = (HTTPCCELocalInterface) getHttpCCEClientChildRelation().create();
            setHttpcceClient(httpcceClient);

            HLRValidationLocalInterface mapcceClient = (HLRValidationLocalInterface) getMapCCEClientChildRelation().create();
            setMapcceClient(mapcceClient);

            //setState(STATE_IDLE);

            // load parameters
            mapcceClient.initConfig();
            capcceClient.initConfig();
            httpcceClient.initConfig();
            this.dbConfig = SSPdbConfig.getInstance();
            String patternString = dbConfig.get("CC.VALID_MSISDN_PATTERN");
            this.msisdnPattern = Pattern.compile(patternString);

            this.tLogger = TransactionLogger.getInstance();

        } catch (CreateException e) {
            System.out.println("[CCE] CollecCallHandler.sbbPostCreate: Unable to create Child SBB");
            e.printStackTrace();
        }
    }

    public void onServiceStartedEvent(ServiceStartedEvent event, ActivityContextInterface aci) {
        aci.detach(getSbbLocalObject()); // Don't need to stay attached to service activity
        System.out.println("[CCE] CollecCallHandler.onServiceStartedEvent: " + event);

    }

    public abstract OnlineCallFlowLocalInterface getCapcceClient();

    public abstract void setCapcceClient(OnlineCallFlowLocalInterface capccelocalinterface);

    public abstract HTTPCCELocalInterface getHttpcceClient();

    public abstract void setHttpcceClient(HTTPCCELocalInterface httpccelocalinterface);

    public abstract HLRValidationLocalInterface getMapcceClient();

    public abstract void setMapcceClient(HLRValidationLocalInterface mapccelocalinterface);

    public abstract void setInActivityContext(ActivityContextInterface inaci);

    public abstract ActivityContextInterface getInActivityContext();

    public abstract ChildRelation getMapCCEClientChildRelation();

    public abstract ChildRelation getCapCCEClientChildRelation();

    public abstract ChildRelation getHttpCCEClientChildRelation();

    private int validateCCRequest(CollectCallProfile ccProfile) {

        int status = CCEStatus.UNKNOWN_STATUS;
        String requesterMsisdn = ccProfile.getRequesterMsisdn();
        String payerMsisdn = ccProfile.getPayerMsisdn();

        // set language B party
        ccProfile.setEnglishPayer(this.isEnglishSubscriber(payerMsisdn));

        // Check Blacklist
        if (isBlackList(requesterMsisdn, payerMsisdn)) {
            status = CCEStatus.REQUESTER_IN_BLACKLIST;
            return status;
        }

        // set WhiteList
        if (isWhiteList(requesterMsisdn, payerMsisdn)) {
            ccProfile.setWhitelist(true);

        } else {
            // Check Daily Limit
            int dailyRequestCount = 0;
            int dailySameNumberCount = 0;
            try {
                Connection conn = ds.getConnection();
                String dateStr = sdf.format(new java.util.Date());
                long minTransId = 1000000000L * Long.parseLong(dateStr);  //yyMMdd000000000
                long maxTransId = minTransId + 999999999L;  //yyMMdd999999999
                PreparedStatement pstmt = conn.prepareStatement(
                        "SELECT COUNT(*), sum(decode(PAYER_MSISDN, ?, 1, 0)) " +
                        "from CC_TRANSACTION WHERE REQUESTER_MSISDN=? and status_code=100" +
                        " and TRANS_ID >= ? and TRANS_ID < ?");
                pstmt.setString(1, payerMsisdn);
                pstmt.setString(2, requesterMsisdn);
                pstmt.setLong(3, minTransId);
                pstmt.setLong(4, maxTransId);
                ResultSet rs = pstmt.executeQuery();
                rs.next();
                dailyRequestCount = rs.getInt(1);
                dailySameNumberCount = rs.getInt(2);
                pstmt.close();

                System.out.println("[CCE] CollecCallHandler.validateCCRequest: dailyCount=" + dailyRequestCount +
                        "; dailySameCount=" + dailySameNumberCount);
            } catch (SQLException e) {
                System.out.println("[CCE] CollecCallHandler: Daily request count fail");
                e.printStackTrace();
            }

            int dailyRequestLimit = Integer.parseInt(dbConfig.get("CC.DAILY_REQUEST_LIMIT"));
            if (dailyRequestCount >= dailyRequestLimit) {
                status = CCEStatus.DAILY_REQUEST_LIMIT_EXCEEDED;
                return status;
            }

            int dailySameNumberLimit = Integer.parseInt(dbConfig.get("CC.DAILY_SAME_NUMBER_LIMIT"));
            if (dailySameNumberCount >= dailySameNumberLimit) {
                status = CCEStatus.DAILY_SAME_NUMBER_LIMIT_EXCEEDED;
                return status;
            }
        }

        // Check balance
        if ("1".equals(dbConfig.get("CC.ENABLE_REQUEST_CHARGING"))) {
            System.out.println("[CCE] CollecCallHandler.validateCCRequest: checking balance. result=");
            int result = getHttpcceClient().getBalance("1" + ccProfile.getTransId(), ccProfile.getRequesterMsisdn());
            if (result == 0) {
                status = CCEStatus.REQUESTER_BALANCE_INSUFFICIENT;
                return status;
            }
        }

        // Check SCB Subscriber
        checkSCBPayer(ccProfile);

        return status;
    }

    private boolean isWhiteList(String requesterMsisdn, String payerMsisdn) {
        try {
            Connection conn = ds.getConnection();
            PreparedStatement pstmt = conn.prepareStatement(
                    "SELECT MSISDN from CC_WHITELIST WHERE OWNER_MSISDN=? and MSISDN=?");
            pstmt.setString(1, payerMsisdn);
            pstmt.setString(2, requesterMsisdn);
            ResultSet rs = pstmt.executeQuery();
            boolean result = rs.next();
            pstmt.close();
            System.out.println("[CCE] CollecCallHandler.isWhiteList: " + result);
            return result;
        } catch (SQLException e) {
            System.out.println("[CCE] CollecCallHandler: white list query fail");
            e.printStackTrace();
            return false;
        }
    }

    private boolean isBlackList(String requesterMsisdn, String payerMsisdn) {
        try {
            Connection conn = ds.getConnection();
            PreparedStatement pstmt = conn.prepareStatement(
                    "SELECT MSISDN from CC_BLACKLIST WHERE OWNER_MSISDN=? and MSISDN=?");
            pstmt.setString(1, payerMsisdn);
            pstmt.setString(2, requesterMsisdn);
            ResultSet rs = pstmt.executeQuery();
            boolean result = rs.next();
            pstmt.close();
            System.out.println("[CCE] CollecCallHandler.isBlackList: " + result);

            return result;
        } catch (SQLException e) {
            System.out.println("[CCE] CollecCallHandler: black list query fail");
            e.printStackTrace();
            return false;
        }
    }

    public boolean isEnglishSubscriber(String msisdn) {

        try {
            Connection conn = ds.getConnection();
            PreparedStatement pstmt = conn.prepareStatement(
                    "SELECT MSISDN from CC_ENGLISH WHERE MSISDN=?");
            pstmt.setString(1, msisdn);
            ResultSet rs = pstmt.executeQuery();
            boolean result = rs.next();
            pstmt.close();
            System.out.println("[CCE] CollecCallHandler.isEnglishSubscriber(" + msisdn + "): " + result);
            return result;
        } catch (SQLException e) {
            System.out.println("[CCE] CollecCallHandler: English query fail");
            e.printStackTrace();
            return false;
        }
    }

    private void checkSCBPayer(CollectCallProfile ccProfile) {

        try {
            Connection conn = ds.getConnection();
            PreparedStatement pstmt = conn.prepareStatement(
                    "SELECT ORIG_OCSI_GT, ORIG_SERVICE_KEY from SUBSCRIPTION WHERE ISENABLED=1 and END_DATE> sysdate and MSISDN=?");
            pstmt.setString(1, ccProfile.getPayerMsisdn());
            ResultSet rs = pstmt.executeQuery();
            boolean result = rs.next();
            if (result) {
                ccProfile.setScbPayer(true);
                ccProfile.setPayerOcsGt(rs.getString(1));
                ccProfile.setPayerServiceKey(rs.getInt(2));
            }
            pstmt.close();
            System.out.println("[CCE] CollecCallHandler.checkSCBPayer:" + ccProfile.getPayerMsisdn() + " = " + result);

        } catch (SQLException e) {
            System.out.println("[CCE] CollecCallHandler: Check SCB payer query fail");
            e.printStackTrace();
        }
    }
}

