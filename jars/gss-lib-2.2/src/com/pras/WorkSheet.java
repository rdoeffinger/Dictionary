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
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

import com.pras.conn.HttpConHandler;
import com.pras.conn.Response;
import com.pras.sp.Entry;
import com.pras.sp.Feed;
import com.pras.sp.Field;
import com.pras.sp.ParseFeed;
import com.pras.table.Record;
import com.pras.table.Table;

/**
 * This class represents an WorkSheet and utility methods to manage WorkSheet data.
 * WorkSheets will manage (Add/Edit/Delete) data in Tables. It also supports <u>List 
 * based Feed</u>, but only to <b>retrieve data</b>. At present it doesn't provide any method to Add/Edit/Delete
 * data in List based Feed.
 * </br>
 * </br>
 * <b><a href="http://code.google.com/apis/spreadsheets/data/3.0/developers_guide.html#TableFeeds">Table Feed</a></b>
 * </br>
 * To Add/Edit/Delete Data, you need to use addRecord(), updateRecord() and deleteRecord(). It 
 * internally stores data in a single Table. 
 * </br>
 * <b>Note:</b> <u>Table Feed will ignore data entered through Web UI.</u>
 * So, if you want all data records irrespective of from where those are inserted/updated, you should use List Feed.
 *    
 * </br>
 * </br>
 * <b><a href="http://code.google.com/apis/spreadsheets/data/3.0/developers_guide.html#ListFeeds">List Feed</a></b>
 * </br>
 * To Retrieve data using List based Feed, use getData() method.
 * 
 * @author Prasanta Paul
 */

public class WorkSheet {

	/*
	 *TODO:
	 *Add set and get methods without creating local variables (use entry instance)
	 */
	private String TAG = "WorkSheet";
	private String workSheetID;
	private String workSheetURL;
	private String title;
	private int colCount;
	private int rowCount;
	/**
	 * Entries of the Header Row of List Feed
	 */
	String[] columns = null;
	
	/**
	 * Table associated with this WorkSheet. It will hold data records
	 */
	private Table table;

	/**
	 * To access all low level Feed values for this Work Sheet
	 */
	private Entry entry;
	
	/**
	 * It will hold WorkSheet records
	 */
	private ArrayList<WorkSheetRow> records = new ArrayList<WorkSheetRow>();
	
	/**
	 * Get WorkSheet ID
	 * @return
	 */
	public String getWorkSheetID() {
		return workSheetID;
	}
	/**
	 * Set WorkSheet ID
	 * @param workSheetID
	 */
	public void setWorkSheetID(String workSheetID) {
		this.workSheetID = workSheetID;
	}
	
	/**
	 * Get WorkSheetURL
	 * @return
	 */
	public String getWorkSheetURL() {
		/*
		 * Sample: https://spreadsheets.google.com/feeds/worksheets/key/private/full
		 * Retrieved from src attribute-
		 * <content type='application/atom+xml;type=feed' src=''/>
		 */
		return workSheetURL;
	}
	/**
	 * Set WorkSheet
	 * @param workSheetURL
	 */
	public void setWorkSheetURL(String workSheetURL) {
		this.workSheetURL = workSheetURL;
	}
	/**
	 * Get WorkSheet Title
	 * @return
	 */
	public String getTitle() {
		return title;
	}
	/**
	 * Set WorkSheet Title
	 * @param title
	 */
	public void setTitle(String title) {
		this.title = title;
	}
	/**
	 * Get column count of this WorkSheet 
	 * @return
	 */
	public int getColCount() {
		return colCount;
	}
	/**
	 * Set column count of this WorkSheet 
	 * @param colCount
	 */
	public void setColCount(int colCount) {
		this.colCount = colCount;
	}
	/**
	 * Get row count of this WorkSheet 
	 * @return
	 */
	public int getRowCount() {
		return rowCount;
	}
	/**
	 * Set row count of this WorkSheet 
	 * @param rowCount
	 */
	public void setRowCount(int rowCount) {
		this.rowCount = rowCount;
	}
	
	/**
	 * Get columns of the WorkSheet. It will return columns of associated table, otherwise
	 * columns of List Feed i.e. content of Header row of ListFeed
	 * @return
	 */
	public String[] getColumns() {
		if(columns != null)
			return columns;
		
		if(table != null){
			ArrayList<String> tableCols = table.getCols();
			if(tableCols != null){
				String[] cols = new String[tableCols.size()];
				tableCols.toArray(cols);
				return cols;
			}
		}
		return null;
	}
	
