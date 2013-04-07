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

import com.pras.auth.Account;
import com.pras.auth.Authenticator;
import com.pras.auth.BasicAuthenticatorImpl;
import com.pras.conn.HttpConHandler;
import com.pras.conn.Response;
import com.pras.sp.Entry;
import com.pras.sp.Feed;
import com.pras.sp.ParseFeed;

/**
 * <p>
 * It is a SpreadSheet Generator Class. It accepts Gmail User ID and PassWord
 * to generate Authentication Token.
 * <br/>
 * It uses 2 Google APIs-
 * <br/>
 * SpreadSheet Create/Delete - <a href="http://code.google.com/apis/documents/docs/3.0/developers_guide_protocol.html">Google Document API</a>
 * <br/>
 * WorkSheet Create/Delete, Record Add - <a href="http://code.google.com/apis/spreadsheets/data/3.0/developers_guide.html">Google SpreadSheet API</a>
 *
 * @author Prasanta Paul
 *
 */
public class SpreadSheetFactory {

	private String TAG = "SpreadSheetFactory";
	
//	private String userName;
//	private String password;
	public static String authToken;
	// URL
	//private final String GOOGLE_CLIENT_LOGIN_URL = "https://www.google.com/accounts/ClientLogin";
	
	private final String SP_GET_LIST_URL = "https://spreadsheets.google.com/feeds/spreadsheets/private/full";
	private final String DOCUMENT_LIST_API_URL = "https://docs.google.com/feeds/default/private/full";
	// Service Name
	/*
	 * Google service name (Reference)-
	 * http://code.google.com/apis/gdata/faq.html
	 */
	final static String SPREADSHEET_API_SERVICE_NAME = "wise";
	final static String DOCUMENT_LIST_API_SERVICE_NAME = "writely";
	
	/**
	 * List of presently stored SpreadSheets
	 */
	private ArrayList<SpreadSheet> spreadSheets = new ArrayList<SpreadSheet>();
	
	private Authenticator authenticator = null;
	
	private static SpreadSheetFactory factory;
	
	/**
	 * This will return an existing SpreadSheetFactory instance or null
	 * </br>
	 * <b>Note:</b>
	 * Make sure you have previously called getInstance(String userName, String password)
	 * 
	 * @return
	 */
	public static SpreadSheetFactory getInstance(){
		return getInstance(null, null);
	}
	
	/**
	 * This will create SpreadSheetFactory Instance with valid User ID (e.g. abc@gmail.com) and password
	 * 
	 * @param userName Gmail account id e.g. abc@gmail.com
	 * @param password Gmail account password
	 * @return
	 */
	public static SpreadSheetFactory getInstance(String email, String password){
		if(factory == null){
			if(email != null && password != null){
				Account account = new Account();
				account.setEmail(email);
				account.setPassword(password);
				BasicAuthenticatorImpl basicAuth = new BasicAuthenticatorImpl(account);
				
				factory = new SpreadSheetFactory(basicAuth);
				
			}else{
				throw new IllegalAccessError("Missing Account Info. Please use getInstance(String email, String password) or getInstance(Authenticator authenticator)");
			}
		}
		return factory;
	}
	
	/**
	 * This will create SpreadSheetFactory Instance using your custom Authenticatior.
	 * 
	 * Use this if you want to use your custom Authenticator e.g. in Android you can use AccountManager
	 * to create a custom Authenticator.
	 * 
	 * @param authenticator Your Custom Authenticator. 
	 * @return
	 */
	public static SpreadSheetFactory getInstance(Authenticator authenticator){
		if(authenticator == null){
			throw new IllegalAccessError("No Authenticator defined");
		}
		if(factory == null)
			factory = new SpreadSheetFactory(authenticator);
		return factory;
	}
	
