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

import java.util.HashMap;

/**
 * This class represents individual records of the Table (associated with an WorkSheet)
 * 
 * @author Prasanta Paul
 */
public class Record {
	/**
	 * <link rel='edit' href="">
	 */
	String editURL;
	/**
	 * <id> node of an entry
	 */
	String id;

	/**
	 * It will store data of a single row
	 * <Col_Name> <Value>
	 */
	HashMap<String, String> data = new HashMap<String, String>();

	/**
	 * Get Record Edit URL
	 * @return
	 */
	public String getEditURL() {
		return editURL;
	}

	/**
	 * Set Record Edit URL
	 * @param editURL
	 */
	public void setEditURL(String editURL) {
		this.editURL = editURL;
	}

	/**
	 * Get data of this Record
	 * @return
	 */
	public HashMap<String, String> getData() {
		return data;
	}
	
	/**
	 * Remove all stored data from this Record
	 */
	public void clearData(){
		data.clear();
	}
	
	/**
	 * Add data into this Record
	 * 
	 * @param colName
	 * @param value
	 */
	public void addData(String colName, String value){
		data.put(colName, value);
	}
	
	/**
	 * Set Data into this Record
	 * @param data
	 */
	public void setData(HashMap<String, String> data) {
		this.data = data;
	}
	/**
	 * Get ID URL of this Record
	 * @return
	 */
	public String getId() {
		return id;
	}

	/**
	 * Set ID URL of this Record
	 * @param id
	 */
	public void setId(String id) {
		this.id = id;
	}
}
