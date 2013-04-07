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

/**
 * Individual Cell of the Work Sheet
 * One Row can have multiple Cells
 * @author Prasanta Paul
 */
public class WorkSheetCell {

	/**
	 * Name of the column this cell belongs to
	 */
	String name;
	/**
	 * Data type of the Cell (Int, String, Date etc.)
	 */
	String type;
	/**
	 * Value of the cell
	 */
	String value;
	/**
	 * Row index of this Cell
	 */
	int row = 0;
	/**
	 * Column index of this Cell
	 */
	int col = 0;
	
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getType() {
		return type;
	}
	public void setType(String type) {
		this.type = type;
	}
	public String getValue() {
		return value;
	}
	public void setValue(String value) {
		this.value = value;
	}
	public int getRow() {
		return row;
	}
	public void setRow(int row) {
		this.row = row;
	}
	public int getCol() {
		return col;
	}
	public void setCol(int col) {
		this.col = col;
	}
	@Override
	public String toString() {
		// TODO Auto-generated method stub
		return name +"="+ value;
	}
}
