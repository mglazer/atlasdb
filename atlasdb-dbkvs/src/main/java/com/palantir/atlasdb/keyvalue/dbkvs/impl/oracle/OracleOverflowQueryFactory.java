package com.palantir.atlasdb.keyvalue.dbkvs.impl.oracle;

import java.util.Collection;
import java.util.List;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.palantir.atlasdb.keyvalue.dbkvs.impl.FullQuery;
import com.palantir.atlasdb.keyvalue.dbkvs.impl.OverflowMigrationState;
import com.palantir.atlasdb.keyvalue.dbkvs.impl.OverflowValue;
import com.palantir.nexus.db.sql.OracleStructArray;

public class OracleOverflowQueryFactory extends OracleQueryFactory {
    private final OverflowMigrationState migrationState;

    public OracleOverflowQueryFactory(String tableName,
                                      OverflowMigrationState migrationState) {
        super(tableName);
        this.migrationState = migrationState;
    }

    @Override
    protected String getValueSubselect(String tableAlias, boolean includeValue) {
        return includeValue ? ", " + tableAlias + ".val, " + tableAlias + ".overflow " : " ";
    }

    @Override
    public boolean hasOverflowValues() {
        return true;
    }

    @Override
    public Collection<FullQuery> getOverflowQueries(Collection<OverflowValue> overflowIds) {
        List<Object[]> oraRows = Lists.newArrayListWithCapacity(overflowIds.size());
        for (OverflowValue overflowId : overflowIds) {
            oraRows.add(new Object[] { null, null, overflowId.id });
        }
        OracleStructArray arg = new OracleStructArray("PT_MET_CELL_TS", "PT_MET_CELL_TS_TABLE", oraRows);
        switch (migrationState) {
        case UNSTARTED:
            return ImmutableList.of(getOldOverflowQuery(arg));
        case IN_PROGRESS:
            return ImmutableList.of(getOldOverflowQuery(arg), getNewOverflowQuery(arg));
        case FINISHING: // fall through
        case FINISHED:
            return ImmutableList.of(getNewOverflowQuery(arg));
        default:
            throw new EnumConstantNotPresentException(OverflowMigrationState.class, migrationState.name());
        }
    }

    private FullQuery getOldOverflowQuery(OracleStructArray arg) {
        String query =
                " /*SQL_MET_SELECT_OVERFLOW */ " +
                " SELECT /*+ USE_NL(t o) LEADING(t o) INDEX(o pk_pt_metropolis_overflow) */ " +
                "   o.id, o.val " +
                " FROM pt_metropolis_overflow o, TABLE(CAST(? AS PT_MET_CELL_TS_TABLE)) t " +
                " WHERE t.max_ts = o.id ";
        return new FullQuery(query).withArg(arg);
    }

    private FullQuery getNewOverflowQuery(OracleStructArray arg) {
        String query =
                " /*SQL_MET_SELECT_OVERFLOW (" + tableName + ") */ " +
                " SELECT /*+ USE_NL(t o) LEADING(t o) INDEX(o pk_pt_mo_" + tableName + ") */ " +
                "   o.id, o.val " +
                " FROM pt_mo_" + tableName + " o, TABLE(CAST(? AS PT_MET_CELL_TS_TABLE)) t " +
                " WHERE t.max_ts = o.id ";
        return new FullQuery(query).withArg(arg);
    }
}
