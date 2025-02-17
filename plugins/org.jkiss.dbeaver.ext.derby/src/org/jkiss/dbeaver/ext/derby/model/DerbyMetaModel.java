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
package org.jkiss.dbeaver.ext.derby.model;

import org.eclipse.core.runtime.IConfigurationElement;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.ext.generic.model.GenericDataSource;
import org.jkiss.dbeaver.ext.generic.model.GenericObjectContainer;
import org.jkiss.dbeaver.ext.generic.model.GenericSequence;
import org.jkiss.dbeaver.ext.generic.model.GenericTable;
import org.jkiss.dbeaver.ext.generic.model.meta.GenericMetaModel;
import org.jkiss.dbeaver.model.DBPErrorAssistant;
import org.jkiss.dbeaver.model.DBUtils;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCPreparedStatement;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCResultSet;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCSession;
import org.jkiss.dbeaver.model.impl.jdbc.JDBCUtils;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.utils.CommonUtils;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DerbyMetaModel
 */
public class DerbyMetaModel extends GenericMetaModel
{
    private Pattern ERROR_POSITION_PATTERN = Pattern.compile(" at line ([0-9]+), column ([0-9]+)\\.");

    public DerbyMetaModel(IConfigurationElement cfg) {
        super(cfg);
    }

    public String getViewDDL(DBRProgressMonitor monitor, GenericTable sourceObject) throws DBException {
        try (JDBCSession session = DBUtils.openMetaSession(monitor, sourceObject.getDataSource(), "Read view definition")) {
            return JDBCUtils.queryString(session, "SELECT v.VIEWDEFINITION from SYS.SYSVIEWS v,SYS.SYSTABLES t,SYS.SYSSCHEMAS s\n" +
                "WHERE v.TABLEID=t.TABLEID AND t.SCHEMAID=s.SCHEMAID AND s.SCHEMANAME=? AND t.TABLENAME=?", sourceObject.getContainer().getName(), sourceObject.getName());
        } catch (SQLException e) {
            throw new DBException(e, sourceObject.getDataSource());
        }
    }

/*
    @Override
    public String getProcedureDDL(DBRProgressMonitor monitor, GenericProcedure sourceObject) throws DBException {
        JDBCSession session = sourceObject.getDataSource().getDefaultContext(true).openSession(monitor, DBCExecutionPurpose.META, "Read procedure definition");
        try {
            return JDBCUtils.queryString(session, "SELECT pg_get_functiondef(p.oid) FROM PG_CATALOG.PG_PROC P, PG_CATALOG.PG_NAMESPACE NS\n" +
                "WHERE ns.oid=p.pronamespace and ns.nspname=? AND p.proname=?", sourceObject.getContainer().getName(), sourceObject.getName());
        } catch (SQLException e) {
            throw new DBException(e, sourceObject.getDataSource());
        } finally {
            session.close();
        }
    }
*/

    @Override
    public boolean supportsSequences(GenericDataSource dataSource) {
        return true;
    }

    @Override
    public List<GenericSequence> loadSequences(DBRProgressMonitor monitor, GenericObjectContainer container) throws DBException {
        try (JDBCSession session = DBUtils.openMetaSession(monitor, container.getDataSource(), "Read procedure definition")) {
            try (JDBCPreparedStatement dbStat = session.prepareStatement(
                "SELECT seq.SEQUENCENAME,seq.CURRENTVALUE,seq.MINIMUMVALUE,seq.MAXIMUMVALUE,seq.INCREMENT\n" +
                    "FROM sys.SYSSEQUENCES seq,sys.SYSSCHEMAS s\n" +
                    "WHERE seq.SCHEMAID=s.SCHEMAID AND s.SCHEMANAME=?")) {
                dbStat.setString(1, container.getName());
                try (JDBCResultSet dbResult = dbStat.executeQuery()) {
                    List<GenericSequence> result = new ArrayList<GenericSequence>();
                    while (dbResult.next()) {
                        GenericSequence sequence = new GenericSequence(
                            container,
                            JDBCUtils.safeGetString(dbResult, 1),
                            "",
                            JDBCUtils.safeGetLong(dbResult, 2),
                            JDBCUtils.safeGetLong(dbResult, 3),
                            JDBCUtils.safeGetLong(dbResult, 4),
                            JDBCUtils.safeGetLong(dbResult, 5));
                        result.add(sequence);
                    }
                    return result;
                }
            }
        } catch (SQLException e) {
            throw new DBException(e, container.getDataSource());
        }
    }

    @Override
    public DBPErrorAssistant.ErrorPosition getErrorPosition(Throwable error) {
        String message = error.getMessage();
        if (!CommonUtils.isEmpty(message)) {
            Matcher matcher = ERROR_POSITION_PATTERN.matcher(message);
            if (matcher.find()) {
                DBPErrorAssistant.ErrorPosition pos = new DBPErrorAssistant.ErrorPosition();
                pos.line = Integer.parseInt(matcher.group(1)) - 1;
                pos.position = Integer.parseInt(matcher.group(2)) - 1;
                return pos;
            }
        }
        return null;
    }

}
