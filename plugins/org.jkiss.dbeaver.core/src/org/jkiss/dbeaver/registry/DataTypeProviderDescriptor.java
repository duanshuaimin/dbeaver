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
package org.jkiss.dbeaver.registry;

import org.jkiss.dbeaver.Log;
import org.eclipse.core.runtime.IConfigurationElement;
import org.jkiss.dbeaver.model.data.DBDValueHandlerProvider;
import org.jkiss.dbeaver.model.impl.AbstractDescriptor;
import org.jkiss.dbeaver.model.struct.DBSTypedObject;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * EntityEditorDescriptor
 */
public class DataTypeProviderDescriptor extends AbstractDescriptor
{
    public static final String EXTENSION_ID = "org.jkiss.dbeaver.dataTypeProvider"; //$NON-NLS-1$

    static final Log log = Log.getLog(DataTypeProviderDescriptor.class);

    private static final String ALL_TYPES_PATTERN = "*";

    private String id;
    private ObjectType implType;
    private Set<Object> supportedTypes = new HashSet<>();
    private Set<DataSourceProviderDescriptor> supportedDataSources = new HashSet<>();

    private DBDValueHandlerProvider instance;

    public DataTypeProviderDescriptor(IConfigurationElement config)
    {
        super(config);

        this.id = config.getAttribute(RegistryConstants.ATTR_ID);
        this.implType = new ObjectType(config.getAttribute(RegistryConstants.ATTR_CLASS));

        IConfigurationElement[] typeElements = config.getChildren(RegistryConstants.TAG_TYPE);
        for (IConfigurationElement typeElement : typeElements) {
            String typeName = typeElement.getAttribute(RegistryConstants.ATTR_NAME);
            if (typeName != null) {
                supportedTypes.add(typeName.toLowerCase(Locale.ENGLISH));
            } else {
                typeName = typeElement.getAttribute(RegistryConstants.ATTR_STANDARD);
                if (typeName == null) {
                    log.warn("Type element without name or standard type reference"); //$NON-NLS-1$
                    continue;
                }
                try {
                    Field typeField = java.sql.Types.class.getField(typeName);
                    int typeNumber = typeField.getInt(null);
                    supportedTypes.add(typeNumber);
                }
                catch (NoSuchFieldException e) {
                    log.warn("Standard type '" + typeName + "' not found in " + java.sql.Types.class.getName(), e); //$NON-NLS-1$
                }
                catch (IllegalAccessException e) {
                    log.warn("Standard type '" + typeName + "' cannot be accessed", e); //$NON-NLS-1$
                }
            }
        }

        IConfigurationElement[] dsElements = config.getChildren(RegistryConstants.TAG_DATASOURCE);
        for (IConfigurationElement dsElement : dsElements) {
            String dsId = dsElement.getAttribute(RegistryConstants.ATTR_ID);
            if (dsId == null) {
                log.warn("Datasource reference with null ID"); //$NON-NLS-1$
                continue;
            }
            DataSourceProviderDescriptor dsProvider = DataSourceProviderRegistry.getInstance().getDataSourceProvider(dsId);
            if (dsProvider == null) {
                log.warn("Datasource provider '" + dsId + "' not found. Bad data type mapping."); //$NON-NLS-1$
                continue;
            }
            supportedDataSources.add(dsProvider);
        }
    }

    public String getId()
    {
        return id;
    }

    public DBDValueHandlerProvider getInstance()
    {
        if (instance == null && implType != null) {
            try {
                this.instance = implType.createInstance(DBDValueHandlerProvider.class);
            }
            catch (Exception e) {
                log.error("Can't instantiate data type provider '" + this.id + "'", e); //$NON-NLS-1$
            }
        }
        return instance;
    }

    public boolean supportsType(DBSTypedObject typedObject)
    {
        String typeName = typedObject.getTypeName();
        return
            supportedTypes.contains(typedObject.getTypeID()) ||
            (typeName != null && supportedTypes.contains(typeName.toLowerCase(Locale.ENGLISH))) ||
            supportedTypes.contains(ALL_TYPES_PATTERN);
    }

    public Set<Object> getSupportedTypes()
    {
        return supportedTypes;
    }

    public boolean isDefault()
    {
        return supportedDataSources.isEmpty();
    }

    public boolean supportsDataSource(DataSourceProviderDescriptor descriptor)
    {
        return supportedDataSources.contains(descriptor);
    }

    public Set<DataSourceProviderDescriptor> getSupportedDataSources()
    {
        return supportedDataSources;
    }

}