	/**
	 * Set columns of ListFeed
	 * @param columns
	 */
	public void setColumns(String[] columns) {
		this.columns = columns;
	}
	
	/**
	 * Get Entry instance for this WorkSheet.
	 * Entry contains low level Feed Details
	 * 
	 * @return
	 */
	public Entry getEntry() {
		return entry;
	}
	/**
	 * Set Feed Entry instance
	 * @param entry
	 */
	public void setEntry(Entry entry) {
		this.entry = entry;
	}
	
	void setTable(Table table){
		this.table = table;
	}
	
	/**
	 * Get all data of this WorkSheet (List based Feed)
	 * 
	 * @return
	 */
	/**
	 * Get all data of this WorkSheet (List based Feed)
	 * 
	 * @param isCachedData Do you want to read cached data or data from Server
	 * @return
	 */
	public ArrayList<WorkSheetRow> getData(boolean isCachedData){
		return getData(isCachedData, false, null, null);
	}

	/**
	 * List based Feed for a particular Work Sheet. Use this method if you want to retrieve data entered
	 * through Web GUI and also through Table records.
	 * 
	 * @param isCachedData Do you want to read cached data or data from Server
	 * @param doReverse Do you need data in reverse order ?
	 * @param sq <a href="http://code.google.com/apis/spreadsheets/data/3.0/developers_guide.html#SendingStructuredRowQueries">Structured Query</a>. If you don't need this, set to <b>null</b>. (Make sure that column name is in lower case).
	 * @param orderBy If you don't need this, set to <b>null</b>. (Make sure that column name is in lower case). 
	 * @return
	 */
	public ArrayList<WorkSheetRow> getData(boolean isCachedData, boolean doReverse, String sq, String orderBy){
		// Sample URL: GET https://spreadsheets.google.com/feeds/list/key/worksheetId/private/full
		
		if(isCachedData)
			return records;
		
		// Add Headers
		HashMap<String, String> httpHeaders = new HashMap<String, String>();
		httpHeaders.put(HttpConHandler.AUTHORIZATION_HTTP_HEADER, "GoogleLogin auth="+ SpreadSheetFactory.authToken);
		httpHeaders.put(HttpConHandler.GDATA_VERSION_HTTP_HEADER, "3.0");
		
		HttpConHandler http = new HttpConHandler();
		
		String url = workSheetURL;
		
		// Add data Filter
		if (doReverse) {
			// Table record in reverse order (last row first)
			url = url.concat("?reverse=true");
			if (sq != null) {
				// Structured Query
				url = url.concat("&sq=" + sq);
			}
			if (orderBy != null) {
				// Order By Statement
				url = url.concat("&orderby=" + orderBy);
			}
		} else {
			// Structured Query & Order By
			if (sq != null) {
				url = url.concat("?sq=" + sq);
				if(orderBy != null)
					url = url.concat("&orderby=" + orderBy);
			} else {
				if (orderBy != null)
					url = url.concat("?orderby="+ orderBy);
			}
		}
		
		Response res = http.doConnect(url, HttpConHandler.HTTP_GET, httpHeaders, null);
		
		if(res.isError()){
			return null;
		}
		
		String xmlOut = res.getOutput();
		
		// XML Parsing
		ParseFeed pf = new ParseFeed();
		Feed f = pf.parse(xmlOut.getBytes());
		ArrayList<Entry> entries = f.getEntries();
		
		// [Fix_11_JAN_2011_Prasanta multiple call to getRecords() while changing Android orientation
		records.clear();
		// Fix_11_JAN_2011_Prasanta]
		
		if(entries != null){
			for(int i=0; i<entries.size(); i++){
				Entry e = entries.get(i);
				WorkSheetRow row = new WorkSheetRow();
				row.setId(e.getId());
				row.setCells(e.getCells());
				
				// Add to records
				records.add(row);
			}
		}
		
		return records;
	}
	