	/**
	 * Provide Gmail user id and password. Use SpreadSheetFactory instance
	 * to generate create/list/delete SpreadSheet
	 * 
	 * @param userName
	 * @param password
	 */
//	private SpreadSheetFactory(String userName, String password){
//		this.userName = userName;
//		this.password = password;
//	}
	
	private SpreadSheetFactory(Authenticator authenticator){
		this.authenticator = authenticator;
	}
	
	/**
	 * Deallocate SpreadSheetFactory instance
	 */
	public void flushMe(){
		factory = null;
		if(spreadSheets != null)
			spreadSheets.clear();
		spreadSheets = null;
		authToken = null;
		authenticator = null;
	}
	
	/**
	 *Create SpreadSheet with the given name
	 * 
	 * @param spName SpreadSheet name
	 */
	public void createSpreadSheet(String spName){
		// login for Document List API
		login(DOCUMENT_LIST_API_SERVICE_NAME);
		
		// Create a SpreadSheet
		String postData = "<?xml version='1.0' encoding='UTF-8'?>" +
						  "<entry xmlns='http://www.w3.org/2005/Atom'>"+
						  "<category scheme='http://schemas.google.com/g/2005#kind'"+
						  " term='http://schemas.google.com/docs/2007#spreadsheet'/>"+
						  "<title>"+ spName +"</title>"+
						  "</entry>";
		// Add Headers
		HashMap<String, String> httpHeaders = new HashMap<String, String>();
		httpHeaders.put(HttpConHandler.AUTHORIZATION_HTTP_HEADER, "GoogleLogin auth="+ authToken);
		httpHeaders.put(HttpConHandler.GDATA_VERSION_HTTP_HEADER, "3.0");
		httpHeaders.put(HttpConHandler.CONTENT_LENGTH_HTTP_HEADER, ""+ postData.length());
		httpHeaders.put(HttpConHandler.CONTENT_TYPE_HTTP_HEADER, "application/atom+xml");
		
		// Http Connection
		HttpConHandler http = new HttpConHandler();
		http.doConnect(DOCUMENT_LIST_API_URL, HttpConHandler.HTTP_POST, httpHeaders, postData);
		
		// login for SpreadSheet API
		// revert back to SpreadSheet Auth Token
		login(SPREADSHEET_API_SERVICE_NAME);
	}
	
	/**
	 * Delete a SpreadSheet
	 * 
	 * @param resID Resource ID of the SpreadSheet you want to Delete
	 */
	public void deleteSpreadSheet(String resID){
		
		// login for Document List API
		login(DOCUMENT_LIST_API_SERVICE_NAME);
		
		// Delete HTTP request
		String url = "https://docs.google.com/feeds/default/private/full/"+ resID +"?delete=true";
		// Add Headers
		HashMap<String, String> httpHeaders = new HashMap<String, String>();
		httpHeaders.put(HttpConHandler.AUTHORIZATION_HTTP_HEADER, "GoogleLogin auth="+ SpreadSheetFactory.authToken);
		httpHeaders.put(HttpConHandler.GDATA_VERSION_HTTP_HEADER, "3.0");
		httpHeaders.put("If-Match", "*");
		
		HttpConHandler http = new HttpConHandler();
		Response res = http.doConnect(url, HttpConHandler.HTTP_DELETE, httpHeaders, null);
		
		if(!res.isError()){
			for(int i=0; i<spreadSheets.size(); i++){
				if(spreadSheets.get(0).getEntry().getResID() == resID)
					spreadSheets.remove(i);
			}
		}
		
		// login for SpreadSheet API
		// revert back to SpreadSheet Auth Token
		login(SPREADSHEET_API_SERVICE_NAME);
	}
	
	/**
	 * Get list of SpreadSheet with matching title. It will do Synch with Server
	 * 
	 * @param title SpreadSheet title
	 * @param isTitleExact Whether title string should be an exact match
	 * 
	 * @return
	 */
	public ArrayList<SpreadSheet> getSpreadSheet(String title, boolean isTitleExact){
		return getAllSpreadSheets(true, title, isTitleExact);
	}
	
