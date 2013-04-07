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

import java.io.ByteArrayInputStream;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import com.pras.Log;
import com.pras.WorkSheetCell;


/**
 * Using SAX Parser, to keep the compatibility with Android
 * @author Prasanta Paul
 *
 */
public class ParseFeed extends DefaultHandler {

	private String TAG = "ParseFeed";
	
	// Attributes
	final String ATTRITUBE_ETAG = "gd:etag";
	final String ATTRITUBE_SRC = "src";
	// Nodes
	final String NODE_FEED = "feed";
	final String NODE_ENTRY = "entry"; 
	final String NODE_ID = "id";
	final String NODE_TITLE = "title";
	// Table Feed
	final String NODE_SUMMARY = "summary";
	final String NODE_CONTENT = "content";
	final String NODE_LINK = "link";
	final String NODE_NAME = "name";
	final String NODE_EMAIL = "email";
	// Nodes- specific to Work Sheet
	static String NODE_ROW_COUNT = "gs:rowCount";
	static String NODE_COL_COUNT = "gs:colCount";
	// Table Feed
	static String NODE_GS_DATA = "gs:data";
	static String NODE_GS_COL = "gs:column";
	// Nodes- specific to WorkSheet List Data Feed (gsx: namespace)
	final String NODE_GSX = "gsx:";
	// Cell based Feed
	final String NODE_GS_CELL = "gs:cell";
	final String ATTRITUBE_ROW = "row";
	final String ATTRITUBE_COL = "col";
	// Resource ID for SpreadSheet (Document List API)
	/**
	 * Not in use; 
	 * @deprecated
	 */
	final String NODE_GD_RESOURCEID = "gd:resourceId";
	
	// Table Record
	static String NODE_GS_FIELD = "gs:field";
	
	// ACL Fields
	final String NODE_GACL_ROLE = "gAcl:role";
	final String NODE_GACL_SCOPE = "gAcl:scope";
	
	// [Fix_11_JAN_2011_Prasanta For Android SDK
	/*
	 * Android SDK removes gs: prefix
	 */
	public static void doCustomizationForSDK(){
		NODE_GS_DATA = "data";
		NODE_GS_COL = "column";
		NODE_GS_FIELD = "field";
		NODE_ROW_COUNT = "rowCount";
		NODE_COL_COUNT = "colCount";
	}
	// Fix_11_JAN_2011_Prasanta]
	
	Feed f = null;
	Entry e = null;
	Field field = null;
	String node = null;
	
	public Feed parse(byte[] data){
		
		SAXParserFactory factory = SAXParserFactory.newInstance();
		try{
			SAXParser parser = factory.newSAXParser();
			parser.parse(new ByteArrayInputStream(data), this);
		}catch(Exception ex){
			Log.p(TAG, "Error in parsing: "+ ex.toString());
			ex.printStackTrace();
		}
		
		return f;
	}

	@Override
	public void characters(char[] ch, int start, int length)
			throws SAXException {
		super.characters(ch, start, length);
		
		if(node.equals(NODE_TITLE)){
			if(e == null)
				f.setTitle(new String(ch).substring(start, start+length));
			else
				e.setTitle(new String(ch).substring(start, start+length));
		}
		if(node.equals(NODE_SUMMARY)){
			if(e != null)
				e.setSummary(new String(ch).substring(start, start+length));
		}
		else if(node.equals(NODE_NAME)){
			if(e != null)
				e.setAuthorName(new String(ch).substring(start, start+length));
		}
		else if(node.equals(NODE_EMAIL)){
			if(e != null)
				e.setAuthorEmail(new String(ch).substring(start, start+length));
		}
		else if(node.equals(NODE_ID)){
			if(e == null)
				f.setId(new String(ch).substring(start, start+length));
			else
				e.setId(new String(ch).substring(start, start+length));
		}
		else if(node.equals(NODE_ROW_COUNT)){
			if(e != null)
				e.setRowCount(Integer.parseInt(new String(ch).substring(start, start+length)));
		}
		else if(node.equals(NODE_COL_COUNT)){
			if(e != null)
				e.setColCount(Integer.parseInt(new String(ch).substring(start, start+length)));
		}
		else if(node.toLowerCase().startsWith(NODE_GSX)){
			node = node.toLowerCase();
			WorkSheetCell cell = new WorkSheetCell();
			cell.setName(node.toLowerCase().substring(node.indexOf(":") + 1));
			cell.setValue(new String(ch).substring(start, start+length));
			// TODO: Cell Data Type
			if(e != null)
				e.addCell(cell);
		}
		else if(node.equals(NODE_GD_RESOURCEID)){
			e.setResID(new String(ch).substring(start, start+length));
		}
		else if(node.equals(NODE_GS_FIELD)){
			field.setValue(new String(ch).substring(start, start+length));
			e.addField(field);
		}
		else if(node.equals(NODE_GS_CELL)){
			// Cell Feed: gs:cell
			WorkSheetCell cellInfo = e.getCellInfo();
			if(cellInfo != null){
				cellInfo.setName(new String(ch).substring(start, start+length));
				if(e != null)
					e.setCellInfo(cellInfo);
			}
		}
	}

