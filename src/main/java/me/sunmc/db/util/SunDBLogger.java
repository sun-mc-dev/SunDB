package me.sunmc.db.util;

import java.util.logging.Logger;

public final class SunDBLogger {

    private static final Logger LOG = Logger.getLogger("SunDB");

    public static void info(String msg) {
        LOG.info(msg);
    }

    public static void warn(String msg) {
        LOG.warning(msg);
    }

    public static void error(String msg) {
        LOG.severe(msg);
    }

    private SunDBLogger() {
    }
}