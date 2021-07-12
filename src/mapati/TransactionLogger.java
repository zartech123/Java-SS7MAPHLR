/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package mapati;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.text.SimpleDateFormat;

import java.util.Date;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

/**
 *
 * @author ida38011
 */
public class TransactionLogger {

    private static TransactionLogger instance = new TransactionLogger();
    private static DataSource ds;
    private static int counter = 0;
    private SimpleDateFormat sdf = new SimpleDateFormat("yyMMddHHmmss");

    private TransactionLogger() {

        Context env;
        try {
            env = (Context) new InitialContext().lookup("java:comp/env");
            ds = (DataSource) env.lookup("jdbc/OracleDB");
        } catch (NamingException ex) {
            System.out.println("[CCE] TransactionLogger: init error");
            ex.printStackTrace();
        }
    }
    
    private static synchronized int getCounter() {
        
        counter++;
        if (counter == 1000) {
            counter = 0;
        }
        return counter;
    }

    public static TransactionLogger getInstance() {
        return instance;
    }

    public long createLog(CollectCallProfile profile) {

        long transid = Long.parseLong(sdf.format(new Date()))*1000 + getCounter();
        try {
            Connection conn = ds.getConnection();

            PreparedStatement pstmt = conn.prepareStatement(
                    "insert into CC_TRANSACTION (trans_id, trans_timestamp, requester_msisdn, payer_msisdn, status_code, call_duration, gmsc_id) " +
                    "values(?,sysdate,?,?,0,0,?)");
            pstmt.setLong(1, transid);
            pstmt.setString(2, profile.getRequesterMsisdn());
            pstmt.setString(3, profile.getPayerMsisdn());
            pstmt.setString(4, profile.getGmscGt());
            pstmt.executeUpdate();
            pstmt.close();
            System.out.println("[CCE] TransactionLogger: log created");

        } catch (SQLException ex) {
            System.out.println("[CCE] TransactionLogger: createLog failed");
            ex.printStackTrace();
        }

        profile.setTransId(transid);
        return transid;

    }

    public void updateLog(CollectCallProfile profile) {
        try {
            Connection conn = ds.getConnection();

            PreparedStatement pstmt = conn.prepareStatement(
                    "update CC_TRANSACTION set " +
                    "gmsc_id = ?, " +
                    "requester_vlr_id = ?, " +
                    "payer_vlr_id = ?, " +
                    "requester_ocs_id = ?, " +
                    "payer_ocs_id = ? " +
                    "where trans_id = ?");
            pstmt.setString(1, profile.getGmscGt());
            pstmt.setString(2, profile.getRequesterMccMnc() + "."
                    + profile.getRequesterLac() + "." + profile.getRequesterCellId());
            pstmt.setString(3, profile.getPayerMccMnc() + "."
                    + profile.getPayerLac() + "." + profile.getPayerCellId());
            pstmt.setString(4, profile.getRequesterOcsGt());
            pstmt.setString(5, profile.getPayerOcsGt());
            pstmt.setLong(6, profile.getTransId());
            pstmt.executeUpdate();
            pstmt.close();
            System.out.println("[CCE] TransactionLogger: log updated");

        } catch (SQLException ex) {
            System.out.println("[CCE] TransactionLogger: updateLog failed");
            ex.printStackTrace();
        }
    }

    public void updateAnnoStartTime(long tid) {
        try {
            Connection conn = ds.getConnection();

            PreparedStatement pstmt = conn.prepareStatement(
                    "update CC_TRANSACTION set announcement_start_time = sysdate " +
                    "where trans_id = ?");
            pstmt.setLong(1, tid);
            pstmt.executeUpdate();
            pstmt.close();
            System.out.println("[CCE] TransactionLogger: anno start time updated");

        } catch (SQLException ex) {
            System.out.println("[CCE] TransactionLogger: update anno start time failed");
            ex.printStackTrace();
        }
    }

    public void updateSuccessStatus(long tid, int chargeResult) {
        try {
            Connection conn = ds.getConnection();

            PreparedStatement pstmt = conn.prepareStatement(
                    "update CC_TRANSACTION set call_start_time=sysdate, request_charge_status=?, status_code=100 " +
                    "where trans_id = ?");
            pstmt.setLong(1, chargeResult);
            pstmt.setLong(2, tid);
            pstmt.executeUpdate();
            pstmt.close();
            System.out.println("[CCE] TransactionLogger: call_start_time updated");

        } catch (SQLException ex) {
            System.out.println("[CCE] TransactionLogger: update call_start_time failed");
            ex.printStackTrace();
        }

    }

    public void updateCallEndTime(long tid) {
        try {
            Connection conn = ds.getConnection();

            PreparedStatement pstmt = conn.prepareStatement(
                    "update CC_TRANSACTION set call_end_time = sysdate, " +
                    "call_duration = decode(call_start_time, null, 0, trunc((call_end_time - sysdate)*24*60*60)) " +
                    "where trans_id = ?");
            pstmt.setLong(1, tid);
            pstmt.executeUpdate();
            pstmt.close();
            System.out.println("[CCE] TransactionLogger: call_end_time updated");

        } catch (SQLException ex) {
            System.out.println("[CCE] TransactionLogger: update call_end_time failed");
            ex.printStackTrace();
        }
    }

    public void updateStatus(long tid, int status, String msg) {
        try {
            Connection conn = ds.getConnection();

            PreparedStatement pstmt = conn.prepareStatement(
                    "update CC_TRANSACTION set status_code=?, message=? " +
                    "where trans_id = ?");
            pstmt.setInt(1, status);
            if (msg.length() > 200) {
                msg = msg.substring(0,200);
            }
            pstmt.setString(2, msg);
            pstmt.setLong(3, tid);

            pstmt.executeUpdate();
            pstmt.close();
            System.out.println("[CCE] TransactionLogger: status updated");

        } catch (SQLException ex) {
            System.out.println("[CCE] TransactionLogger: update status failed");
            ex.printStackTrace();
        }

    }
}
