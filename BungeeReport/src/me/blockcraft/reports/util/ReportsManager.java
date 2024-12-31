package me.blockcraft.reports.util;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

public class ReportsManager {
    public static void addReport(final String reporter, final String reported, final String Grund, final String Server) {
        MySQL.update("INSERT INTO Reports (Reportername, Reportedname, Grund, Server, Status) VALUES ('" + reporter + "','" + reported + "','" + Grund + "','" + Server + "','Offen')");
    }

    public static void delReport(final Integer ID) {
        MySQL.update("DELETE FROM Reports WHERE ID = '" + ID + "'");
    }

    public static String getReport(final Integer ID, final String spalte) {
        final ResultSet rs = MySQL.getResult("SELECT " + spalte + " FROM Reports WHERE ID='" + ID + "'");
        try {
            if (rs != null && rs.next()) {
                return rs.getString(spalte);
            }
        } catch (final SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static boolean ReportExists(final String reporter, final String reported) {
        final ResultSet rs = MySQL.getResult("SELECT * FROM Reports WHERE Reportedname='" + reported + "' AND Reportername='" + reporter + "'");
        try {
            if (rs != null && rs.next()) {
                return rs.getString("Status") != null;
            }
        } catch (final SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static Integer anzReports(final String name) {
        final ResultSet rs = MySQL.getResult("SELECT COUNT(*) AS anzahl FROM Reports WHERE Reportedname='" + name + "'");
        try {
            if (rs != null && rs.next()) {
                return rs.getInt("anzahl");
            }
        } catch (final SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static ArrayList<String> getReportLists(final String spalte) {
        final ResultSet rs = MySQL.getResult("SELECT * FROM Reports");
        final ArrayList<String> a = new ArrayList<>();
        try {
            if (rs != null) {
                while (rs.next()) {
                    a.add(rs.getString(spalte));
                }
            }
        } catch (final SQLException e) {
            e.printStackTrace();
        }
        return a;
    }
}
