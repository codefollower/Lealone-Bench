/*
 * Copyright Lealone Database Group.
 * Licensed under the Server Side Public License, v 1.
 * Initial Developer: zhh
 */
package com.lealone.plugins.bench.cs;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.lealone.db.ConnectionSetting;
import com.lealone.db.Constants;

import com.lealone.plugins.bench.BenchTest;
import com.lealone.plugins.bench.DbType;

public abstract class ClientServerBTest extends BenchTest {

    static {
        System.setProperty("lealone.server.cached.objects", "10000000");
        System.setProperty("h2.serverCachedObjects", "10000000");
    }

    protected DbType dbType;
    protected boolean disableLealoneQueryCache = true;

    protected int benchTestLoop = 10;
    protected int outerLoop = 15;
    protected int innerLoop = 100;
    protected int sqlCountPerInnerLoop = 500;
    protected boolean printInnerLoopResult;
    protected boolean async;
    protected boolean autoCommit = true;
    protected boolean batch;
    protected boolean prepare;
    protected boolean reinit = true;
    protected String[] sqls;

    protected AtomicInteger id = new AtomicInteger();
    protected Random random = new Random();

    public void start() {
        String name = getBTestName();
        DbType dbType;
        if (name.startsWith("AsyncLealone")) {
            dbType = DbType.LEALONE;
            async = true;
        } else if (name.startsWith("Lealone")) {
            dbType = DbType.LEALONE;
            async = false;
        } else if (name.startsWith("H2")) {
            dbType = DbType.H2;
        } else if (name.startsWith("MySQL")) {
            dbType = DbType.MYSQL;
        } else if (name.startsWith("Pg")) {
            dbType = DbType.POSTGRESQL;
        } else if (name.startsWith("LM")) {
            dbType = DbType.LM;
        } else if (name.startsWith("LP")) {
            dbType = DbType.LP;
        } else {
            throw new RuntimeException("Unsupported BTestName: " + name);
        }
        this.dbType = dbType;
        try {
            run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void run() throws Exception {
        for (int i = 0; i < benchTestLoop; i++) {
            if (reinit || i == 0)
                init();
            run(threadCount);
        }
    }

    protected void run(int threadCount) throws Exception {
        Connection[] conns = new Connection[threadCount];
        for (int i = 0; i < threadCount; i++) {
            Connection conn = getConnection();
            conns[i] = conn;
        }

        if (warmUpEnabled()) {
            for (int i = 0; i < 2; i++) {
                run(threadCount, conns, true);
            }
        }

        for (int i = 0; i < outerLoop; i++) {
            run(threadCount, conns, false);
        }

        for (int i = 0; i < threadCount; i++) {
            close(conns[i]);
        }
    }

    protected boolean warmUpEnabled() {
        return true;
    }

    protected void run(int threadCount, Connection[] conns, boolean warmUp) throws Exception {
        ClientServerBTestThread[] threads = new ClientServerBTestThread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            threads[i] = createBTestThread(i, conns[i]);
        }
        long totalTime = 0;
        for (int i = 0; i < threadCount; i++) {
            threads[i].setCloseConn(false);
            threads[i].start();
        }
        for (int i = 0; i < threadCount; i++) {
            threads[i].join();
            totalTime += threads[i].getTotalTime();
        }
        System.out.println(
                getBTestName() + " sql count: " + (threadCount * innerLoop * sqlCountPerInnerLoop)
                        + ", total time: " + TimeUnit.NANOSECONDS.toMillis(totalTime / threadCount)
                        + " ms" + (warmUp ? " (***WarmUp***)" : ""));
    }

    protected ClientServerBTestThread createBTestThread(int id, Connection conn) {
        throw new RuntimeException("not supports");
    }

    protected abstract class ClientServerBTestThread extends Thread {

        protected Connection conn;
        protected Statement stmt;
        protected PreparedStatement ps;
        protected boolean closeConn = true;
        protected long startTime;
        protected long endTime;

        public ClientServerBTestThread(int id, Connection conn) {
            super(getBTestName() + "Thread-" + id);
            this.conn = conn;
            try {
                this.stmt = conn.createStatement();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }

        public long getTotalTime() {
            return endTime - startTime;
        }

        public void setCloseConn(boolean closeConn) {
            this.closeConn = closeConn;
        }

        public void prepareStatement(String sql) {
            try {
                ps = conn.prepareStatement(sql);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }

        protected abstract String nextSql();

        protected void prepare() throws Exception {
        }

        protected void execute() throws Exception {
        }

        @Override
        public void run() {
            try {
                execute();
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                close(stmt, ps);
                if (closeConn)
                    close(conn);
            }
        }
    }

    protected Connection getConnection() throws Exception {
        switch (dbType) {
        case H2:
            return getH2Connection();
        case MYSQL:
            return getMySQLConnection();
        case POSTGRESQL:
            return getPgConnection();
        case LEALONE: {
            Connection conn = getLealoneConnection(async);
            if (disableLealoneQueryCache) {
                disableLealoneQueryCache(conn);
            }
            return conn;
        }
        case LM: {
            Connection conn = getLMConnection();
            if (disableLealoneQueryCache) {
                disableLealoneQueryCache(conn);
            }
            return conn;
        }
        default:
            throw new RuntimeException();
        }
    }

    protected String getBTestName() {
        return getClass().getSimpleName();
    }

    protected static void close(AutoCloseable... acArray) {
        for (AutoCloseable ac : acArray) {
            if (ac != null) {
                try {
                    ac.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static Connection getLMConnection() throws Exception {
        String db = "mysql";
        String user = "root";
        String password = "";
        int port = 9310;
        return getMySQLConnection(db, user, password, port);
    }

    public static Connection getMySQLConnection() throws Exception {
        String db = "test";
        String user = "test";
        String password = "test";
        int port = 3306;
        return getMySQLConnection(db, user, password, port);
    }

    public static Connection getMySQLConnection(String db, String user, String password, int port)
            throws Exception {
        String url = "jdbc:mysql://localhost:" + port + "/" + db;

        Properties info = new Properties();
        info.put("user", user);
        info.put("password", password);
        // info.put("holdResultsOpenOverStatementClose","true");
        // info.put("allowMultiQueries","true");

        info.put("useServerPrepStmts", "true");
        // info.put("cachePrepStmts", "true");
        info.put("rewriteBatchedStatements", "true");
        // info.put("useCompression", "true");
        info.put("serverTimezone", "GMT");

        Connection conn = DriverManager.getConnection(url, info);
        // conn.setAutoCommit(true);
        return conn;
    }

    public static Connection getPgConnection() throws Exception {
        String url = "jdbc:postgresql://localhost:" + 5432 + "/test";
        return getConnection(url, "test", "test");
    }

    public static Connection getH2Connection() throws Exception {
        String url = "jdbc:h2:tcp://localhost:9092/mydb";
        return getConnection(url, "sa", "");
    }

    public static String getLealoneUrl() {
        String url = "jdbc:lealone:tcp://localhost:" + Constants.DEFAULT_TCP_PORT + "/lealone";
        url += "?" + ConnectionSetting.NETWORK_TIMEOUT + "=" + Integer.MAX_VALUE;
        return url;
    }

    public static Connection getLealoneConnection(boolean async) throws Exception {
        String url = getLealoneUrl();
        url += "&" + ConnectionSetting.IS_SHARED + "=false";
        url += "&" + ConnectionSetting.NET_CLIENT_COUNT + "=16";
        url += "&" + ConnectionSetting.NET_FACTORY_NAME + "=" + (async ? "nio" : "bio");
        return getConnection(url, "root", "");
    }

    public static Connection getLealoneSharedConnection(int maxSharedSize) throws Exception {
        String url = getLealoneUrl();
        url += "&" + ConnectionSetting.IS_SHARED + "=true";
        url += "&" + ConnectionSetting.MAX_SHARED_SIZE + "=" + maxSharedSize;
        return getConnection(url, "root", "");
    }

    public static void disableLealoneQueryCache(Connection conn) {
        try {
            Statement statement = conn.createStatement();
            statement.executeUpdate("set QUERY_CACHE_SIZE 0");
            // statement.executeUpdate("set OPTIMIZE_REUSE_RESULTS 0");
            statement.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static Connection getConnection(String url, String user, String password) throws Exception {
        Properties info = new Properties();
        info.put("user", user);
        info.put("password", password);
        return DriverManager.getConnection(url, info);
    }
}
