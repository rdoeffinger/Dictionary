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

import com.pras.Log;

/**
 * Wrapper class to hold Response from Server, Connection Exception etc.
 * 
 * @author rasanta
 *
 */
public class Response {

	String TAG = "HTTP_Response";
	
	boolean error = false;
	String responseCode;
	String responseMessage;
	String errorStreamMsg;
	String output;
	Exception exception;
	
	
	public boolean isError() {
		return error;
	}
	public void setError(boolean error) {
		this.error = error;
	}
	public String getResponseCode() {
		return responseCode;
	}
	public void setResponseCode(String responseCode) {
		this.responseCode = responseCode;
		
		/*
		 * Response Codes-
		 * 1xx: Informational
		 * 2xx: Success
		 * 3xx: Redirection
		 * 4xx: Client Error
		 * 5xx: Server Error
		 */
	   
		if(this.responseCode.startsWith("2"))
			error = false;
		else
			error = true;
	}
	public String getResponseMessage() {
		return responseMessage;
	}
	public void setResponseMessage(String responseMessage) {
		this.responseMessage = responseMessage;
	}
	public String getErrorStreamMsg() {
		return errorStreamMsg;
	}
	public void setErrorStreamMsg(String errorStreamMsg) {
		this.errorStreamMsg = errorStreamMsg;
	}
	public String getOutput() {
		return output;
	}
	public void setOutput(String output) {
		this.output = output;
	}
	public Exception getException() {
		return exception;
	}
	public void setException(Exception exception) {
		this.exception = exception;
	}
	
	public void printErrorLog(){
		Log.p(TAG, "HTTP Response Code: "+ getResponseCode());
		Log.p(TAG, "HTTP Response: "+ getResponseMessage());
		Log.p(TAG, "Error Msg from Server: "+ getErrorStreamMsg());
		if(exception != null){
			Log.p(TAG, "Error in Connection: "+ exception.getMessage());
			exception.printStackTrace();
		}
	}
}
