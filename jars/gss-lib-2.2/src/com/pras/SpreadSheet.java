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

import com.pras.conn.HttpConHandler;
import com.pras.conn.Response;
import com.pras.sp.Entry;
import com.pras.sp.Feed;
import com.pras.sp.ParseFeed;
import com.pras.table.Table;

/**
 * This class represents a given Spreadsheet. You can add multiple WorkSheets into a Spreadsheet.
 * It provides methods to Add/Retrieve/Delete WorkSheet and Share SpreadSheet.
 * 
 * <br/>API Ref:<br/>
 * <a href="http://code.google.com/apis/spreadsheets/data/3.0/developers_guide.html">http://code.google.com/apis/spreadsheets/data/3.0/developers_guide.html</a>
 * <br/>
 * Feed Ref:
 * <br/>
 * <a href="http://code.google.com/apis/documents/docs/3.0/reference.html">http://code.google.com/apis/documents/docs/3.0/reference.html</a>
 * <br/>
 * <br/>
 * <b>NOTE:</b>
 * <i>Resource ID is required to delete a SpreadSheet</i>
 * 
 * @author Prasanta Paul
 */
public class SpreadSheet {

	/*
	 * How to get Resource_ID ?
	 * From SpreadSheet Feed (SpreadSheet List) read the "key" of the following field 
	 * <link rel='alternate' type='text/html' href='https://spreadsheets.google.com/ccc?key=0Asn_4k-vXoTXdHhVcUIyeDZwV3VWREdMZll5RTJTMmc'/>
	 */
	private String TAG = "SpreadSheet";
	/**
	 * This will contain info specific to SpreadSheet and not about its
	 * WorkSheets
	 */
	Entry entry;
	ArrayList<WorkSheet> wks = new ArrayList<WorkSheet>();
	/**
	 * List of People with whom this SpreadSheet is shared and their
	 * access rights (ACL)
	 */
	ArrayList<Collaborator> collaborators = new ArrayList<Collaborator>();
	
	
	@Override
	public boolean equals(Object obj) {
		if(obj == null)
			return false;
		if(obj instanceof SpreadSheet){
			SpreadSheet s = (SpreadSheet) obj;
			if(this.hashCode() == s.hashCode())
				return true;
		}
		return false;
		//return super.equals(obj);
	}

	@Override
	public int hashCode() {
		/*
		 * ResID will be unique for each SpreadSheet
		 * e.g.
		 * spreadsheet:0Asn_4k-vXoTXdHhVcUIyeDZwV3VWREdMZll5RTJTMmc
		 */
		if(entry != null){
			return entry.getResID().hashCode();
		}
		return super.hashCode();
	}

	public SpreadSheet(Entry entry){
		this.entry = entry;
	}
	
	/**
	 * Get Entry Object of SpreadSheet Feed. Entry holds all SpreadSheet Feed details
	 * 
	 * @return
	 */
	public Entry getEntry() {
		return entry;
	}

	/**
	 * Set Entry Object of SpreadSheet Feed.
	 * @param entry
	 */
	public void setEntry(Entry entry) {
		this.entry = entry;
	}
	
	/**
	 * Get SpreadSheet Title
	 * @return
	 */
	public String getTitle(){
		if(entry != null)
			return entry.getTitle();
		return null;
	}
	
	/**
	 * Get SpreadSheet Resource ID. Each SpreadSheet has an unique Resource ID.
	 * This ID is required to Delete this SpreadSheet.
	 * 
	 * @return
	 */
	public String getResourceID(){
		if(entry != null)
			return entry.getResID();
		return null;
	}
	
	/**
	 * Get SpreadSheet Feed Key
	 * @return
	 */
	public String getKey(){
		if(entry != null)
			return entry.getKey();
		return null;
	}
	/**
	 * Get the list of all Collaborator details (users/groups and their access rights) for a given SpreadSheet
	 * 
	 * @return
	 */
	public ArrayList<Collaborator> getCollaborators() {
		return collaborators;
	}

