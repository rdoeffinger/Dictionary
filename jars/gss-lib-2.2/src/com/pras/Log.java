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
 * Based on your target platform- edit this file and add appropriate Log console
 * e.g. 
 * for Android, android.util.Log
 * for J2ME, Desktop File based logging.
 * </br>
 * By default Logging will be enabled
 * 
 * @author Prasanta Paul
 *
 */
public class Log {

	/**
	 * Set to false if you don't need Log output
	 */
	private static boolean isLogEnabled = true;
	
	
	/**
	 * Enable logging
	 */
	public static void enableLog(){
		isLogEnabled = true;
	}
	/**
	 * Disable logging. Good for production release.
	 */
	public static void disableLog(){
		isLogEnabled = false;
	}
	/**
	 * Pring Log message
	 * @param tag Log TAG
	 * @param msg Log Message
	 */
	public static void p(String tag, String msg){
		
		if(!isLogEnabled)
			return;
		
		if(tag != null)
			print("["+ tag +"] ");
		if(msg != null)
			print(msg);
		print("\n");
	}
	
	private static void print(String s){
		// TODO: Add appropriate stream based on your platform
		System.out.print(s);
	}
}
