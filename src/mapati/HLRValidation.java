package mapati;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.slee.ActivityContextInterface;
import javax.slee.ChildRelation;
import javax.slee.CreateException;
import javax.slee.SbbContext;
import javax.slee.serviceactivity.ServiceStartedEvent;
import javax.sql.DataSource;


import com.opencloud.slee.resources.cgin.CGINActivityContextInterfaceFactory;
import com.opencloud.slee.resources.cgin.CGINProvider;
import com.opencloud.slee.resources.cgin.DialogOpenAcceptEvent;
import com.opencloud.slee.resources.cgin.OperationErrorEvent;
import com.opencloud.slee.resources.cgin.ProtocolException;
import com.opencloud.slee.resources.cgin.SccpAddress;
import com.opencloud.slee.resources.cgin.TooManyInvokesException;
import com.opencloud.slee.resources.cgin.map.MAPAnyTimeInterrogationArg;
import com.opencloud.slee.resources.cgin.map.MAPAnyTimeInterrogationRes;
import com.opencloud.slee.resources.cgin.map.MAPAnyTimeSubscriptionInterrogationArg;
import com.opencloud.slee.resources.cgin.map.MAPAnyTimeSubscriptionInterrogationRes;
import com.opencloud.slee.resources.cgin.map.MAPCAMEL_SubscriptionInfo;
import com.opencloud.slee.resources.cgin.map.MAPCallBarringData;
import com.opencloud.slee.resources.cgin.map.MAPCallBarringInfo;
import com.opencloud.slee.resources.cgin.map.MAPCallForwardingData;
import com.opencloud.slee.resources.cgin.map.MAPDialog;
import com.opencloud.slee.resources.cgin.map.MAPExt_CallBarInfo;
import com.opencloud.slee.resources.cgin.map.MAPExt_CallBarringFeature;
import com.opencloud.slee.resources.cgin.map.MAPLocationInformation;
import com.opencloud.slee.resources.cgin.map.MAPMAP_DialoguePDU;
import com.opencloud.slee.resources.cgin.map.MAPMAP_OpenInfo;
import com.opencloud.slee.resources.cgin.map.MAPODB_Info;
import com.opencloud.slee.resources.cgin.map.MAPRequestedCAMEL_SubscriptionInfo;
import com.opencloud.slee.resources.cgin.map.MAPRequestedInfo;
import com.opencloud.slee.resources.cgin.map.MAPRequestedSubscriptionInfo;
import com.opencloud.slee.resources.cgin.map.MAPRoutingInfoForSM_Arg;
import com.opencloud.slee.resources.cgin.map.MAPRoutingInfoForSM_Res;
import com.opencloud.slee.resources.cgin.map.MAPSubscriberIdentity;
import com.opencloud.slee.resources.cgin.map.MAPSubscriberInfo;
import com.opencloud.slee.resources.cgin.map.events.MAPAnyTimeInterrogationResultEvent;
import com.opencloud.slee.resources.cgin.map.events.MAPAnyTimeSubscriptionInterrogationResultEvent;
import com.opencloud.slee.resources.cgin.map.events.MAPSendRoutingInfoForSMResultEvent;
import com.opencloud.slee.resources.cgin.map.metadata.MAPApplicationContexts;
import com.opencloud.slee.resources.in.datatypes.cc.AddressString;
import com.opencloud.slee.resources.in.datatypes.cc.CellGlobalId;
import com.opencloud.slee.resources.in.datatypes.cc.LocationNumber;
import com.opencloud.slee.resources.in.datatypes.map.SSCode;

public abstract class HLRValidation extends BaseSbb {

    public static final int INIT = 100;
    public static final int INQUIRY_A = 101;
    public static final int INQUIRY_B = 102;
    public static final int CONTINUE = 103;
    public static final int FAIL = 104;
    private SSPdbConfig dbConfig;
    private SbbContext sbbContext;
    private CGINProvider cginprovider;
    private CGINActivityContextInterfaceFactory aciFactory;
    private String cceGt = "";
    private SccpAddress cceSccpAddr = null;
    private String hlrGt = "";
    private SccpAddress hlrSccpAddr = null;
    private AddressString cceAddress = null;
    private MAPRequestedInfo atiReqInfo = null;
    private MAPRequestedSubscriptionInfo atsiReqInfo = null;
    private TransactionLogger tLogger;

