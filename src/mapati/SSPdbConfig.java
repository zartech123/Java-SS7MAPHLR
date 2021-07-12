package mapati;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.HashSet;
import java.util.HashMap;
import java.util.ArrayList;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

public class SSPdbConfig {

    private final static int QUERY_TIMEOUT = 60000;
    private static SSPdbConfig instance = new SSPdbConfig();
    private long lastupdate = 0;
    private static DataSource ds;
    private Map<String, String> params = null;
    private Map<String, String> loc_english = null;
    private HashSet<String> vlr_prefix = new HashSet<String>();
    private Map<String, String> loc_bahasa = null;
    private ArrayList<LACGMSCMapping> lacGmsc = new ArrayList<LACGMSCMapping>();
    private Map<String, String> defaults = null;

    public boolean isLocationAllowed(String mccmnc) {
        return vlr_prefix.contains(mccmnc);
    }

    public Map<String, String> getParams() {
        return params;
    }

    public Map<String, String> getLocEnglish() {
        return loc_english;
    }

    public Map<String, String> getLocBahasa() {
        return loc_bahasa;
    }

    public void setParams(Map<String, String> params) {
        this.params = params;
    }

    public void setLocEnglish(Map<String, String> params) {
        this.loc_english = params;
    }

    public void setLocBahasa(Map<String, String> params) {
        this.loc_bahasa = params;
    }

    public static SSPdbConfig getInstance() {
        instance.loadData();
        return instance;
    }

    public void reLoad() {
        loadData();
    }

    private SSPdbConfig() {
        params = new HashMap<String, String>();
        loc_english = new HashMap<String, String>();
        loc_bahasa = new HashMap<String, String>();
        defaults = new HashMap<String, String>(1);
        defaults.put("scb.Author", "twibawa");
    }

    private synchronized void loadData() {
        long now = System.currentTimeMillis();
        if ((now - lastupdate) > 30000) { // greater than 30s
            Connection conn = null;
            try {
                try {
                    Context env = (Context) new InitialContext().lookup("java:comp/env");
                    ds = (DataSource) env.lookup("jdbc/OracleDB");
                    conn = ds.getConnection();

                    PreparedStatement pstmt = conn.prepareStatement("SELECT PARAM_NAME,PARAM_VALUE FROM CONFIGURATION");
                    System.out.println("start config load");
                    pstmt.setQueryTimeout(QUERY_TIMEOUT);
                    ResultSet rs;
                    rs = pstmt.executeQuery();
                    params.clear();
                    while (rs.next()) {
                        params.put(rs.getString("PARAM_NAME"), rs.getString("PARAM_VALUE"));
                    }
                    pstmt.close();
                    rs.close();

                    Statement stmt = conn.createStatement();
                    rs = stmt.executeQuery("SELECT LOC_KEY,LOC_VALUE FROM CC_LOCALIZATION WHERE LOC_LANG='en'");

                    loc_english.clear();
                    while (rs.next()) {
                        loc_english.put(rs.getString("LOC_KEY"), rs.getString("LOC_VALUE"));
                    }
                    rs.close();
                    stmt.close();

                    stmt = conn.createStatement();
                    rs = stmt.executeQuery("SELECT LOC_KEY,LOC_VALUE FROM CC_LOCALIZATION WHERE LOC_LANG='bhs'");

                    loc_bahasa.clear();
                    while (rs.next()) {
                        loc_bahasa.put(rs.getString("LOC_KEY"), rs.getString("LOC_VALUE"));
                    }

                    rs.close();
                    stmt.close();

                    pstmt = conn.prepareStatement("SELECT VLR_PREFIX FROM CC_VLR");
                    pstmt.setQueryTimeout(QUERY_TIMEOUT);
                    rs = pstmt.executeQuery();
                    vlr_prefix.clear();
                    while (rs.next()) {
                        vlr_prefix.add(rs.getString("VLR_PREFIX"));
                    }

                    rs.close();
                    pstmt.close();


                    pstmt = conn.prepareStatement(
                            "SELECT LAC_FROM, LAC_TO, GMSC_GT FROM CC_LAC_GMSC a, CC_GMSC b where a.GMSC_ID = b.GMSC_ID");
                    pstmt.setQueryTimeout(QUERY_TIMEOUT);
                    rs = pstmt.executeQuery();
                    this.lacGmsc.clear();
                    while (rs.next()) {
                        lacGmsc.add(new LACGMSCMapping(rs.getInt(1), rs.getInt(2), rs.getString(3)));
                    }

                    rs.close();
                    pstmt.close();

                } catch (NamingException e) {
                    // TODO Auto-generated catch block
                    //log.error("Error getting datasource SSPDB: "+e.getMessage());
                    e.printStackTrace();
                }

            } catch (SQLException e1) {
                // TODO Auto-generated catch block
                //log.error("Error querying database: "+e1.getMessage());
                System.err.println("error execute");
                e1.printStackTrace();
            } finally {
                /*if (conn!=null)
                try {
                conn.close();
                } catch (SQLException e) {
                // TODO Auto-generated catch block
                //log.error("Error closing connection: "+e.getMessage());
                e.printStackTrace();
                }*/
            }
            lastupdate = now;
        }
    }

    public String get(String paramName) {
        String result = params.get(paramName);
        if ((result == null) || (result.equals(""))) {
            result = defaults.get(paramName);
        }
        return result;
    }

    public String getLocEnglish(String paramName) {
        String result = loc_english.get(paramName);
        if ((result == null) || (result.equals(""))) {
            result = defaults.get(paramName);
        }
        return result;
    }

    public String getLocBahasa(String paramName) {
        String result = loc_bahasa.get(paramName);
        if ((result == null) || (result.equals(""))) {
            result = defaults.get(paramName);
        }
        return result;
    }

    public synchronized Connection get() throws Exception {
        Connection conn = ds.getConnection();
        return conn;
    }

    public String getMscGt(int lac) {

        String mscGt = null;
        boolean found = false;
        for (LACGMSCMapping m : this.lacGmsc) {
            if (m.lac_from <= lac && m.lac_to >= lac) {
                found = true;
                mscGt = m.gmscGt;
            }
        }

        return mscGt;

    }
}
