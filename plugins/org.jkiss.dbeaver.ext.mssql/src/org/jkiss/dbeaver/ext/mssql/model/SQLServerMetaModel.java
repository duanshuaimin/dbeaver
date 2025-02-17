/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2015 Serge Rieder (serge@jkiss.org)
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License (version 2)
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */
package org.jkiss.dbeaver.ext.mssql.model;

import org.eclipse.core.runtime.IConfigurationElement;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.ext.generic.model.GenericDataSource;
import org.jkiss.dbeaver.ext.generic.model.GenericProcedure;
import org.jkiss.dbeaver.ext.generic.model.GenericTable;
import org.jkiss.dbeaver.ext.generic.model.meta.GenericMetaModel;
import org.jkiss.dbeaver.model.DBUtils;
import org.jkiss.dbeaver.model.exec.DBCQueryTransformProvider;
import org.jkiss.dbeaver.model.exec.DBCQueryTransformType;
import org.jkiss.dbeaver.model.exec.DBCQueryTransformer;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCPreparedStatement;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCResultSet;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCSession;
import org.jkiss.dbeaver.model.impl.jdbc.JDBCUtils;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;

import java.sql.SQLException;

/**
 * SQLServerMetaModel
 */
public class SQLServerMetaModel extends GenericMetaModel implements DBCQueryTransformProvider
{
    static final Log log = Log.getLog(SQLServerMetaModel.class);

    public SQLServerMetaModel(IConfigurationElement cfg) {
        super(cfg);
    }

    public String getViewDDL(DBRProgressMonitor monitor, GenericTable sourceObject) throws DBException {
        return extractSource(monitor, sourceObject.getDataSource(), sourceObject.getCatalog().getName(), sourceObject.getSchema().getName(), sourceObject.getName());
    }

    @Override
    public String getProcedureDDL(DBRProgressMonitor monitor, GenericProcedure sourceObject) throws DBException {
        return extractSource(monitor, sourceObject.getDataSource(), sourceObject.getCatalog().getName(), sourceObject.getSchema().getName(), sourceObject.getName());
    }

    private String extractSource(DBRProgressMonitor monitor, GenericDataSource dataSource, String catalog, String schema, String name) throws DBException {
        try (JDBCSession session = DBUtils.openMetaSession(monitor, dataSource, "Read view definition")) {
            String activeCatalog = dataSource.getSelectedEntityName();
            boolean switchDatabase = !catalog.equals(activeCatalog);
            if (switchDatabase) {
                try {
                    JDBCUtils.executeSQL(session, "USE " + catalog);
                } catch (SQLException e) {
                    log.warn("Can't switch to object catalog '" + catalog + "'");
                }
            }
            try {
                try (JDBCPreparedStatement dbStat = session.prepareStatement("sp_helptext '" + schema + "." + name + "'")) {
                    try (JDBCResultSet dbResult = dbStat.executeQuery()) {
                        StringBuilder sql = new StringBuilder();
                        while (dbResult.nextRow()) {
                            sql.append(dbResult.getString(1));
                        }
                        return sql.toString();
                    }
                }
            } finally {
                if (switchDatabase) {
                    try {
                        JDBCUtils.executeSQL(session, "USE " + activeCatalog);
                    } catch (SQLException e) {
                        log.warn("Can't switch back to active catalog '" + activeCatalog + "'");
                    }
                }
            }
        } catch (SQLException e) {
            throw new DBException(e, dataSource);
        }
    }

    @Nullable
    @Override
    public DBCQueryTransformer createQueryTransformer(DBCQueryTransformType type) {
        if (type == DBCQueryTransformType.RESULT_SET_LIMIT) {
            //return new QueryTransformerTop();
        }
        return null;
    }

}
