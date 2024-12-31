package me.blockcraft.reportsmenu.util;

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
            assert rs != null;
            if (rs.next()) {
                return rs.getString(spalte);
            }
        } catch (final SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static ArrayList<Integer> getIDs() {
        final ResultSet rs = MySQL.getResult("SELECT ID FROM Reports");
        final ArrayList<Integer> a = new ArrayList<>();
        try {
            while (true) {
                assert rs != null;
                if (!rs.next()) break;
                a.add(rs.getInt("ID"));
            }
        } catch (final SQLException e) {
            e.printStackTrace();
        }
        return a;
    }

    public static void updateStatus(final String status, final Integer ID) {
        MySQL.update("UPDATE Reports SET Status='" + status + "' WHERE ID='" + ID + "'");
    }

    public static ArrayList<String> getReportLists(final String spalte) {
        final ResultSet rs = MySQL.getResult("SELECT * FROM Reports");
        final ArrayList<String> a = new ArrayList<>();
        try {
            while (true) {
                assert rs != null;
                if (!rs.next()) break;
                a.add(rs.getString(spalte));
            }
        } catch (final SQLException e) {
            e.printStackTrace();
        }
        return a;
    }

    public static ArrayList<Integer> getUserIDs(final String target) {
        final ResultSet rs = MySQL.getResult("SELECT ID FROM Reports WHERE Reportedname='" + target + "'");
        final ArrayList<Integer> a = new ArrayList<>();
        try {
            while (true) {
                assert rs != null;
                if (!rs.next()) break;
                a.add(rs.getInt("ID"));
            }
        } catch (final SQLException e) {
            e.printStackTrace();
        }
        return a;
    }

    public static ArrayList<String> getUserReportLists(final String spalte, final String target) {
        final ResultSet rs = MySQL.getResult("SELECT * FROM Reports WHERE Reportedname='" + target + "'");
        final ArrayList<String> a = new ArrayList<>();
        try {
            while (true) {
                assert rs != null;
                if (!rs.next()) break;
                a.add(rs.getString(spalte));
            }
        } catch (final SQLException e) {
            e.printStackTrace();
        }
        return a;
    }

    public static Integer getReportAnz() {
        final ResultSet rs = MySQL.getResult("SELECT COUNT(*) AS anz FROM Reports");
        try {
            assert rs != null;
            if (rs.next()) {
                return rs.getInt("anz");
            }
        } catch (final SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static Integer anzReports(final String name) {
        final ResultSet rs = MySQL.getResult("SELECT COUNT(*) AS anzahl FROM Reports WHERE Reportedname='" + name + "'");
        try {
            assert rs != null;
            if (rs.next()) {
                return rs.getInt("anzahl");
            }
        } catch (final SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
}