	@Override
	public void startDocument() throws SAXException {
		super.startDocument();
		f = new Feed();
	}

	@Override
	public void startElement(String uri, String localName, String name,
			Attributes attributes) throws SAXException {
		super.startElement(uri, localName, name, attributes);
		
		if(name.trim().length() == 0)
			node = localName;
		else
			node = name;
		
		//Log.p(TAG, "LocalName: "+ localName +" Name="+ name +" URI="+ uri);
		
		if(node.equals(NODE_FEED)){
			f = new Feed();
			// read ETag
			//f.setEtag(attributes.getValue("http://schemas.google.com/g/2005", "gd:etag"));
			f.setEtag(attributes.getValue("gd:etag"));
		}
		else if(node.equals(NODE_ENTRY)){
			//Log.p(TAG, "Entry...XML Feed");
			e = new Entry();
			// read ETag
			e.setETAG(attributes.getValue("gd:etag"));
		}
		else if(node.equals(NODE_CONTENT)){
			/*
			 * TODO:
			 * Fetch WorkSheet or Main access URL for any SpreadSheet or WorkSheet
			 * from <link rel="self">
			 */
			e.setWorkSheetURL(attributes.getValue("src"));
		}
		else if(node.equals(NODE_LINK)){
			if(e != null){
				// Consider Link only of Entry node
				String rel = attributes.getValue("rel");
				if(rel != null && rel.equals("alternate")){
					String href = attributes.getValue("href");
					int index = href.indexOf("?key=");
					if(index != -1)
						e.setResID("spreadsheet:"+ href.substring(index + 5));
				}
				else if(rel != null && rel.equals("edit")){
					// Read Edit Link
					e.setEditLink(attributes.getValue("href"));
				}
			}
		}
		else if(node.equals(NODE_GS_COL)){
			String colName = attributes.getValue("name");
			//Log.p(TAG, "[gs:column:] node..."+ colName);
			if(colName != null)
				e.addCol(colName);
		}
		else if(node.equals(NODE_GS_FIELD)){
			String fieldName = attributes.getValue("name");
			if(fieldName != null){
				field = new Field();
				field.setColName(fieldName);
				field.setIndex(attributes.getValue("index"));
			}
		}
		else if(node.equals(NODE_GACL_ROLE)){
			//ACL Role
			e.setAclRole(attributes.getValue("value"));
		}
		else if(node.equals(NODE_GACL_SCOPE)){
			// ACL Scope- Type and Value
			e.setAclScopeType(attributes.getValue("type"));
			e.setAclScopeValue(attributes.getValue("value"));
		}
		else if(node.equals(NODE_GS_CELL)){
			// Cell Feed: gs:cell
			WorkSheetCell cellInfo = new WorkSheetCell();
			cellInfo.setRow(Integer.parseInt(attributes.getValue(ATTRITUBE_ROW)));
			cellInfo.setCol(Integer.parseInt(attributes.getValue(ATTRITUBE_COL)));
			if(e != null)
				e.setCellInfo(cellInfo);
		}
	}
	
	@Override
	public void endDocument() throws SAXException {
		super.endDocument();
	}

	@Override
	public void endElement(String uri, String localName, String name)
			throws SAXException {
		super.endElement(uri, localName, name);
		
		if(name.trim().length() == 0)
			node = localName;
		else
			node = name;
		
		if(node == null)
			return;
		
		if(node.equals(NODE_ENTRY)){
			// end of entry
			f.addEntry(e);
			e = null;
		}
	}
}