	/**
	 * Add List Feed row. One row at a time.
	 * TODO: need to test
	 * @param records
	 */
	public WorkSheetRow addListRow(HashMap<String, String> records){
		/*
		 * It will send request to WorkSheet URL:
		 * https://spreadsheets.google.com/feeds/list/key/worksheetId/private/full
		 * Unlike Tables, there can be only one List Feed associated with a given WorkSheet
		 */
		
		StringBuffer listRecordXML = new StringBuffer();
		listRecordXML.append("<entry xmlns=\"http://www.w3.org/2005/Atom\" xmlns:gsx=\"http://schemas.google.com/spreadsheets/2006/extended\">");
		
		Iterator<String> ks = records.keySet().iterator();
		while(ks.hasNext()){
			String colName = ks.next();
			String value = records.get(colName);
			listRecordXML.append(" <gsx:"+ colName +">"+ value +"</gsx:"+ colName +">");
		}
		listRecordXML.append("</entry>");
		
		// Do server transaction
		// Add Headers
		HashMap<String, String> httpHeaders = new HashMap<String, String>();
		httpHeaders.put(HttpConHandler.AUTHORIZATION_HTTP_HEADER, "GoogleLogin auth="+ SpreadSheetFactory.authToken);
		httpHeaders.put(HttpConHandler.GDATA_VERSION_HTTP_HEADER, "3.0");
		httpHeaders.put(HttpConHandler.CONTENT_TYPE_HTTP_HEADER, "application/atom+xml");
		
		HttpConHandler http = new HttpConHandler();
		Response res = http.doConnect(workSheetURL, HttpConHandler.HTTP_POST, httpHeaders, listRecordXML.toString());
		
		// Add the into local Record cache
		// Parse response and create new Row instance
		if(res.isError()){
			Log.p(TAG, "Error in updating List Row...");
			return null;
		}
		
		String xmlOut = res.getOutput();
		
		// XML Parsing
		ParseFeed pf = new ParseFeed();
		Feed f = pf.parse(xmlOut.getBytes());
		ArrayList<Entry> entries = f.getEntries();
		
		if(entries == null || entries.size() == 0){
			Log.p(TAG, "Error in parsing...");
			return null;
		}
		
		Entry e = entries.get(0);
		WorkSheetRow row = new WorkSheetRow();
		row.setId(e.getId());
		row.setCells(e.getCells());
		
		// Update local Cache
		if(this.records == null){
			this.records = new ArrayList<WorkSheetRow>();
			this.records.add(row);
			return row;
		}
		// Update existing Row instance
		this.records.add(row);
		return row;
	}
	
	/**
	 * Delete List Row
	 * @param r WorkSheetRow which need to be deleted
	 */
	public void deleteListRow(String key, WorkSheetRow r)
	{
		if(r == null){
			Log.p(TAG, "WorkSheetRow is null...");
			return;
		}
		
		// Sample DELETE URL- https://spreadsheets.google.com/feeds/list/tmG5DprMeR-l2j91JQBB1TQ/odp/private/full/cokwr
		String listRowURL = "https://spreadsheets.google.com/feeds/list/"+ key +"/"+ workSheetID + "/private/full/"+ r.getRowIndex();
		
		// Do server transaction
		// Add Headers
		HashMap<String, String> httpHeaders = new HashMap<String, String>();
		httpHeaders.put(HttpConHandler.AUTHORIZATION_HTTP_HEADER, "GoogleLogin auth="+ SpreadSheetFactory.authToken);
		httpHeaders.put(HttpConHandler.GDATA_VERSION_HTTP_HEADER, "3.0");
		//If you want to delete the row regardless of whether someone else has updated it since you retrieved it
		httpHeaders.put("If-Match", "*");
		
		HttpConHandler http = new HttpConHandler();
		http.doConnect(listRowURL, HttpConHandler.HTTP_DELETE, httpHeaders, null);
		
		// Delete this entry from local record list
		records.remove(r);
	}
	
	/**
	 * Update List row
	 * @param rowID ID of the List row to be updated
	 * @param records records need to be updated
	 */