    public void setSbbContext(SbbContext sbbContext) {
        super.setSbbContext(sbbContext);
        try {
            this.sbbContext = sbbContext;
            Context env = (Context) new InitialContext().lookup("java:comp/env");
            cginprovider = (CGINProvider) env.lookup("slee/resources/map/provider");
            aciFactory = (CGINActivityContextInterfaceFactory) env.lookup("slee/resources/map/acifactory");

//            logProvider = (LogProvider) env.lookup("cce/resources/log/provider");
//            logACIFactory = (LogActivityContextInterfaceFactory) env.lookup("cce/resources/log/logacifactory");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    SccpAddress createSccpAddr(String GT, int ssn) {
        SccpAddress sccpaddr;
        sccpaddr = new SccpAddress(SccpAddress.Type.C7);
        sccpaddr.setRouteOnPC(false);
        sccpaddr.setSSN(ssn);
        sccpaddr.setNational(false);
        sccpaddr.setEncodingScheme(((GT.length() % 2) == 0 ? 2 : 1));
        sccpaddr.setNatureOfAddress(SccpAddress.NATURE_INTERNATIONAL_NUMBER);
        sccpaddr.setNumberingPlan(SccpAddress.NUMBERING_PLAN_ISDN);
        sccpaddr.setTranslationType(0);
        sccpaddr.setGlobalTitleIndicator(SccpAddress.GTIndicator.GT_0100);
        sccpaddr.setAddress(GT);

        return sccpaddr;
    }

    protected String getTraceType() {
        return "HLR Validation";
    }

    public abstract CollectCallHandlerLocalInterface getListener();

    public abstract void setListener(CollectCallHandlerLocalInterface listener);

    public abstract ActivityContextInterface getMapccActivityContext();

    public abstract void setMapccActivityContext(ActivityContextInterface id);

    public void sbbPostCreate() {
        try {
            super.sbbPostCreate();

            HTTPCCELocalInterface httpcceClient = (HTTPCCELocalInterface) getHttpCCEClientChildRelation().create();
            setHttpcceClient(httpcceClient);

        } catch (CreateException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void onServiceStartedEvent(ServiceStartedEvent event, ActivityContextInterface aci) {
        aci.detach(getSbbLocalObject()); // Don't need to stay attached to service activity
        info("received start event: " + event);


    }

    public abstract void setCallState(int state);

    public abstract int getCallState();

    public abstract void setCcProfile(CollectCallProfile ccProfile);

    public abstract CollectCallProfile getCcProfile();

    public void startValidation(CollectCallProfile ccProfile) {

        System.out.println("Start HLR Validation");

        setCcProfile(ccProfile);
        setCallState(HLRValidation.INQUIRY_A);
//        sendATSI(ccProfile.getRequesterMsisdn());
//        sendATI(ccProfile.getRequesterMsisdn());
//        sendSRISM(ccProfile.getPayerMsisdn());

    }

    public void initConfig() {
        dbConfig = SSPdbConfig.getInstance();
        cceGt = dbConfig.get("CC.CCE_GT_ADDRESS");
        cceSccpAddr = createSccpAddr(this.cceGt, 147); //ssn = 147 (gsmSCF)
        hlrGt = "6281105108";
        hlrSccpAddr = createSccpAddr(this.cceGt, 6); //ssn = 6 (HLR)

        this.cceAddress = new AddressString();
        cceAddress.setAddress(this.cceGt);
        cceAddress.setNature(AddressString.Nature.INTERNATIONAL);
        cceAddress.setNumberingPlan(AddressString.NumberingPlan.ISDN);

        atiReqInfo = new MAPRequestedInfo();
        atiReqInfo.setCurrentLocationPresent(true);
        atiReqInfo.setLocationInformationPresent(true);
        atiReqInfo.setSubscriberStatePresent(true);

        atsiReqInfo = new MAPRequestedSubscriptionInfo();
        atsiReqInfo.setRequestedCAMEL_SubscriptionInfo(MAPRequestedCAMEL_SubscriptionInfo.o_CSI);
        atsiReqInfo.setOdbPresent(true);

        this.tLogger = TransactionLogger.getInstance();
        getHttpcceClient().initConfig();
    }

    private void sendATI(String msisdn) {


        System.out.println("Sending ATI for " + msisdn);
        System.out.println("Set Calling partynumber : " + this.cceGt);

        try {
            SccpAddress destAddress = this.createSccpAddr(msisdn, 6);

            final MAPMAP_DialoguePDU pdu = new MAPMAP_DialoguePDU();
            final MAPMAP_OpenInfo openInfo = new MAPMAP_OpenInfo();
            pdu.setMap_open(openInfo);

            MAPDialog dialog = (MAPDialog) cginprovider.issueOpenRequest(
                    MAPApplicationContexts.anyTimeInfoEnquiryContext_v3_ac,
                    cceSccpAddr, destAddress, 30000, pdu);

            ActivityContextInterface dialogACI = aciFactory.getActivityContextInterface(dialog);
            dialogACI.attach(getSbbLocalObject());

            MAPAnyTimeInterrogationArg atiarg = new MAPAnyTimeInterrogationArg();
            atiarg.setRequestedInfo(atiReqInfo);
            atiarg.setGsmSCF_Address(cceAddress);

            MAPSubscriberIdentity subIdentity = new MAPSubscriberIdentity();
            AddressString ms = new AddressString();
            ms.setAddress(msisdn);
            ms.setNature(AddressString.Nature.INTERNATIONAL);
            ms.setNumberingPlan(AddressString.NumberingPlan.ISDN);

            subIdentity.setMsisdn(ms);
            atiarg.setSubscriberIdentity(subIdentity);

            dialog.sendAnyTimeInterrogation(30000, atiarg);
            dialog.sendDelimiter();
            System.out.println("ATI sent");
            System.out.println("[sendATI]: Send --> sendATI To HLR" + atiarg.toString());

        } catch (TooManyInvokesException e) {

            e.printStackTrace();
        } catch (ProtocolException e) {

            e.printStackTrace();
        }

    }

    public void onatiResp(MAPAnyTimeInterrogationResultEvent event, ActivityContextInterface aci) {
        System.out.println("Receive ATI response: " + event.getResult().toString());

        CollectCallProfile ccProfile = getCcProfile();
        MAPLocationInformation locInfo = null;
        LocationNumber locNum = null;
        CellGlobalId cid = null;
        String mccmnc = "";

        MAPAnyTimeInterrogationRes atiRes = event.getResult();
        if (atiRes.hasSubscriberInfo()) {
            MAPSubscriberInfo subsInfo = atiRes.getSubscriberInfo();
            locInfo = subsInfo.getLocationInformation();
            locNum = locInfo.getLocationNumber();
            locInfo.setLocationNumber(null);
            cid = locInfo.getCellGlobalIdOrServiceAreaIdOrLAI().getCellGlobalIdOrServiceAreaIdFixedLength();
            mccmnc = cid.getMobileCountryCode() + cid.getMobileNetworkCode();
        } else {
            handleError(CCEStatus.ATI_ERROR, "state=" + getCallState());
            return;
        }

        if (getCallState() == HLRValidation.INQUIRY_A) {
            // Validate A location

            ccProfile.setRequesterLocInfo(locInfo);
            ccProfile.setRequesterLocNum(locNum);
            ccProfile.setRequesterMccMnc(mccmnc);
            ccProfile.setRequesterLac(cid.getLocationAreaCode());
            ccProfile.setRequesterCellId(cid.getCellId());

            if (mccmnc.equals(dbConfig.get("CC.HOME_LAI_PREFIX")) || dbConfig.isLocationAllowed(mccmnc)) {
                if (!mccmnc.equals(dbConfig.get("CC.HOME_LAI_PREFIX"))) {
                    ccProfile.setRoamingRequester(true);
                }
                sendATSI(ccProfile.getRequesterMsisdn());

            } else {
                handleError(CCEStatus.REQUESTER_LOCATION_NOT_ALLOWED, "state=" + getCallState());
            }
        } else if (getCallState() == HLRValidation.INQUIRY_B) {
            // Validate B location
            ccProfile.setPayerLocInfo(locInfo);
            ccProfile.setPayerLocNum(locNum);
            ccProfile.setPayerMccMnc(mccmnc);
            ccProfile.setPayerLac(cid.getLocationAreaCode());
            ccProfile.setPayerCellId(cid.getCellId());
            ccProfile.setGmscGt(dbConfig.getMscGt(cid.getLocationAreaCode()));

            if (mccmnc.equals(dbConfig.get("CC.HOME_LAI_PREFIX"))) {
                sendATSI(ccProfile.getPayerMsisdn());
            } else {
                handleError(CCEStatus.PAYER_LOCATION_NOT_ALLOWED, "state=" + getCallState());
            }
        }
    }

    private void sendATSI(String msisdn) {
        System.out.println("Sending ATSI for " + msisdn);
        System.out.println("Set Calling partynumber : " + this.cceGt);

        try {
            SccpAddress destAddress = this.createSccpAddr(msisdn, 6);

            final MAPMAP_DialoguePDU pdu = new MAPMAP_DialoguePDU();
            final MAPMAP_OpenInfo openInfo = new MAPMAP_OpenInfo();
            pdu.setMap_open(openInfo);

            MAPDialog dialog = (MAPDialog) cginprovider.issueOpenRequest(
                    MAPApplicationContexts.anyTimeInfoHandlingContext_v3_ac,
                    cceSccpAddr, destAddress, 30000, pdu);

            ActivityContextInterface dialogACI = aciFactory.getActivityContextInterface(dialog);
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

            dialog.sendAnyTimeSubscriptionInterrogation(30000, atsiarg);
            dialog.sendDelimiter();
            System.out.println("ATSI sent");
            System.out.println("[sendATSI]: Send --> sendATSI To HLR" + atsiarg.toString());

        } catch (TooManyInvokesException e) {

            e.printStackTrace();
        } catch (ProtocolException e) {

            e.printStackTrace();
        }

    }

    public void onatsiResp(MAPAnyTimeSubscriptionInterrogationResultEvent event, ActivityContextInterface aci) {
        System.out.println("Receive ATSI response: " + event.getResult().toString());
        CollectCallProfile ccProfile = getCcProfile();

        MAPAnyTimeSubscriptionInterrogationRes atsiRes = event.getResult();
        boolean isOffline = false;
        String ocsiGt = "";


        if (atsiRes.hasCamel_SubscriptionInfo()) {
            MAPCAMEL_SubscriptionInfo subsInfo = atsiRes.getCamel_SubscriptionInfo();
            if (subsInfo.hasO_CSI()) {
                ocsiGt = subsInfo.getO_CSI().getO_BcsmCamelTDPDataList()[0].getGsmSCF_Address().getAddress();
            } else {
                isOffline = true;
            }
        }

        if (getCallState() == HLRValidation.INQUIRY_A) {
            // Validate A Profile
            ccProfile.setOfflineRequester(isOffline);

            if (atsiRes.hasCallForwardingData()) {
                handleError(CCEStatus.REQUESTER_CALL_FORWARD_ACTIVE, "state=" + getCallState());
            } else if (atsiRes.hasCallBarringData()) {
                MAPCallBarringData barring = atsiRes.getCallBarringData();
                MAPExt_CallBarInfo barringInfo = new MAPExt_CallBarInfo();
                barringInfo.setCallBarringFeatureList(barring.getCallBarringFeatureList());
                SSCode ssCode = barringInfo.getSs_Code();
                if (ssCode == SSCode.BARRING_OF_INCOMING_CALLS) {
                    handleError(CCEStatus.REQUESTER_CALL_BARRED, "state=" + getCallState());
                }
            } else {
                setCallState(HLRValidation.INQUIRY_B);
                sendATI(ccProfile.getPayerMsisdn());
            }

        } else if (getCallState() == HLRValidation.INQUIRY_B) {
            // Validate B Profile
            ccProfile.setOfflinePayer(isOffline);
            if (!ccProfile.isScbPayer()) {
                ccProfile.setPayerOcsGt(ocsiGt);
            }

            //if (atsiRes.hasCallForwardingData()) {
            //    handleError(CCEStatus.PAYER_CALL_FORWARD_ACTIVE, "state=" + getCallState());
            if (atsiRes.hasCallBarringData()) {
                MAPCallBarringData barring = atsiRes.getCallBarringData();
                MAPExt_CallBarInfo barringInfo = new MAPExt_CallBarInfo();
                barringInfo.setCallBarringFeatureList(barring.getCallBarringFeatureList());
                SSCode ssCode = barringInfo.getSs_Code();
                if (ssCode == SSCode.BARRING_OF_INCOMING_CALLS ||
                        ssCode == SSCode.BARRING_OF_OUTGOING_CALLS) {
                    handleError(CCEStatus.PAYER_CALL_BARRED, "state=" + getCallState());
                }
            } else if (isOffline && dbConfig.get("CC.ALLOW_OFFLINE_PAYER").equals("0")) {
                handleError(CCEStatus.OFFLINE_PAYER_NOT_ALLOWED, "state=" + getCallState());
            } else {
                sendSRISM(ccProfile.getPayerMsisdn());
            }
        }
    }

    private void sendSRISM(String msisdn) {
        System.out.println("Sending SRISM for " + msisdn);

        try {
            SccpAddress destAddress = this.createSccpAddr(msisdn, 6);

            final MAPMAP_DialoguePDU pdu = new MAPMAP_DialoguePDU();
            final MAPMAP_OpenInfo openInfo = new MAPMAP_OpenInfo();
            pdu.setMap_open(openInfo);

            MAPDialog dialog = (MAPDialog) cginprovider.issueOpenRequest(
                    MAPApplicationContexts.shortMsgGatewayContext_v3_ac,
                    cceSccpAddr, destAddress, 30000, pdu);

            ActivityContextInterface dialogACI = aciFactory.getActivityContextInterface(dialog);
            dialogACI.attach(getSbbLocalObject());

            MAPRoutingInfoForSM_Arg srismarg = new MAPRoutingInfoForSM_Arg();
            srismarg.setServiceCentreAddress(this.cceAddress);
            srismarg.setSm_RP_PRI(false);

            AddressString ms = new AddressString();
            ms.setAddress(msisdn);
            ms.setNature(AddressString.Nature.INTERNATIONAL);
            ms.setNumberingPlan(AddressString.NumberingPlan.ISDN);
            srismarg.setMsisdn(ms);

            dialog.sendSendRoutingInfoForSM(30000, srismarg);
            dialog.sendDelimiter();
            System.out.println("SRI-SM sent");
            System.out.println("[SRI-SM]: Send --> SRI-SM To HLR" + srismarg.toString());

        } catch (TooManyInvokesException e) {
            e.printStackTrace();
        } catch (ProtocolException e) {

            e.printStackTrace();
        }
    }

    public void onsrismResp(MAPSendRoutingInfoForSMResultEvent event, ActivityContextInterface aci) {
        System.out.println("Receive SRISM response: " + event.getResult().toString());

        MAPRoutingInfoForSM_Res response = event.getResult();
        String imsi = response.getImsi().getAddress();
        CollectCallProfile profile = getCcProfile();
        profile.setPayerImsi(imsi);

        tLogger.updateLog(profile);

        // Continue Collect Call processing
        // OnlineCallFlow .initCollectCall(getCcProfile());

    }

    public void onOperationError(OperationErrorEvent error, ActivityContextInterface aci) {
        System.out.println("Receive Operation Error: " + error.getName() + " - " + error.toString());
    }

    public void onOpenConf(DialogOpenAcceptEvent event, ActivityContextInterface aci) {
        System.out.println("=====onOpenConf()========");
    }

    private void handleError(int status, String msg) {

        CollectCallProfile ccProfile = this.getCcProfile();

        String errMsgId = "";
        if (status == CCEStatus.ATI_ERROR) {
            errMsgId = "SMS.ATI_ERROR";
        } else if (status == CCEStatus.ATSI_ERROR) {
            errMsgId = "SMS.ATSI_ERROR";
        } else if (status == CCEStatus.REQUESTER_LOCATION_NOT_ALLOWED) {
            errMsgId = "SMS.REQUESTER_LOCATION_NOT_ALLOWED";
        } else if (status == CCEStatus.REQUESTER_CALL_BARRED) {
            errMsgId = "SMS.REQUESTER_CALL_BARRED";
        } else if (status == CCEStatus.REQUESTER_CALL_FORWARD_ACTIVE) {
            errMsgId = "SMS.REQUESTER_CALL_FORWARD_ACTIVE";
        } else if (status == CCEStatus.REQUESTER_BALANCE_INSUFFICIENT) {
            errMsgId = "SMS.REQUESTER_BALANCE_INSUFFICIENT";
        } else if (status == CCEStatus.PAYER_LOCATION_NOT_ALLOWED) {
            errMsgId = "SMS.PAYER_LOCATION_NOT_ALLOWED";
        } else if (status == CCEStatus.PAYER_CALL_BARRED) {
            errMsgId = "SMS.PAYER_CALL_BARRED";
        } else if (status == CCEStatus.PAYER_CALL_FORWARD_ACTIVE) {
            errMsgId = "SMS.PAYER_CALL_FORWARD_ACTIVE";
        } else if (status == CCEStatus.OFFLINE_PAYER_NOT_ALLOWED) {
            errMsgId = "SMS.OFFLINE_PAYER_NOT_ALLOWED";
        } else if (status == CCEStatus.IN_GW_ERROR) {
            errMsgId = "SMS.IN_GW_ERROR";
        } else {
            errMsgId = "SMS.UNKNOWN_ERROR";
        }

        String errMessage = "";
        if (ccProfile.isEnglishRequester()) {
            errMessage = dbConfig.getLocEnglish(errMsgId);
        } else {
            errMessage = dbConfig.getLocBahasa(errMsgId);
        }

        System.out.println(errMessage);

        tLogger.updateStatus(getCcProfile().getTransId(), status, msg);
        getHttpcceClient().sendSMS(ccProfile.getRequesterMsisdn(), errMessage);
    }

    public abstract ChildRelation getHttpCCEClientChildRelation();

    public abstract HTTPCCELocalInterface getHttpcceClient();

    public abstract void setHttpcceClient(HTTPCCELocalInterface httpccelocalinterface);
}


