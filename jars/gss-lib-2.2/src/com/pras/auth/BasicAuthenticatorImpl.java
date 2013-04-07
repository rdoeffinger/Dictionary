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

package com.pras.auth;

import com.pras.Log;
import com.pras.conn.HttpConHandler;
import com.pras.conn.Response;

/**
 * This will provide a basic and LOW Level way of getting Authetication Token
 * for various Google's services.
 * This is a generic approach for core Java. User can add Platform specific Autheticator
 * implementation.
 * @author Prasanta Paul
 *
 */
public class BasicAuthenticatorImpl implements Authenticator {

	final String TAG = "BasicAuthenticatorImpl";
	// Google Authentication URL
	private final String GOOGLE_CLIENT_LOGIN_URL = "https://www.google.com/accounts/ClientLogin";
	Account account;
	
	public BasicAuthenticatorImpl(Account account){
		this.account = account;
	}
	
	/* (non-Javadoc)
	 * @see com.pras.auth.Authenticator#getAuthToken(java.lang.String)
	 */
	public String getAuthToken(String service) {
		if(account == null){
			Log.p(TAG, "No Account Info "+ account);
		}
			
		// TODO Auto-generated method stub
		String postData = "accountType="+ account.getAccountType() +"&Email="+ account.getEmail() +"&Passwd="+ account.getPassword() +"&service="+ service +"&source=test-app-log";
		
		HttpConHandler http = new HttpConHandler();
		Response res = http.doConnect(GOOGLE_CLIENT_LOGIN_URL, HttpConHandler.HTTP_POST, null, postData);
		
		if(res.isError()){
			return null;
		}
		
		String out = res.getOutput();
		/*
		 * Format of success response
		 * SID=<>LSID=<>Auth=<>
		 */
		String[] parms = out.split("=");
		String authToken = null;
		for(int i=0; i<parms.length; i++){
			if(parms[i].toLowerCase().endsWith("auth")){
				authToken = parms[i+1];
				break;
			}
		}
		return authToken;
	}
}