	/**
	 * Add a Collaborator
	 * @param c
	 */
	public void addCollaborator(Collaborator c){
		collaborators.add(c);
	}
	
	/**
	 * Clear existing list of Collaborators
	 */
	public void clearCollaboratorList(){
		collaborators.clear();
	}
	
	/**
	 * Set a list of Collaborators
	 * @param collaborators
	 */
	public void setCollaborators(ArrayList<Collaborator> collaborators) {
		this.collaborators = collaborators;
	}
	
	/**
	 * This will create an WorkSheet without Table. It will create WorkSheet with 1 Column and 1 Row. It can
	 * be accessed/modified by Web Interface.
	 * 
	 * <b>IMPORTANT:</b> 
	 * This Library supports Record Handling only through Table. If you use this method, it will not
	 * create Table internally and thus unable to add/edit/delete record from this WorkSheet. Instead,
	 * use addWorkSheet(String name, String[] cols)
	 * 
	 * @param name WorkSheet name
	 */
	public void addWorkSheet(String name){
		
		if(name == null){
			throw new IllegalAccessError("Please provide WorkSheet Name");
		}
		addWorkSheet(name, 1, 1);
	}
	
	/**
	 * Create WorkSheet. 
	 * 
	 * @param name name of WorkSheet
	 * @param col number of columns
	 * @param row number of rows
	 */
	private WorkSheet addWorkSheet(String name, int col, int row){
		// Sample URL: https://spreadsheets.google.com/feeds/worksheets/key/private/full
		String workSheetURL = "https://spreadsheets.google.com/feeds/worksheets/"+ entry.getKey() +"/private/full";
		
		// Add headers
		HashMap<String, String> httpHeaders = new HashMap<String, String>();
		httpHeaders.put(HttpConHandler.AUTHORIZATION_HTTP_HEADER, "GoogleLogin auth="+ SpreadSheetFactory.authToken);
		httpHeaders.put(HttpConHandler.GDATA_VERSION_HTTP_HEADER, "3.0");
		httpHeaders.put(HttpConHandler.CONTENT_TYPE_HTTP_HEADER, "application/atom+xml");
		
		String postData = "<entry xmlns=\"http://www.w3.org/2005/Atom\""+
						  " xmlns:gs=\"http://schemas.google.com/spreadsheets/2006\">"+
						  "<title>"+ name +"</title>"+
						  "<gs:rowCount>"+ row +"</gs:rowCount>"+
						  "<gs:colCount>"+ col +"</gs:colCount>"+
						  "</entry>";
		Log.p(TAG, "POST Data- "+ postData);
		HttpConHandler http = new HttpConHandler();
		Response resp = http.doConnect(workSheetURL, HttpConHandler.HTTP_POST, httpHeaders, postData);
		if(resp == null)
			return null;
		
		// Create WorkSheet instance from the response
		return parseWorkSheet(resp.getOutput());
	}
	
	private WorkSheet parseWorkSheet(String xmlFeed)
	{
		if(xmlFeed == null)
			return null;
		
		// XML Parsing
		ParseFeed pf = new ParseFeed();
		Feed f = pf.parse(xmlFeed.getBytes());
		ArrayList<Entry> entries = f.getEntries();
		
		if(entries == null || entries.size() == 0){
			return null;
		}
		
		WorkSheet ws = new WorkSheet();
		
		for(int i=0; i<entries.size(); i++){
			Entry e = entries.get(i);
			
			ws.setWorkSheetID(e.getKey());
			ws.setWorkSheetURL(e.getWorkSheetURL());
			ws.setTitle(e.getTitle());
			ws.setColCount(e.getColCount());
			ws.setRowCount(e.getRowCount());
			ws.setEntry(e);
		}
		return ws;
	}
	/**
	 * Add WorkSheet
	 * This will create an Internal Table and manage WorkSheet data in that Table.
	 * This Lib supports only Table based record Add/Edit/Delete
	 * 
	 * 
	 * @param name name of WorkSheet
	 * @param cols name of Columns
	 */
	public void addWorkSheet(String name, String[] cols){
		
		if(cols == null || cols.length == 0){
			throw new IllegalAccessError("Please provide column name");
		}
		
		// Add WorkSheet
		/*
		 * 21st Dec, 2010
		 * Bug:
		 * If I use row count as 1, it gives HTTP 403 error while adding Record
		 * Error Msg: It looks like someone else already deleted this cell.
		 */
		addWorkSheet(name, name, 2, cols);
	}
	
