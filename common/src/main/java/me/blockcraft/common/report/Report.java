package me.blockcraft.common.report;

public record Report(int id, String reporter, String reported, String reason, String server, String status) {

    public Report withStatus(String newStatus) {
        return new Report(id, reporter, reported, reason, server, newStatus);
    }
}
