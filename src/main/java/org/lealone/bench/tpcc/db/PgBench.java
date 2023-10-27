/*
 * Copyright Lealone Database Group.
 * Licensed under the Server Side Public License, v 1.
 * Initial Developer: zhh
 */
package org.lealone.bench.tpcc.db;

import org.lealone.bench.tpcc.bench.TpccBench;

public class PgBench {
    public static void main(String[] args) {
        System.setProperty("db.config", "postgresql/db.properties");
        TpccBench.main(args);
    }
}