	/**
	 * Add WorkSheet <br/>
	 * It will create a Table for this WorkSheet. WorkSheet and Table Name will be the same
	 * The Table will be used for Add/Edit/Delete Records
	 * <br/>
	 * 
	 * @param name Name of the WorkSheet
	 * @param description Description of the WorkSheet 
	 * @param row Number of Rows
	 * @param cols Name of Columns
	 */
	public void addWorkSheet(String name, String description, int row, String[] cols){
		
		if(name == null || description == null || cols == null){
			throw new IllegalAccessError("Please provide correct input parameters");
		}
		
		int col = cols.length;
		
		addWorkSheet(name, col, row);
		
		// Create a Table for this WorkSheet
		String tableCreateXML = "<entry xmlns='http://www.w3.org/2005/Atom' xmlns:gs='http://schemas.google.com/spreadsheets/2006'>"+
								"<title type='text'>"+ name +"</title>"+ // Table name will be same as WorkSheet Name
								"<summary type='text'>"+ description +"</summary>"+
								"<gs:worksheet name='"+ name +"'/>"+
								"<gs:header row='1' />"+
								"<gs:data numRows='0' startRow='2'>";
		for(int i=0; i<cols.length; i++){
			// "index" attributes are mandatory
			tableCreateXML = tableCreateXML.concat("<gs:column index='"+ (i+1) +"' name='"+ cols[i] +"' />");
		}
		tableCreateXML = tableCreateXML.concat("</gs:data></entry>");
		
		// Add headers
		HashMap<String, String> httpHeaders = new HashMap<String, String>();
		httpHeaders.put(HttpConHandler.AUTHORIZATION_HTTP_HEADER, "GoogleLogin auth="+ SpreadSheetFactory.authToken);
		httpHeaders.put(HttpConHandler.GDATA_VERSION_HTTP_HEADER, "3.0");
		httpHeaders.put(HttpConHandler.CONTENT_TYPE_HTTP_HEADER, "application/atom+xml");
		
		// HTTP Connection
		String tableURL = "https://spreadsheets.google.com/feeds/"+ entry.getKey() +"/tables";
		HttpConHandler http = new HttpConHandler();
		http.doConnect(tableURL, HttpConHandler.HTTP_POST, httpHeaders, tableCreateXML);
	}
	

