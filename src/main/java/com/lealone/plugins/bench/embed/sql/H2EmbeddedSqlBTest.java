/*
 * Copyright Lealone Database Group.
 * Licensed under the Server Side Public License, v 1.
 * Initial Developer: zhh
 */
package com.lealone.plugins.bench.embed.sql;

import java.sql.Connection;

import com.lealone.plugins.bench.start.H2BenchTestServer;

public class H2EmbeddedSqlBTest extends EmbeddedSqlBenchTest {

    public static void main(String[] args) throws Exception {
        H2BenchTestServer.setH2Properties();
        new H2EmbeddedSqlBTest().run();
    }

    @Override
    protected Connection getConnection() throws Exception {
        return getEmbeddedH2Connection();
    }
}