	/**
	 * Update List row
	 * 
	 * @param r WorkSheetRow to be updated
	 * @param records new records
	 * @return
	 */
	public WorkSheetRow updateListRow(String key, WorkSheetRow r, HashMap<String, String> records)
	{
		if(r == null){
			Log.p(TAG, "WorkSheetRow is null...");
			return r;
		}
		
		// Sample PUT URL- https://spreadsheets.google.com/feeds/list/tmG5DprMeR-l2j91JQBB1TQ/odp/private/full/cokwr
		String listRowURL = "https://spreadsheets.google.com/feeds/list/"+ key +"/"+ workSheetID + "/private/full/"+ r.getRowIndex();
		
		StringBuffer rowUpdateXML = new StringBuffer("<entry xmlns=\"http://www.w3.org/2005/Atom\" xmlns:gsx=\"http://schemas.google.com/spreadsheets/2006/extended\">");
		
		Iterator<String> ks = records.keySet().iterator();
		while(ks.hasNext()){
			String colName = ks.next();
			String value = records.get(colName);
			rowUpdateXML.append(" <gsx:"+ colName +">"+ value +"</gsx:"+ colName +">");
		}
		rowUpdateXML.append("</entry>");
		
		// Do server transaction- PUT
		// Add Headers
		HashMap<String, String> httpHeaders = new HashMap<String, String>();
		httpHeaders.put(HttpConHandler.AUTHORIZATION_HTTP_HEADER, "GoogleLogin auth="+ SpreadSheetFactory.authToken);
		httpHeaders.put(HttpConHandler.GDATA_VERSION_HTTP_HEADER, "3.0");
		httpHeaders.put(HttpConHandler.CONTENT_TYPE_HTTP_HEADER, "application/atom+xml");
		//If you want to delete the row regardless of whether someone else has updated it since you retrieved it
		httpHeaders.put("If-Match", "*");
		
		HttpConHandler http = new HttpConHandler();
		Response res = http.doConnect(listRowURL, HttpConHandler.HTTP_PUT, httpHeaders, rowUpdateXML.toString());
		
		// Parse response and create new Row instance
		if(res.isError()){
			Log.p(TAG, "Error in updating List Row...");
			return null;
		}
		
		String xmlOut = res.getOutput();
		
		// XML Parsing
		ParseFeed pf = new ParseFeed();
		Feed f = pf.parse(xmlOut.getBytes());
		ArrayList<Entry> entries = f.getEntries();
		
		if(entries == null || entries.size() == 0){
			Log.p(TAG, "Error in parsing...");
			return null;
		}
		
		Entry e = entries.get(0);
		WorkSheetRow row = new WorkSheetRow();
		row.setId(e.getId());
		row.setCells(e.getCells());
		
		// Update local Cache
		if(this.records == null){
			this.records = new ArrayList<WorkSheetRow>();
			this.records.add(row);
			return row;
		}
		// Update existing Row instance
		this.records.set(this.records.indexOf(row), row);
		return row;
	}

	/**
	 * Add record into WorkSheet. This WorkSheet need to have associated Table.
	 * 
	 * Use SpreadSheet.addWorkSheet()
	 * </br>
	 * <b>Note:</b>
	 * </br>
	 * All data entered through Web Interface will be ignored.
	 * 
	 * @param key Key of SpreadSheet
	 * @param records Record to be added ([col_name],[value])
	 */
	public void addRecord(String key, HashMap<String, String> records){
		if(table == null){
			throw new IllegalAccessError("This WorkSheet doesn't have any Table");
		}
		
		Log.p(TAG, "Associated Table ID: "+ table.getId());
		String tableRecordURL = "https://spreadsheets.google.com/feeds/"+ key +"/records/"+ table.getId();
		
		String tableRecordXML = "<entry xmlns='http://www.w3.org/2005/Atom' xmlns:gs='http://schemas.google.com/spreadsheets/2006'>"+
		  						"<title>"+ table.getName() +"</title>";
		
		Set keys = records.keySet();
		Iterator<String> it = keys.iterator();
		
		while(it.hasNext()){
			String k = it.next();
			tableRecordXML = tableRecordXML.concat("<gs:field name='"+ k +"'>"+ records.get(k) +"</gs:field>");
		}
		
		tableRecordXML = tableRecordXML.concat("</entry>");
		
		Log.p(TAG, "Table Record XML: "+ tableRecordXML);
		
		// Add Headers
		HashMap<String, String> httpHeaders = new HashMap<String, String>();
		httpHeaders.put(HttpConHandler.AUTHORIZATION_HTTP_HEADER, "GoogleLogin auth="+ SpreadSheetFactory.authToken);
		httpHeaders.put(HttpConHandler.GDATA_VERSION_HTTP_HEADER, "3.0");
		httpHeaders.put(HttpConHandler.CONTENT_TYPE_HTTP_HEADER, "application/atom+xml");
		
		HttpConHandler http = new HttpConHandler();
		http.doConnect(tableRecordURL, HttpConHandler.HTTP_POST, httpHeaders, tableRecordXML);
	}
	

