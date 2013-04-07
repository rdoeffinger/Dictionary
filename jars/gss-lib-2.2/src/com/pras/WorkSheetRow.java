/*
 * Copyright (C) 2010 Prasanta Paul, http://prasanta-paul.blogspot.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.pras;

import java.util.ArrayList;

/**
 * Supporting class for list based data feed
 * @author Prasanta Paul
 */
public class WorkSheetRow {

	String id;

	String rowIndex;
	/**
	 * Data Type of 
	 */
	String type;
	ArrayList<WorkSheetCell> cells = new ArrayList<WorkSheetCell>();
	
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
		setRowIndex(id.substring(id.lastIndexOf("/") + 1));
	}
	public String getRowIndex() {
		return rowIndex;
	}
	public void setRowIndex(String rowIndex) {
		this.rowIndex = rowIndex;
	}
	public String getType() {
		return type;
	}
	public void setType(String type) {
		this.type = type;
	}
	public ArrayList<WorkSheetCell> getCells() {
		return cells;
	}
	public void setCells(ArrayList<WorkSheetCell> cells) {
		this.cells = cells;
	}
	
	@Override
	public boolean equals(Object obj) {
		if(obj == null || !(obj instanceof WorkSheetRow))
			return false;
		WorkSheetRow r = (WorkSheetRow) obj;
		if(r.hashCode() == this.hashCode())
			return true;
		return false;
	}
	
	@Override
	public int hashCode() {
		if(id == null)
			id = "";
		return id.hashCode();
	}
	
	@Override
	public String toString() {
		// TODO Auto-generated method stub
		return ""+ cells;
	}
}