	/**
	 * Get All stored SpreadSheets from Server
	 * 
	 * @return
	 */
	public ArrayList<SpreadSheet> getAllSpreadSheets(){
		return getAllSpreadSheets(true);
	}
	
	/**
	 * Get All stored SpreadSheets either from Server or Local Cache
	 * 
	 * @param doRefresh Do you want to Synch with Server ?
	 * @return List of Entry. Each Entry represents a SpreadSheet
	 */
	public ArrayList<SpreadSheet> getAllSpreadSheets(boolean doRefresh){
		return getAllSpreadSheets(doRefresh, null, false);
	}

	/**
	 * Get All stored SpreadSheets either from Server or Local Cache
	 * 
	 * @param doRefresh
	 * @param title SpreadSheet title. <b>null</b> means all SpreadSheets. No need to do URL encode. 
	 * @param isTitleExact Whether title string should be an exact match
	 * @return
	 */
	public ArrayList<SpreadSheet> getAllSpreadSheets(boolean doRefresh, String title, boolean isTitleExact){
		
		/*
		 *  TODO:
		 * 	Retrieve data only if there is change in Feed
		 * 	If-None-Match: W/"D0cERnk-eip7ImA9WBBXGEg."
		 */
		
		if(!doRefresh){
			// Don't synch with Server
			return spreadSheets;
		}
		
		// login to Spreadsheet Service
		login(SPREADSHEET_API_SERVICE_NAME);
		
		// get list of all spreadsheets
		String xmlOut = getSpreadSheetList(title, isTitleExact);
		
		if(xmlOut == null){
			Log.p(TAG, "No SpreadSheet Feed received from Server!!");
			return null;
		}
		
		// XML Parsing
		ParseFeed pf = new ParseFeed();
		Feed f = pf.parse(xmlOut.getBytes());
		ArrayList<Entry> entries = f.getEntries();
		
		if(entries == null){
			// No SpreadSheet exists
			return null;
		}
		
		// Refresh SpreadSheet List from Server
		// Clear existing list
		spreadSheets.clear();
		
		for(int i=0; i<entries.size(); i++){
			spreadSheets.add(new SpreadSheet(entries.get(i)));
		}
		
		return spreadSheets;
	}
	
	/**
	 * Share a SpreadSheet with a given list of Collaborators.
	 * NOTE: You need not to mention editLink of Collaborator
	 * 
	 * @param sp The SpreadSheet you want to share
	 * @param collaborators List of Collaborators with whim you want to share this.
	 */
	public void addSharePermission(SpreadSheet sp, Collaborator[] collaborators){
		
		if(sp == null || collaborators == null || collaborators.length == 0){
			throw new IllegalArgumentException("Please provide SpreadSheet ResID and Collaborator details to whom you want to share.");
		}
		
		// Get Document List API Authentication Token
		login(DOCUMENT_LIST_API_SERVICE_NAME);
		
		// Share Doc
		String postData = "";
		for(int i=0; i<collaborators.length; i++){
			
			Collaborator c = collaborators[i];
			
			postData = "<entry xmlns='http://www.w3.org/2005/Atom' xmlns:gAcl='http://schemas.google.com/acl/2007'>"+
					   "<category scheme='http://schemas.google.com/g/2005#kind' term='http://schemas.google.com/acl/2007#accessRule'/>"+
					   "<gAcl:role value='"+ c.getRole() +"'/>"+
					   "<gAcl:scope type='"+ c.getScopeType() +"' value='"+ c.getScopeValue() +"'/>"+
					   "</entry>";
			
			// Send Data
			HashMap<String, String> httpHeaders = new HashMap<String, String>();
			httpHeaders.put(HttpConHandler.AUTHORIZATION_HTTP_HEADER, "GoogleLogin auth="+ authToken);
			httpHeaders.put(HttpConHandler.GDATA_VERSION_HTTP_HEADER, "3.0");
			httpHeaders.put(HttpConHandler.CONTENT_TYPE_HTTP_HEADER, "application/atom+xml");

			// HTTP Connection
			HttpConHandler http = new HttpConHandler();
			String url = "https://docs.google.com/feeds/default/private/full/"+ sp.getResourceID() +"/acl";
			
			http.doConnect(url, HttpConHandler.HTTP_POST, httpHeaders, postData);
		}
		
		// Get SpreadSheet API Authentication Token
		login(SPREADSHEET_API_SERVICE_NAME);
		
	}
	