	/**
	 * Get data stored in this WorkSheet
	 * 
	 * @param key SpreadSheet Key
	 * @return
	 */
	public ArrayList<Record> getRecords(String key){
		return getRecords(key, false, null, null);
	}
	
	/**
	 * @param key SpreadSheet Key
	 * @param sq  <a href="http://code.google.com/apis/spreadsheets/data/3.0/developers_guide.html#SendingStructuredRowQueries">Structured Query</a> 
	 * 
	 * @return
	 */
	public ArrayList<Record> getRecords(String key, String sq){
		return getRecords(key, false, sq, null);
	}


	/**
	 * Get data stored in this WorkSheet. Retrieved data will be in a HashMap-
	 * <COL_NAME>,<VALUE>
	 * <br/>
	 * It supports following conditional Query- <br/>
	 * <b>Structured Query:</b> you can define conditional statements like in SQL e.g. <col_name> != <value><br/>
	 * <b>Order By:</b> Order by a given column name or position <br/>
	 * <b>Reverse:</b> Record retrived in reverse order (last row 1st)<br/>
	 * <br/>
	 * <b>NOTE:</b>
	 * <br/>
	 * It will retrieve records present in the Table. Tables can't be accessed by the Web UI.
	 * <br/>
	 * So, any data inserted by Web UI will be discarded.
	 * <br/>
	 * @param key SpreadSheet Key
	 * @param doReverse Display data in reverse order (last row first)
	 * @param sq <a href="http://code.google.com/apis/spreadsheets/data/3.0/developers_guide.html#SendingStructuredRowQueries">Structured Query</a>
	 * @param orderBy <a href="http://code.google.com/apis/spreadsheets/data/3.0/developers_guide.html#SendingStructuredRowQueries">Order By</a>
	 * @return
	 */
	public ArrayList<Record> getRecords(String key, boolean doReverse, String sq, String orderBy){
		
		if(table == null){
			Log.p(TAG, "No Associated Table to Hold Data!!");
			return null;
		}
		
		// Table Access URL
		String tableRecordURL = "https://spreadsheets.google.com/feeds/"+ key +"/records/"+ table.getId();
		
		if(doReverse){
			// Table record in reverse order (last row first)
			tableRecordURL = tableRecordURL.concat("?reverse=true");
			if(sq != null){
				// Structured Query
				tableRecordURL = tableRecordURL.concat("&sq="+ sq);
			}
			if(orderBy != null){
				// Order By Statement
				tableRecordURL = tableRecordURL.concat("&orderby="+ orderBy);
			}
		}
		else{
			// Structured Query & Order By
			if(sq != null){
				tableRecordURL = tableRecordURL.concat("?sq="+ sq);
				if(orderBy != null)
					tableRecordURL = tableRecordURL.concat("&orderby="+ orderBy);
			}else{
				if(orderBy != null)
					tableRecordURL = tableRecordURL.concat("?orderby="+ orderBy);
			}
		}
		
		Log.p(TAG, "tableRecordURL="+ tableRecordURL);
		
		// Add Headers
		HashMap<String, String> httpHeaders = new HashMap<String, String>();
		httpHeaders.put(HttpConHandler.AUTHORIZATION_HTTP_HEADER, "GoogleLogin auth="+ SpreadSheetFactory.authToken);
		httpHeaders.put(HttpConHandler.GDATA_VERSION_HTTP_HEADER, "3.0");
		
		HttpConHandler http = new HttpConHandler();
		Response res = http.doConnect(tableRecordURL, HttpConHandler.HTTP_GET, httpHeaders, null);
		
		if(res.isError()){
			return null;
		}
		
		String xmlOut = res.getOutput();
		
		// XML Parsing
		ParseFeed pf = new ParseFeed();
		Feed f = pf.parse(xmlOut.getBytes());
		ArrayList<Entry> entries = f.getEntries();
		
		if(entries == null){
			Log.p(TAG, "No Reord found!!");
			return null;
		}
		
		// [Fix_11_JAN_2011_Prasanta multiple call to getRecords() while changing Android orientation
		table.clearData();
		// Fix_11_JAN_2011_Prasanta]
		
		for(int i=0; i< entries.size(); i++){
			Entry e = entries.get(i);
			ArrayList<Field> fields = e.getFields();
			Record r = new Record();
			r.setId(e.getId());
			r.setEditURL(e.getEditLink());
			
			for(int j=0; j<fields.size(); j++){
				Field fd = fields.get(j);
				r.addData(fd.getColName(), fd.getValue());
			}
			table.addRecord(r);
		}
		
		return table.getRecords();
	}
	