	/**
	 * Create List feed based WorkSheet
	 * 
	 * @param name Name of the WorkSheet
	 * @param rowCount Number of row. This doesn't limit future row addition.
	 * @param cols Array of column name. First row of the WorkSheet is header row.
	 * 
	 * @return
	 */
	public WorkSheet addListWorkSheet(String name, int rowCount, String[] cols){
		if(name == null || cols == null){
			throw new IllegalAccessError("Please provide correct input parameters");
		}
		
		/*
		 * Steps-
		 * 1. Create an empty Worksheet with specified number of Rows and Columns
		 * 2. Create the Header Row
		 */
		// Create the 
		Log.p(TAG, "## Create WorkSheet...");
		WorkSheet workSheet = addWorkSheet(name, cols.length, rowCount);
		
		if(workSheet == null)
			return null;
		
		workSheet.setColumns(cols);
		
		// 2. Create Header Row
		// Add headers
		HashMap<String, String> httpHeaders = new HashMap<String, String>();
		httpHeaders.put(HttpConHandler.AUTHORIZATION_HTTP_HEADER, "GoogleLogin auth="+ SpreadSheetFactory.authToken);
		httpHeaders.put(HttpConHandler.GDATA_VERSION_HTTP_HEADER, "3.0");
		httpHeaders.put(HttpConHandler.CONTENT_TYPE_HTTP_HEADER, "application/atom+xml");
		// Ignore updates done by other on the Header Row
		httpHeaders.put("If-Match", "*");
		
		// XML to Add HEADER ROW
		StringBuffer cellUpdateXML = new StringBuffer();
		// Batch Cell Update
		// XML- Batch query for multiple Cell Update
		cellUpdateXML.append("<feed xmlns=\"http://www.w3.org/2005/Atom\" xmlns:batch=\"http://schemas.google.com/gdata/batch\" xmlns:gs=\"http://schemas.google.com/spreadsheets/2006\">");
		cellUpdateXML.append("<id>https://spreadsheets.google.com/feeds/cells/"+ entry.getKey() +"/"+ workSheet.getWorkSheetID() +"/private/full</id>");
		
		for(int i=0; i<cols.length; i++)
		{
			// Column Name should be lower_case and without space and special chars- Google SP API has limitations
			// One entry per column
			cellUpdateXML.append("<entry>");
			cellUpdateXML.append("<batch:id>B"+ i +"</batch:id>");
			cellUpdateXML.append("<batch:operation type=\"update\"/>");
			cellUpdateXML.append("<id>https://spreadsheets.google.com/feeds/cells/"+ entry.getKey() + "/"+ workSheet.getWorkSheetID() +"/private/full/R1C"+ (i+1) +"</id>");
			cellUpdateXML.append("<link rel=\"edit\" type=\"application/atom+xml\"");
			cellUpdateXML.append(" href=\"https://spreadsheets.google.com/feeds/cells/"+ entry.getKey() + "/"+ workSheet.getWorkSheetID() +"/private/full/R1C"+ (i+1) +"\"/>");
			cellUpdateXML.append("<gs:cell row=\"1\" col=\""+ (i+1) +"\" inputValue=\""+ cols[i].toLowerCase() +"\"/>");
			cellUpdateXML.append("</entry>");
		}
		cellUpdateXML.append("</feed>");
		
		// Do Server transaction
		String headerRowUpdateUrl = "https://spreadsheets.google.com/feeds/cells/"+ entry.getKey() + "/"+ workSheet.getWorkSheetID() +"/private/full/batch";
		new HttpConHandler().doConnect(headerRowUpdateUrl, HttpConHandler.HTTP_POST, httpHeaders, cellUpdateXML.toString());
		
		return workSheet;
	}
	
	/**
	 * It will retrieve all WorkSheets of this SpreadSheet from Server
	 * 
	 * @return
	 */
	public ArrayList<WorkSheet> getAllWorkSheets(){
		return getAllWorkSheets(true);
	}
	
	/**
	 * It will retrieve all WorkSheets of this SpreadSheet either from Server or from Local Cache
	 *  
	 * @param doRefresh Do you want to Synch with Server ?
	 * @return list of available WorkSheets
	 */
	public ArrayList<WorkSheet> getAllWorkSheets(boolean doRefresh){
		return getAllWorkSheets(doRefresh, null, false);
	}

	/**
	 * It will retrieve WorkSheets with matching title from Server
	 *  
	 * @param title SpreadSheet title. <b>null</b> means all SpreadSheets. No need to do URL encode.
	 * @param isTitleExact whether title string should be an exact match
	 * @return
	 */
	public ArrayList<WorkSheet> getWorkSheet(String title, boolean isTitleExact){
		return getAllWorkSheets(true, title, isTitleExact);
	}
	