	/**
	 * Get list of all Collaborators to whom this SpreadSheet is shared
	 * @param sp SpreadSheet
	 * @return
	 */
	public ArrayList<Collaborator> getAllCollaborators(SpreadSheet sp){
		
		if(sp == null){
			throw new IllegalArgumentException("Please provide SpreadSheet ResID and Emails to whom you want to share.");
		}
		
		// Get Document List API Authentication Token
		login(DOCUMENT_LIST_API_SERVICE_NAME);
		
		HashMap<String, String> httpHeaders = new HashMap<String, String>();
		httpHeaders.put(HttpConHandler.AUTHORIZATION_HTTP_HEADER, "GoogleLogin auth="+ authToken);
		httpHeaders.put(HttpConHandler.GDATA_VERSION_HTTP_HEADER, "3.0");
		
		HttpConHandler http = new HttpConHandler();
		String url = "https://docs.google.com/feeds/default/private/full/"+ sp.getResourceID() +"/acl";
		Response res = http.doConnect(url, HttpConHandler.HTTP_GET, httpHeaders, null);
		
		// Get SpreadSheet API Authentication Token
		login(SPREADSHEET_API_SERVICE_NAME);
		
		// Process output
		String xmlOut = res.getOutput();
		
		if(xmlOut == null){
			Log.p(TAG, "No XML feed from Server!!");
			return null;
		}
		
		// XML Parsing
		ParseFeed pf = new ParseFeed();
		Feed f = pf.parse(xmlOut.getBytes());
		ArrayList<Entry> entries = f.getEntries();
		
		sp.clearCollaboratorList();
		
		if(entries != null){
			// Create Collaborator Instances
			System.out.println("Number of Collaborators: "+ entries.size());
			
			for(int i=0; i<entries.size(); i++){
				Entry e = entries.get(i);
				Collaborator c = new Collaborator();
				c.setEditLink(e.getEditLink());
				c.setScopeType(e.getAclScopeType());
				c.setScopeValue(e.getAclScopeValue());
				c.setRole(e.getAclRole());
				
				sp.addCollaborator(c);
			}
		}
		
		return sp.getCollaborators();
	}
	
	/**
	 * Change Share permission for a particular ACL entry
	 * 
	 * @param c Collaborator instance stored in SpreadSheet. It should have a valid EditLink URL
	 * @param role {owner, writer, reader} 
	 */
	public void changeSharePermission(Collaborator c, String role){
		// Get Document List API Authentication Token
		login(DOCUMENT_LIST_API_SERVICE_NAME);
		
		if(c.getEditLink() == null){
			throw new IllegalArgumentException("No EditLink URL defined in the Collaborator Instance");
		}
		
		String url = c.getEditLink();
		
		String postData = "<entry xmlns='http://www.w3.org/2005/Atom' xmlns:gAcl='http://schemas.google.com/acl/2007'>"+
						  "<category scheme='http://schemas.google.com/g/2005#kind' term='http://schemas.google.com/acl/2007#accessRule'/>"+
						  "<gAcl:role value='"+ role +"'/>"+
						  "<gAcl:scope type='"+ c.getScopeType() +"' value='"+ c.getScopeValue() +"'/>"+
						  "</entry>";
		
		// HTTP Header- Send PUT request
		HashMap<String, String> httpHeaders = new HashMap<String, String>();
		httpHeaders.put(HttpConHandler.AUTHORIZATION_HTTP_HEADER, "GoogleLogin auth="+ authToken);
		httpHeaders.put(HttpConHandler.GDATA_VERSION_HTTP_HEADER, "3.0");
		httpHeaders.put(HttpConHandler.CONTENT_TYPE_HTTP_HEADER, "application/atom+xml");

		// HTTP Connection
		HttpConHandler http = new HttpConHandler();
		http.doConnect(url, HttpConHandler.HTTP_PUT, httpHeaders, postData);
		
		// Get SpreadSheet API Authentication Token
		login(SPREADSHEET_API_SERVICE_NAME);
	}
	
