/*
 * Copyright (C) 2013      Denis Forveille titou10.titou10@gmail.com
 * Copyright (C) 2010-2013 Serge Rieder serge@jkiss.org
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
package org.jkiss.dbeaver.ext.db2.model.dict;

/**
 * Type of data that can be stored in a DB2 Tablespace
 * 
 * @author Denis Forveille
 * 
 */
public enum DB2TablespaceDataType {
   A("A (All types, regular)"),

   L("L (All types, large)"),

   T("T (System temporary tables only)"),

   U("U (Created temporary tables or declared temporary tables only)");

   private String description;

   // -----------------
   // Constructor
   // -----------------
   private DB2TablespaceDataType(String description) {
      this.description = description;
   }

   // ----------------
   // Standard Getters
   // ----------------
   public String getDescription() {
      return description;
   }
}