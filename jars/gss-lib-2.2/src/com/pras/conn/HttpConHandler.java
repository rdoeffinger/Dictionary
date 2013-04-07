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

package com.pras.conn;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Iterator;

import com.pras.Log;

/**
 * HTTP Connection handler, supports GET, POST, PUT and DELETE.
 * 
 * @author Prasanta Paul
 */
public class HttpConHandler {
	
	/*
	 * HTTP Headers
	 */
	public static final String AUTHORIZATION_HTTP_HEADER = "Authorization";
	public static final String GDATA_VERSION_HTTP_HEADER = "GData-Version";
	public static final String CONTENT_LENGTH_HTTP_HEADER = "Content-Length";
	public static final String CONTENT_TYPE_HTTP_HEADER = "Content-Type";
	
	public static final int HTTP_GET = 0xA1;
	public static final int HTTP_POST = 0xA2;
	public static final int HTTP_DELETE = 0xA3;
	public static final int HTTP_PUT = 0xA4;
	
	/**
	 * @param urlStr HTTP URL
	 * @param type Type of Connection (POST, GET, PUT or DELETE)
	 * @param httpHeaders HTTP headers
	 * @param postData Data to be sent as a part of POST/PUT request
	 * 
	 * @return ATOM XML feed and Response/Error message
	 */
	public Response doConnect(String urlStr, int type, HashMap<String, String> httpHeaders, String postData) {
		
		String res = null;
		HttpURLConnection con = null;
		Response response = new Response();
		String TAG = "HttpConHandler";

		try{
			/*
			 * IMPORTANT: 
			 * User SHOULD provide URL Encoded Parms
			 */
			Log.p(TAG, "URL="+ urlStr);
			// TODO: Remove proxy
			// Somehow Eclipse is not detecting Proxy
			// HTTP Proxy
			System.getProperties().put("http.proxyHost", "168.219.61.250");
			System.getProperties().put("http.proxyPort", "8080");
			// HTTPS Proxy
			System.getProperties().put("https.proxyHost", "168.219.61.252");
			System.getProperties().put("https.proxyPort", "8080");
			URL url = new URL(urlStr);
			con = (HttpURLConnection) url.openConnection();
			
			//con.setInstanceFollowRedirects(false);
			
			OutputStream out = null;
			
			// Set headers
			/*
			 * All subsequent request to Google Spreadsheet/Data API
			 * should include following 2 Headers
			 */
			//con.setRequestProperty("Authorization", "GoogleLogin auth="+ authToken);
			//con.setRequestProperty("GData-Version", "3.0");
			
			if(httpHeaders != null){
				//System.out.println("Number of HTTP Headers: "+ httpHeaders.size());
				Iterator<String> keys = httpHeaders.keySet().iterator();
				while(keys.hasNext()){
					String k = keys.next();
					con.setRequestProperty(k, httpHeaders.get(k));
				}
			}
			
			if(type == HTTP_POST){
				con.setDoOutput(true);
				out = con.getOutputStream();
				out.write(postData.getBytes());
				out.flush();
			}
			else if(type == HTTP_GET){
				con.setDoInput(true);
			}
			else if(type == HTTP_DELETE){
				con.setRequestMethod("DELETE");
				con.connect();
			}
			else if(type == HTTP_PUT){
				con.setRequestMethod("PUT");
				// Send Data
				con.setDoOutput(true);
				out = con.getOutputStream();
				out.write(postData.getBytes());
				out.flush();
			}
			
			// Read Response Code
			response.setResponseCode(""+ con.getResponseCode());
			response.setResponseMessage(con.getResponseMessage());
			
			// Read InputStream
			BufferedReader reader = new BufferedReader(new InputStreamReader(con.getInputStream()));
			StringBuffer strBuf = new StringBuffer();
			String line = "";
			while((line = reader.readLine()) != null)
				strBuf.append(line);
			
			reader.close();
			
			if(out != null)
				out.close();
			
			res = strBuf.toString();
			
			response.setOutput(res);
			
			Log.p(TAG, "Response from Google Server: \n"+ res);
			
		}catch(Exception ex){
			
			Log.p(TAG, "Error in connection: "+ ex.toString());
			// Oops Exception
			response.setError(true);
			
			// Set Exception
			response.setException(ex);
			
			if(con == null)
				return response; 
			
			InputStream error_in = con.getErrorStream();
			
			if(error_in == null)
				return response;
			
			// Read the error stream
			BufferedReader reader = new BufferedReader(new InputStreamReader(error_in));
			if(reader == null)
				return response;
			
			StringBuffer errStrBuf = new StringBuffer();
			String line = "";
			
			try{
				while((line = reader.readLine()) != null)
					errStrBuf.append(line);
			
				// Set Error Stream Message
				response.setErrorStreamMsg(errStrBuf.toString());
			
				reader.close();
			
				// Display error on logging console
				response.printErrorLog();
			
			}catch(Exception e){
				Log.p(TAG, "Error in reading Stream: "+ e.getMessage());
			}
		}
		return response;
	}
	
	/**
	 * Encode URL parameters in UTF-8 format
	 * 
	 * @param str String to be URL encoded
	 * 
	 * @return
	 */
	public static String encode(String str){
		try{
			str = URLEncoder.encode(str, "UTF-8");
		}catch(Exception ex){ex.printStackTrace();}
		return str;
	}
}
