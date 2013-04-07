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

/**
 * This class is to hold Spreadsheet Feed data
 * @author Prasanta Paul
 *
 */
public class Feed {

	String id;
	/**
	 * key associated with each feed. if feed changes, so as its value
	 */
	String etag;
	String title;
	ArrayList<Entry> entries;
	
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	public String getEtag() {
		return etag;
	}
	public void setEtag(String etag) {
		this.etag = etag;
	}
	public String getTitle() {
		return title;
	}
	public void setTitle(String title) {
		this.title = title;
	}
	public ArrayList<Entry> getEntries() {
		return entries;
	}
	public void addEntry(Entry e) {
		if(entries == null)
			entries = new ArrayList<Entry>();
		entries.add(e);
	}
	public void clearEntries(){
		if(entries != null)
			entries.clear();
	}
}
