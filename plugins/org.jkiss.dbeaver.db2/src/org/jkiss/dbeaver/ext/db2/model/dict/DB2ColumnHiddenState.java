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
 * DB2 Column Hidden Status
 * 
 * @author Denis Forveille
 * 
 */
public enum DB2ColumnHiddenState {
   I("I (Implicitely hidden)", true),

   S("S (System managed hidden)", false);

   private String  description;
   private Boolean hidden;

   // -----------
   // Constructor
   // -----------

   private DB2ColumnHiddenState(String description, Boolean hidden) {
      this.description = description;
      this.hidden = hidden;
   }

   // ----------------
   // Static Helpers
   // ----------------

   public static Boolean isHidden(String hiddenChar) {
      if (hiddenChar == null) {
         return false;
      }
      if (hiddenChar.trim().length() == 0) {
         return false;
      }
      return DB2ColumnHiddenState.valueOf(hiddenChar).isHidden();
   }

   // ----------------
   // Standard Getters
   // ----------------

   public String getDescription() {
      return description;
   }

   public Boolean isHidden() {
      return hidden;
   }
}