	/**
	 * It will retrieve WorkSheets with matching title either from Server or from Local Cache
	 *  
	 * @param doRefresh Do you want to Synch with Server ?
	 * @param title SpreadSheet title. <b>null</b> means all SpreadSheets. No need to do URL encode. 
	 * @param isTitleExact Whether title string should be an exact match
	 * @return list of available WorkSheets
	 */
	public ArrayList<WorkSheet> getAllWorkSheets(boolean doRefresh, String title, boolean isTitleExact){
		// Sample URL: https://spreadsheets.google.com/feeds/worksheets/key/private/full
		/*
		 * TODO:
		 * Check with other projection values instead of "private" and "full"
		 * private - unpublished work sheets
		 * public- published work sheets
		 */
		
		if(!doRefresh){
			// Don't synch with Server
			return wks;
		}
		
		String workSheetListURL = "https://spreadsheets.google.com/feeds/worksheets/"+ entry.getKey() +"/private/full";
		
		if(title != null){
			// WorkSheet Query Parameters
			workSheetListURL = workSheetListURL.concat("?title="+ HttpConHandler.encode(title));
			workSheetListURL = workSheetListURL.concat("&title-exact="+ isTitleExact);
		}
		
		// Add Headers
		HashMap<String, String> httpHeaders = new HashMap<String, String>();
		httpHeaders.put(HttpConHandler.AUTHORIZATION_HTTP_HEADER, "GoogleLogin auth="+ SpreadSheetFactory.authToken);
		httpHeaders.put(HttpConHandler.GDATA_VERSION_HTTP_HEADER, "3.0");
		
		HttpConHandler http = new HttpConHandler();
		Response res = http.doConnect(workSheetListURL, HttpConHandler.HTTP_GET, httpHeaders, null);
		
		if(res.isError()){
			return null;
		}
		
		String xmlOut = res.getOutput();
		
		// XML Parsing
		ParseFeed pf = new ParseFeed();
		Feed f = pf.parse(xmlOut.getBytes());
		ArrayList<Entry> entries = f.getEntries();
		
		HashMap<String, Table> tables = null;
		
		if(entries != null && entries.size() > 0){
			// Fetch Table details of each Work Sheet
			Log.p(TAG, "Get Table Feed");
			tables = getTables(title, isTitleExact);
		}else{
			//No WorkSheet exists
			return null;
		}
		
		// clear existing entries
		wks.clear();
		
		for(int i=0; i<entries.size(); i++){
			Entry e = entries.get(i);
			
			WorkSheet ws = new WorkSheet();
			
			ws.setWorkSheetID(e.getKey());
			ws.setWorkSheetURL(e.getWorkSheetURL());
			ws.setTitle(e.getTitle());
			ws.setColCount(e.getColCount());
			ws.setRowCount(e.getRowCount());
			ws.setEntry(e);
			
			// Table name and WorkSheet name will be same
			if(tables != null){
				Table table = tables.get(ws.getTitle());
				if(table != null){
					// This WorkSheet is Table Feed based
					ws.setTable(tables.get(ws.getTitle()));
				}
				else{
					// This WorkSheet is List Feedback
					// Read HEADER ROW
					ws.setColumns(readHeaderRow(ws.getWorkSheetID()));
				}
			}
			wks.add(ws);
		}
		
		return wks;
	}
	
