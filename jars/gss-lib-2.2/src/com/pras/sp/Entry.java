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

package com.pras.sp;

import java.util.ArrayList;

import com.pras.WorkSheetCell;


/**
 * This class represents an entry of SpreadSheet XML Feed
 * @author Prasanta Paul
 *
 */
public class Entry {

	String eTAG;
	String id;
	String key;
	String title;
	String workSheetURL;
	/**
	 * Table Feed- Summary
	 */
	String summary;
	
	String authorName;
	String authorEmail;
	
	// ACL Fields- Role and Scope
	String aclRole;
	String aclScopeType;
	String aclScopeValue;
	String editLink;
	
	// Fields specific to Work Sheet 
	int rowCount;
	int colCount;
	
	// SpreadSheet Doc ID
	String resID;
	// Cell Feed, cell info
	WorkSheetCell cellInfo = null;
	
	//WorkSheet List Feed- Cells
	ArrayList<WorkSheetCell> cells = new ArrayList<WorkSheetCell>();
	
	/**
	 * Table feed column name
	 */
	ArrayList<String> cols = new ArrayList<String>();
	
	/**
	 * Table record
	 */
	ArrayList<Field> fields = new ArrayList<Field>();
	
	
	public String getETAG() {
		return eTAG;
	}
	public void setETAG(String etag) {
		eTAG = etag;
	}
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
		setKey(id.substring(id.lastIndexOf("/") + 1));
	}
	public String getKey() {
		return key;
	}
	public void setKey(String key) {
		this.key = key;
	}
	public String getTitle() {
		return title;
	}
	public void setTitle(String title) {
		this.title = title;
	}
	public String getWorkSheetURL() {
		return workSheetURL;
	}
	public void setWorkSheetURL(String workSheetURL) {
		this.workSheetURL = workSheetURL;
	}
	public String getAuthorName() {
		return authorName;
	}
	public void setAuthorName(String authorName) {
		this.authorName = authorName;
	}
	public String getAuthorEmail() {
		return authorEmail;
	}
	public void setAuthorEmail(String authorEmail) {
		this.authorEmail = authorEmail;
	}
	public int getRowCount() {
		return rowCount;
	}
	public void setRowCount(int rowCount) {
		this.rowCount = rowCount;
	}
	public int getColCount() {
		return colCount;
	}
	public void setColCount(int colCount) {
		this.colCount = colCount;
	}
	public WorkSheetCell getCellInfo() {
		return cellInfo;
	}
	public void setCellInfo(WorkSheetCell cellInfo) {
		this.cellInfo = cellInfo;
	}
	public ArrayList<WorkSheetCell> getCells() {
		return cells;
	}
	public void setCells(ArrayList<WorkSheetCell> cells) {
		this.cells = cells;
	}
	public void addCell(WorkSheetCell cell) {
		this.cells.add(cell);
	}
	public String getResID() {
		return resID;
	}
	public void setResID(String resID) {
		this.resID = resID;
	}
	public String getSummary() {
		return summary;
	}
	public void setSummary(String summary) {
		this.summary = summary;
	}
	public ArrayList<String> getCols() {
		return cols;
	}
	public void addCol(String colName){
		cols.add(colName);
	}
	public void setCols(ArrayList<String> cols) {
		this.cols = cols;
	}
	public ArrayList<Field> getFields() {
		return fields;
	}
	public void addField(Field f){
		fields.add(f);
	}
	public void setFields(ArrayList<Field> fields) {
		this.fields = fields;
	}
	public String getAclRole() {
		return aclRole;
	}
	public void setAclRole(String aclRole) {
		this.aclRole = aclRole;
	}
	public String getAclScopeType() {
		return aclScopeType;
	}
	public void setAclScopeType(String aclScopeType) {
		this.aclScopeType = aclScopeType;
	}
	public String getAclScopeValue() {
		return aclScopeValue;
	}
	public void setAclScopeValue(String aclScopeValue) {
		this.aclScopeValue = aclScopeValue;
	}
	public String getEditLink() {
		return editLink;
	}
	public void setEditLink(String editLink) {
		this.editLink = editLink;
	}
}
