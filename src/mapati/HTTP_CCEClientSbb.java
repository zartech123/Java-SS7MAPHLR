package mapati;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLEncoder;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.slee.ActivityContextInterface;
import javax.slee.SbbContext;
import javax.slee.facilities.TraceLevel;

import com.opencloud.slee.resources.http.HttpActivityContextInterfaceFactory;
import com.opencloud.slee.resources.http.HttpProvider;
import com.opencloud.slee.resources.http.HttpRequest;
import com.opencloud.slee.resources.http.HttpResponse;
import com.opencloud.slee.resources.http.OutgoingHttpRequestActivity;

public abstract class HTTP_CCEClientSbb extends BaseSbb {

    private HttpProvider httpProvider;
    private HttpActivityContextInterfaceFactory aciFactory;
    private String SMSGW_URL = "";
    private String SMSGW_QUERY_STRING = "";
    private SSPdbConfig dbConfig;
    private String SMSGW_PASSWORD = "";
    private String INGW_APPSID = "";
    private String INGW_PASSWORD = "";
    private String INGW_CPNAME = "";
    private String INGW_CHECK_BALANCE_QUERY_STRING = "";
    private String INGW_CHECK_BALANCE_URL = "";
    private String INGW_CHARGE_QUERY_STRING = "";
    private String INGW_CHARGE_URL = "";
    private int balance;
    private int minBalance = 0;

    public void setSbbContext(SbbContext ctx) {
        super.setSbbContext(ctx);
        try {
            final Context env = (Context) new InitialContext().lookup("java:comp/env");
            httpProvider = (HttpProvider) env.lookup("slee/resources/http/2.0/provider");
            aciFactory = (HttpActivityContextInterfaceFactory) env.lookup("slee/resources/http/2.0/acifactory");
            info("===============load http evn variable ===================");
        } catch (NamingException ne) {
            ne.printStackTrace();
        }
    }

    public abstract CollectCallHandlerLocalInterface getListener();

    public abstract void setListener(CollectCallHandlerLocalInterface listener);

    public void initConfig() {
        dbConfig = SSPdbConfig.getInstance();
        this.SMSGW_URL = dbConfig.get("SMSGW.URL");
        this.SMSGW_QUERY_STRING = dbConfig.get("SMSGW.queryString");
        this.SMSGW_PASSWORD = dbConfig.get("SMSGW.password");
        this.INGW_APPSID = dbConfig.get("INGW.info_appsid");
        this.INGW_PASSWORD = dbConfig.get("INGW.info_password");
        this.INGW_CPNAME = dbConfig.get("INGW.cpname");
        this.INGW_CHECK_BALANCE_QUERY_STRING = dbConfig.get("INGW.checkBalance.queryString");
        this.INGW_CHECK_BALANCE_URL = dbConfig.get("INGW.checkBalance.URL");
        this.INGW_CHARGE_QUERY_STRING = dbConfig.get("INGW.charging.queryString");
        this.INGW_CHARGE_URL = dbConfig.get("INGW.charging.URL");
        this.minBalance = new Integer(dbConfig.get("CC.MIN_REQUESTER_BALANCE"));
    }

    public void sendSMS(String msisdn, String messageText) {
        String SMSGWURL = "";
        try {
            SMSGWURL = this.SMSGW_URL + this.SMSGW_QUERY_STRING;
            SMSGWURL = SMSGWURL.replace("[MSISDN]", msisdn);
            SMSGWURL = SMSGWURL.replace("[password]", this.SMSGW_PASSWORD);
            SMSGWURL = SMSGWURL.replace("[sms]", URLEncoder.encode(messageText, "UTF-8"));
            System.out.println("send SMS to SMSGW " + SMSGWURL);

            HttpRequest newRequest = httpProvider.createRequest(HttpRequest.GET, new URL(SMSGWURL));
            newRequest.setContentType("text/plain; charset=\"utf-8\"");

            OutgoingHttpRequestActivity activity = httpProvider.sendRequest(newRequest);
            ActivityContextInterface newACI = aciFactory.getActivityContextInterface(activity);
            newACI.attach(getSbbLocalObject());

        } catch (UnsupportedEncodingException e) {
            severe("sendSMS encoding error", e);
        } catch (IOException e) {
            severe("sendSMS io error", e);
        } finally {
            //nothing
        }
    }

    public int charge(String transid, String msisdn) {
        String ingwURL;
        String ingwQueryString;
        int result = 0;

        ingwQueryString = this.INGW_CHARGE_QUERY_STRING;
        ingwQueryString = ingwQueryString.replace("[MSISDN]", msisdn);
        ingwQueryString = ingwQueryString.replace("[appsid]", this.INGW_APPSID);
        ingwQueryString = ingwQueryString.replace("[password]", this.INGW_PASSWORD);
        ingwQueryString = ingwQueryString.replace("[cpname]", this.INGW_CPNAME);
        ingwQueryString = ingwQueryString.replace("[trxid]", transid);

        ingwURL = this.INGW_CHARGE_URL + ingwQueryString;
        System.out.println("send Charge " + ingwURL);

        try {
            HttpRequest newRequest = httpProvider.createRequest(HttpRequest.GET, new URL(ingwURL));
            newRequest.setContentType("text/plain; charset=\"utf-8\"");

            HttpResponse activity = httpProvider.sendSyncRequest(newRequest);
            String[] responseIN = activity.getContentAsString().split(" ");
            System.out.println("response Check Balance:" + activity.getContentAsString());

            if (responseIN[2].equals("2")) {
                result = 1;	//OK
            } else {
                result = 0;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("getBalance Result :" + result);

        return result;
    }

    public int getBalance(String transid, String msisdn) {
        String ingwURL;
        String ingwQueryString;
        int resultData = 0;
        ingwQueryString = this.INGW_CHECK_BALANCE_QUERY_STRING;
        ingwQueryString = ingwQueryString.replace("[MSISDN]", msisdn);
        ingwQueryString = ingwQueryString.replace("[appsid]", this.INGW_APPSID);
        ingwQueryString = ingwQueryString.replace("[password]", this.INGW_PASSWORD);
        ingwQueryString = ingwQueryString.replace("[cpname]", this.INGW_CPNAME);
        ingwQueryString = ingwQueryString.replace("[trxid]", transid);

        ingwURL = this.INGW_CHECK_BALANCE_URL + ingwQueryString;
        System.out.println("send Check Balance" + ingwURL);

        try {
            HttpRequest newRequest = httpProvider.createRequest(HttpRequest.GET, new URL(ingwURL));
            newRequest.setContentType("text/plain; charset=\"utf-8\"");

            HttpResponse activity = httpProvider.sendSyncRequest(newRequest);
            String[] responseIN = activity.getContentAsString().split(" ");
            System.out.println("response Check Balance:" + activity.getContentAsString());

            if (responseIN[2].equals("2")) {
                //OK online number
                if (responseIN[7].equals("PRE")) {
                    resultData = 0;
                    balance = Integer.parseInt(responseIN[3]);
                    if (balance >= minBalance) {
                        resultData = 1;
                    }
                } else {
                    resultData = 1;
                }
            } else if (responseIN[2].equals("17")) {
                //postpaid number
                resultData = 1;
            }

            System.out.println("getBalance Result :" + resultData);

        } catch (Exception e) {
            e.printStackTrace();
        }

        return resultData;
    }

    @Override
    protected String getTraceType() {
        // TODO Auto-generated method stub
        return null;
    }
}
