package mapati;

import java.io.Serializable;

import com.opencloud.slee.resources.cgin.map.MAPLocationInformation;
import com.opencloud.slee.resources.in.datatypes.cc.LocationNumber;
//import com.nsn.bso.cce.log.common.Transaction;

public class CollectCallProfile implements Serializable {

    private long transId;

    private String requesterMsisdn;
    private String requesterMccMnc;
    private int requesterLac;
    private int requesterCellId;
    private String requesterOcsGt;
    private MAPLocationInformation requesterLocInfo;
    private LocationNumber requesterLocNum;
    private boolean roamingRequester = false;
    private boolean offlineRequester = false;
    private boolean englishRequester = false;
    private boolean whitelist = false;
    private String payerMsisdn;
    private int payerServiceKey;
    private String payerImsi;
    private String payerOcsGt;
    private String payerMccMnc;
    private int payerLac;
    private int payerCellId;
    private MAPLocationInformation payerLocInfo;
    private LocationNumber payerLocNum;
    private boolean offlinePayer = false;
    private boolean englishPayer = false;
    private boolean scbPayer = false;
    private String gmscGt = "";
    private boolean requestCharged = false;

    public CollectCallProfile(String transid, String requester_msisdn,
            String payer_msisdn) {
        this.requesterMsisdn = requester_msisdn;
        this.payerMsisdn = payer_msisdn;
    }

    public CollectCallProfile(String requester_msisdn, String payer_msisdn) {
        this.requesterMsisdn = requester_msisdn;
        this.payerMsisdn = payer_msisdn;
    }

    public boolean isEnglishPayer() {
        return englishPayer;
    }

    public void setEnglishPayer(boolean englishPayer) {
        this.englishPayer = englishPayer;
    }

    public boolean isEnglishRequester() {
        return englishRequester;
    }

    public void setEnglishRequester(boolean englishRequester) {
        this.englishRequester = englishRequester;
    }

    public boolean isOfflinePayer() {
        return offlinePayer;
    }

    public void setOfflinePayer(boolean offlinePayer) {
        this.offlinePayer = offlinePayer;
    }

    public boolean isOfflineRequester() {
        return offlineRequester;
    }

    public void setOfflineRequester(boolean offlineRequester) {
        this.offlineRequester = offlineRequester;
    }

    public int getPayerCellId() {
        return payerCellId;
    }

    public void setPayerCellId(int payerCellId) {
        this.payerCellId = payerCellId;
    }

    public String getPayerImsi() {
        return payerImsi;
    }

    public void setPayerImsi(String payerImsi) {
        this.payerImsi = payerImsi;
    }

    public int getPayerLac() {
        return payerLac;
    }

    public void setPayerLac(int payerLac) {
        this.payerLac = payerLac;
    }

    public MAPLocationInformation getPayerLocInfo() {
        return payerLocInfo;
    }

    public void setPayerLocInfo(MAPLocationInformation payerLocInfo) {
        this.payerLocInfo = payerLocInfo;
    }

    public String getPayerMccMnc() {
        return payerMccMnc;
    }

    public void setPayerMccMnc(String payerMccMnc) {
        this.payerMccMnc = payerMccMnc;
    }

    public String getPayerMsisdn() {
        return payerMsisdn;
    }

    public void setPayerMsisdn(String payerMsisdn) {
        this.payerMsisdn = payerMsisdn;
    }

    public String getPayerOcsGt() {
        return payerOcsGt;
    }

    public void setPayerOcsGt(String payerOcsGt) {
        this.payerOcsGt = payerOcsGt;
    }

    public int getPayerServiceKey() {
        return payerServiceKey;
    }

    public void setPayerServiceKey(int payerServiceKey) {
        this.payerServiceKey = payerServiceKey;
    }

    public int getRequesterCellId() {
        return requesterCellId;
    }

    public void setRequesterCellId(int requesterCellId) {
        this.requesterCellId = requesterCellId;
    }

    public int getRequesterLac() {
        return requesterLac;
    }

    public void setRequesterLac(int requesterLac) {
        this.requesterLac = requesterLac;
    }

    public MAPLocationInformation getRequesterLocInfo() {
        return requesterLocInfo;
    }

    public void setRequesterLocInfo(MAPLocationInformation requesterLocInfo) {
        this.requesterLocInfo = requesterLocInfo;
    }

    public String getRequesterMccMnc() {
        return requesterMccMnc;
    }

    public void setRequesterMccMnc(String requesterMccMnc) {
        this.requesterMccMnc = requesterMccMnc;
    }

    public String getRequesterMsisdn() {
        return requesterMsisdn;
    }

    public void setRequesterMsisdn(String requesterMsisdn) {
        this.requesterMsisdn = requesterMsisdn;
    }

    public boolean isRoamingRequester() {
        return roamingRequester;
    }

    public void setRoamingRequester(boolean roamingRequester) {
        this.roamingRequester = roamingRequester;
    }

    public boolean isWhitelist() {
        return whitelist;
    }

    public void setWhitelist(boolean whitelist) {
        this.whitelist = whitelist;
    }

    public long getTransId() {
        return transId;
    }

    public void setTransId(long transId) {
        this.transId = transId;
    }

    public String getGmscGt() {
        return gmscGt;
    }

    public void setGmscGt(String gmscGt) {
        this.gmscGt = gmscGt;
    }

    public String getRequesterOcsGt() {
        return requesterOcsGt;
    }

    public void setRequesterOcsGt(String requesterOcsGt) {
        this.requesterOcsGt = requesterOcsGt;
    }

    public boolean isRequestCharged() {
        return requestCharged;
    }

    public void setRequestCharged(boolean requestCharged) {
        this.requestCharged = requestCharged;
    }

    public boolean isScbPayer() {
        return scbPayer;
    }

    public void setScbPayer(boolean scbPayer) {
        this.scbPayer = scbPayer;
    }

    public LocationNumber getPayerLocNum() {
        return payerLocNum;
    }

    public void setPayerLocNum(LocationNumber payerLocNum) {
        this.payerLocNum = payerLocNum;
    }

    public LocationNumber getRequesterLocNum() {
        return requesterLocNum;
    }

    public void setRequesterLocNum(LocationNumber requesterLocNum) {
        this.requesterLocNum = requesterLocNum;
    }

    
    
}
