/*
 * Copyright (C) 2010-2013 Serge Rieder
 * serge@jkiss.org
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
package org.jkiss.dbeaver.ext.mssql.data;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.ext.mssql.model.MSSQLTableColumn;
import org.jkiss.dbeaver.model.data.DBDValueController;
import org.jkiss.dbeaver.model.data.DBDValueEditor;
import org.jkiss.dbeaver.ui.dialogs.data.DefaultValueViewDialog;
import org.jkiss.utils.CommonUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * MSSQL SET value handler
 */
public class MSSQLSetValueHandler extends MSSQLEnumValueHandler {

    public static final MSSQLSetValueHandler INSTANCE = new MSSQLSetValueHandler();

    @Override
    public DBDValueEditor createEditor(final DBDValueController controller)
        throws DBException
    {
        switch (controller.getEditType()) {
            case INLINE:
            case PANEL:
                final MSSQLTableColumn column = ((MSSQLTypeEnum) controller.getValue()).getColumn();

                return new ValueEditor<org.eclipse.swt.widgets.List>(controller) {
                    @Override
                    public void primeEditorValue(Object value) throws DBException
                    {
                        MSSQLTypeEnum enumValue = (MSSQLTypeEnum) value;
                        fillSetList(control, enumValue);
                    }
                    @Override
                    public Object extractEditorValue()
                    {
                        String[] selection = control.getSelection();
                        StringBuilder resultString = new StringBuilder();
                        for (String selString : selection) {
                            if (CommonUtils.isEmpty(selString)) {
                                continue;
                            }
                            if (resultString.length() > 0) resultString.append(',');
                            resultString.append(selString);
                        }
                        return new MSSQLTypeEnum(column, resultString.toString());
                    }

                    @Override
                    protected org.eclipse.swt.widgets.List createControl(Composite editPlaceholder)
                    {
                        return new org.eclipse.swt.widgets.List(controller.getEditPlaceholder(), SWT.BORDER | SWT.MULTI);
                    }
                };
            case EDITOR:
                return new DefaultValueViewDialog(controller);
            default:
                return null;
        }
    }

    static void fillSetList(org.eclipse.swt.widgets.List editor, MSSQLTypeEnum value)
    {
        editor.removeAll();
        List<String> enumValues = value.getColumn().getEnumValues();
        String setString = value.getValue();
        List<String> setValues = new ArrayList<String>();
        if (!CommonUtils.isEmpty(setString)) {
            StringTokenizer st = new StringTokenizer(setString, ",");
            while (st.hasMoreTokens()) {
                setValues.add(st.nextToken());
            }
        }
        if (enumValues != null) {
            int[] selIndices = new int[setValues.size()];
            int selIndex = 0;
            for (int i = 0; i < enumValues.size(); i++) {
                String enumValue = enumValues.get(i);
                editor.add(enumValue);
                if (setValues.contains(enumValue)) {
                    selIndices[selIndex++] = i;
                }
            }
            editor.select(selIndices);
        }
    }

}