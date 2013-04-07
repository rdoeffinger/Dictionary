package com.prasanta.sample;

import java.util.ArrayList;
import java.util.HashMap;

import com.pras.Log;
import com.pras.SpreadSheet;
import com.pras.SpreadSheetFactory;
import com.pras.WorkSheet;
import com.pras.conn.HttpConHandler;
import com.pras.table.Record;

/**
 * Demonstrates how to handle Data/Records using this Lib. 
 * Conditional Data Retrieval and Record Add/Edit/Delete  
 * @author Prasanta Paul
 *
 */
public class ConditionalDataRetrieve {

	public static void main(String[] args){
		String TAG = "ConditionalDataRetrieve";
		// Enable/Disable Log
		Log.enableLog();
		
		// Create SpreadSheet Factory instance
		SpreadSheetFactory spf = SpreadSheetFactory.getInstance(args[0], args[1]);
		
		// Get all SpreadSheets
		ArrayList<SpreadSheet> spreadSheets = spf.getAllSpreadSheets();
		
		if(spreadSheets == null && spreadSheets.size() == 0){
			Log.p(TAG, "No SpreadSheet Exists");
			return;
		}
		
		SpreadSheet s = spreadSheets.get(0);
		
		System.out.println("SpreadSheet: "+ s.getTitle());
		
		// Create WorkSheet
		s.addWorkSheet("tab_work_sample", new String[]{"Name", "Age", "Exp", "Country"});
		
		// Get List of All WorkSheets
		ArrayList<WorkSheet> wks = s.getAllWorkSheets();
		WorkSheet wk = wks.get(0);
		
		// Add Record
		HashMap<String, String> record = new HashMap<String, String>();
		
		record.put("Name", "Santosh");
		record.put("Age", "25");
		record.put("Exp", "10");
		record.put("Country", "India");
		
		wk.addRecord(s.getKey(), record);
		
		// Conditional Data Read
		// Read Data from WorkSheet, where Age < 30 and Order by "Name" column 
		// Please follow- http://code.google.com/apis/spreadsheets/data/3.0/reference.html#RecordParameters
		ArrayList<Record> records = wk.getRecords(s.getKey(), false, HttpConHandler.encode("\"Age\"") + "<" + HttpConHandler.encode("30"), "column:Name");
		
		if(records == null || records.size() == 0){
			System.out.println("No matching data");
			return;
		}
		
		// Display Record
		for(int i=0; i<records.size(); i++){
			Record r = records.get(i);
			HashMap<String, String> data = r.getData();
			System.out.println("Data: "+ data);
			
			// Delete Record
			//wk.deleteRecord(r);
		}
		
		// Delete WorkSheet
		s.deleteWorkSheet(wk);
	}
}