	/**
	 * Get Cached Data (previously retrieved from server)
	 * @return
	 */
	public ArrayList<Record> getRecords(){
		return table.getRecords();
	}
	
	/**
	 * Record instance you want to Update
	 * Keep only those data which you want to update for this record
	 * 
	 * @param record Record to be updated
	 */
	public void updateRecord(Record record){
		
		if(record == null){
			throw new IllegalAccessError("Pass a valid Record!!");
		}
		
		String recordXML = "<entry xmlns='http://www.w3.org/2005/Atom' xmlns:gs='http://schemas.google.com/spreadsheets/2006'>"+
						  "<id>"+ record.getId() +"</id>";
		
		HashMap<String, String> data = record.getData();
		
		Set keys = data.keySet();
		Iterator<String> it = keys.iterator();
		
		while(it.hasNext()){
			String k = it.next();
			recordXML = recordXML.concat("<gs:field name='"+ k +"'>"+ data.get(k) +"</gs:field>");
		}
		
		recordXML += "</entry>";
		
		Log.p(TAG, "Update Record XML: "+ recordXML);
		
		// Add Headers
		HashMap<String, String> httpHeaders = new HashMap<String, String>();
		httpHeaders.put(HttpConHandler.AUTHORIZATION_HTTP_HEADER, "GoogleLogin auth="+ SpreadSheetFactory.authToken);
		httpHeaders.put(HttpConHandler.GDATA_VERSION_HTTP_HEADER, "3.0");
		httpHeaders.put(HttpConHandler.CONTENT_TYPE_HTTP_HEADER, "application/atom+xml");
		httpHeaders.put("If-Match", "*");
		
		// HTTP Connection
		HttpConHandler http = new HttpConHandler();
		http.doConnect(record.getEditURL(), HttpConHandler.HTTP_PUT, httpHeaders, recordXML);
	}
	
	/**
	 * Delete a Record
	 * @param record Record to be deleted
	 */
	public void deleteRecord(Record record){
		
		if(record == null){
			throw new IllegalAccessError("Pass a valid Record!!");
		}
		
		// Add Headers
		HashMap<String, String> httpHeaders = new HashMap<String, String>();
		httpHeaders.put(HttpConHandler.AUTHORIZATION_HTTP_HEADER, "GoogleLogin auth="+ SpreadSheetFactory.authToken);
		httpHeaders.put(HttpConHandler.GDATA_VERSION_HTTP_HEADER, "3.0");
		httpHeaders.put("If-Match", "*");
		
		// HTTP Connection
		HttpConHandler http = new HttpConHandler();
		http.doConnect(record.getEditURL(), HttpConHandler.HTTP_DELETE, httpHeaders, null);
	}
	
	/**
	 * Delete this WorkSheet. It will also delete its associated Table.
	 * There is no separate method for deleting a Table
	 */
	public void delete(){
		// Sample URL: DELETE https://spreadsheets.google.com/feeds/worksheets/key/private/full/worksheetId/version
		// We don't need "version"
		
		// Add Headers
		HashMap<String, String> httpHeaders = new HashMap<String, String>();
		httpHeaders.put(HttpConHandler.AUTHORIZATION_HTTP_HEADER, "GoogleLogin auth="+ SpreadSheetFactory.authToken);
		httpHeaders.put(HttpConHandler.GDATA_VERSION_HTTP_HEADER, "3.0");
		httpHeaders.put("If-Match", "*");
		
		HttpConHandler http = new HttpConHandler();
		String wsDeleteURL = entry.getId().substring(0, entry.getId().lastIndexOf("/"));
		wsDeleteURL = wsDeleteURL.concat("/private/full/").concat(workSheetID);
		
		http.doConnect(wsDeleteURL, HttpConHandler.HTTP_DELETE, httpHeaders, null);
	}
}
