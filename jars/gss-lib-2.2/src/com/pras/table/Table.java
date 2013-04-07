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

package com.pras.table;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Table associated with WorkSheet. It holds data and internally used in WorkSheet.
 * 
 * @author Prasanta Paul
 */
public class Table {
	/**
	 * Table ID
	 */
	private String id;
	/**
	 * Table name
	 */
	private String name;
	/**
	 * Table description
	 */
	private String description;
	/**
	 * Table access URL
	 */
	private String url;
	/**
	 * Number of rows
	 */
	private int rowNum;
	/**
	 * Number of columns
	 */
	private int colNum;
	/**
	 * Column name
	 */
	private ArrayList<String> cols;
	/**
	 * Records- 
	 * <name,alan>, <age,20>, <country, india>, <exp, 1>
	 */
	ArrayList<Record> records = new ArrayList<Record>();
	
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getUrl() {
		return url;
	}
	public String getDescription() {
		return description;
	}
	public void setDescription(String description) {
		this.description = description;
	}
	public void setUrl(String url) {
		this.url = url;
	}
	public int getRowNum() {
		return rowNum;
	}
	public void setRowNum(int rowNum) {
		this.rowNum = rowNum;
	}
	public int getColNum() {
		return colNum;
	}
	public void setColNum(int colNum) {
		this.colNum = colNum;
	}
	public ArrayList<String> getCols() {
		return cols;
	}
	public void setCols(ArrayList<String> cols) {
		this.cols = cols;
		if(cols != null)
			setColNum(cols.size());
		else
			setColNum(0);
	}
	public ArrayList<Record> getRecords() {
		return records;
	}
	public void addRecord(Record data){
		records.add(data);
	}
	public void clearData(){
		records.clear();
	}
	public void setRecords(ArrayList<Record> records) {
		this.records = records;
	}
}
