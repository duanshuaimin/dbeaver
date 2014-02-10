/*
 * Copyright (C) 2013-2014 Denis Forveille titou10.titou10@gmail.com
 * Copyright (C) 2010-2014 Serge Rieder serge@jkiss.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.jkiss.dbeaver.ext.db2.tools.maintenance;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.ui.IWorkbenchPartSite;
import org.jkiss.dbeaver.ext.db2.DB2Messages;
import org.jkiss.dbeaver.ext.db2.model.DB2Table;
import org.jkiss.dbeaver.ui.UIUtils;
import org.jkiss.dbeaver.ui.dialogs.sql.GenerateMultiSQLDialog;
import org.jkiss.dbeaver.ui.dialogs.sql.SQLScriptProgressListener;
import org.jkiss.dbeaver.ui.dialogs.sql.SQLScriptStatusDialog;

import java.util.Collection;

/**
 * Base Dialog for DB2 Tools Dialogs
 */
public abstract class DB2BaseTableToolDialog extends GenerateMultiSQLDialog<DB2Table> {

    public DB2BaseTableToolDialog(IWorkbenchPartSite partSite, String title, Collection<DB2Table> objects)
    {
        super(partSite, title, objects);
    }

    @Override
    protected SQLScriptProgressListener<DB2Table> getScriptListener()
    {
        return new SQLScriptStatusDialog<DB2Table>(getShell(), getTitle() + " " + DB2Messages.dialog_table_tools_progress, null) {
            @Override
            protected void createStatusColumns(Tree objectTree)
            {
                TreeColumn msgColumn = new TreeColumn(objectTree, SWT.NONE);
                msgColumn.setText(DB2Messages.dialog_table_tools_result);
            }

            @Override
            public void endObjectProcessing(DB2Table db2Table, Exception exception)
            {
                TreeItem treeItem = getTreeItem(db2Table);
                if (exception == null) {
                    treeItem.setText(1, DB2Messages.dialog_table_tools_success_title);
                } else {
                    treeItem.setText(1, exception.getMessage());
                }
                UIUtils.packColumns(treeItem.getParent(), false, null);
            }
        };
    }
}