	/**
	 * Remove Share access of a selected user.
	 * @param c Collaborator Instance. It should have the Edit Link.
	 */
	public void removeSharePermission(Collaborator c){
		// Get Document List API Authentication Token
		login(DOCUMENT_LIST_API_SERVICE_NAME);
		
		// HTTP Header- Send PUT request
		HashMap<String, String> httpHeaders = new HashMap<String, String>();
		httpHeaders.put(HttpConHandler.AUTHORIZATION_HTTP_HEADER, "GoogleLogin auth="+ authToken);
		httpHeaders.put(HttpConHandler.GDATA_VERSION_HTTP_HEADER, "3.0");
		
		HttpConHandler http = new HttpConHandler();
		String url = c.getEditLink();
		
		http.doConnect(url, HttpConHandler.HTTP_DELETE, httpHeaders, null);
		
		// Get SpreadSheet API Authentication Token
		login(SPREADSHEET_API_SERVICE_NAME);
	}
	
	/**
	 * Login to get Authentication Token
	 * @param service
	 */
	private void login(String service){
		// HTTP POST Data
//		String postData = "accountType=GOOGLE&Email="+ userName +"&Passwd="+ password +"&service="+ service +"&source=test-app-log";
//		
//		HttpConHandler http = new HttpConHandler();
//		Response res = http.doConnect(GOOGLE_CLIENT_LOGIN_URL, HttpConHandler.HTTP_POST, null, postData);
//		
//		if(res.isError()){
//			return;
//		}
//		
//		String out = res.getOutput();
//		/*
//		 * Format of success response
//		 * SID=<>LSID=<>Auth=<>
//		 */
//		String[] parms = out.split("=");
//		
//		for(int i=0; i<parms.length; i++){
//			if(parms[i].toLowerCase().endsWith("auth")){
//				authToken = parms[i+1];
//				break;
//			}
//		}
//		Log.p(TAG, "AuthToken="+ authToken);
		// A generic approach to add Authenticator Plug-in
		Log.p(TAG, "New Auth...");
		authToken = authenticator.getAuthToken(service);
	}
	
	/**
	 * Get a list of stored SpreadSheets
	 * @return
	 */
	private String getSpreadSheetList(String title, boolean isTitleExact){
		
		HashMap<String, String> httpHeaders = new HashMap<String, String>();
		httpHeaders.put(HttpConHandler.AUTHORIZATION_HTTP_HEADER, "GoogleLogin auth="+ authToken);
		httpHeaders.put(HttpConHandler.GDATA_VERSION_HTTP_HEADER, "3.0");
		
		HttpConHandler http = new HttpConHandler();
		String url = SP_GET_LIST_URL;
		
		// Add SpreadSheet Query Params (title and title-exact)
		if(title != null){
			url = url.concat("?title="+ HttpConHandler.encode(title));
			url = url.concat("&title-exact="+ isTitleExact);
		}
		
		Response res = http.doConnect(url, HttpConHandler.HTTP_GET, httpHeaders, null);
		
		if(res.isError()){
			return null;
		}
		
		return res.getOutput(); 
	}
}