	/**
	 * Read clumns of List Feed based WorkSheet. Columns are placed in Header Row.
	 * 
	 * @param workSheetID
	 * @return
	 */
	private String[] readHeaderRow(String workSheetID){
		// Read the first Row
		String headerRowURL = "https://spreadsheets.google.com/feeds/cells/"+ entry.getKey() +"/"+ workSheetID +"/private/full?min-row=1&max-row=1";
		// Add Headers
		HashMap<String, String> httpHeaders = new HashMap<String, String>();
		httpHeaders.put(HttpConHandler.AUTHORIZATION_HTTP_HEADER, "GoogleLogin auth="+ SpreadSheetFactory.authToken);
		httpHeaders.put(HttpConHandler.GDATA_VERSION_HTTP_HEADER, "3.0");
		
		HttpConHandler http = new HttpConHandler();
		Response res = http.doConnect(headerRowURL, HttpConHandler.HTTP_GET, httpHeaders, null);
		
		if(res.isError()){
			return null;
		}
		String xmlOut = res.getOutput();
		
		// XML Parsing
		ParseFeed pf = new ParseFeed();
		Feed f = pf.parse(xmlOut.getBytes());
		ArrayList<Entry> entries = f.getEntries();
		
		if(entries == null)
			return null;
		
		String[] cols = new String[entries.size()];
		
		for(int i=0; i<entries.size(); i++)
		{
			Entry e = entries.get(i);
			WorkSheetCell cellInfo = e.getCellInfo();
			if(cellInfo != null)
				cols[i] = cellInfo.getName();
		}
		return cols;
	}
	
	/**
	 * Get details of associated table. WorkSheet and Table Name will be the same
	 * 
	 * @param title SpreadSheet title. <b>null</b> means all SpreadSheets. No need to do URL encode. 
	 * @param isTitleExact whether title string should be an exact match
	 * 
	 * @return
	 */
	private HashMap<String, Table> getTables(String title, boolean isTitleExact){
		
		//Get list of all Tables- one per WorkSheet
		HashMap<String, Table> tables = new HashMap<String, Table>();
		
		// Table URL- Get list of all Tables- one per WorkSheet
		String tableURL = "https://spreadsheets.google.com/feeds/"+ entry.getKey() +"/tables";
		
		if(title != null){
			tableURL = tableURL.concat("?title="+ HttpConHandler.encode(title));
			tableURL = tableURL.concat("&title-exact="+ isTitleExact);
		}
		
		// Add Headers
		HashMap<String, String> httpHeaders = new HashMap<String, String>();
		httpHeaders.put(HttpConHandler.AUTHORIZATION_HTTP_HEADER, "GoogleLogin auth="+ SpreadSheetFactory.authToken);
		httpHeaders.put(HttpConHandler.GDATA_VERSION_HTTP_HEADER, "3.0");
		
		HttpConHandler http = new HttpConHandler();
		Response res = http.doConnect(tableURL, HttpConHandler.HTTP_GET, httpHeaders, null);
		
		if(res.isError()){
			return null;
		}
		
		String xmlOut = res.getOutput();
		
		// XML Parsing
		ParseFeed pf = new ParseFeed();
		Feed f = pf.parse(xmlOut.getBytes());
		
		ArrayList<Entry> entries = f.getEntries();
		
		if(entries == null) // No Table exists for this WorkSheet/SpreadSheet
			return null;
		
		for(int i=0; i<entries.size(); i++){
			Entry e = entries.get(i);
			Table t = new Table();
			t.setId(e.getKey());
			t.setName(e.getTitle());
			t.setDescription(e.getSummary());
			t.setCols(e.getCols());
			// TODO: how to identify table of a WorkSheet
			t.setUrl("https://spreadsheets.google.com/feeds/"+ entry.getKey() +"/tables/"+ t.getId());
			
			Log.p(TAG, "Table Name: "+ t.getName());
			Log.p(TAG, "Table ID: "+ t.getId());
			Log.p(TAG, "Table URL: "+ t.getUrl());
			Log.p(TAG, "Number of Cols: "+ t.getCols().size());
			
			tables.put(e.getTitle(), t);
		}
		
		return tables;
	}
	
	/**
	 * Delete this WorkSheet
	 * @param wk WorkSheet to be deleted
	 */
	public void deleteWorkSheet(WorkSheet wk){
		if(wk != null)
			wk.delete();
		wks.remove(wk);
	}
}
