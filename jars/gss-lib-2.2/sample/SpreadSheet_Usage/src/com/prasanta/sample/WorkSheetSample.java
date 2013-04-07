package com.prasanta.sample;

import java.util.ArrayList;
import java.util.HashMap;

import com.pras.Log;
import com.pras.SpreadSheet;
import com.pras.SpreadSheetFactory;
import com.pras.WorkSheet;
import com.pras.table.Record;

/**
 * Demonstrates WorkSheet handling Add/Delete
 * Add/Update/Delete Records into WorkSheet
 * Retrieve record from WorkSheet
 * 
 * @author Prasanta Paul
 *
 */
public class WorkSheetSample {
	
	public static void main(String[] args){
		
		// Enable/Disable Logging
		// by default it will be enabled
		Log.enableLog();
		
		// Create SpreadSheet Factory
		SpreadSheetFactory spf = SpreadSheetFactory.getInstance(args[0], args[1]);
		
		// Get All SpreadSheets
		//ArrayList<SpreadSheet> spreadSheets = spf.getAllSpreadSheets();
		
		// Get selected SpreadSheet- whose name contains "Pras"
		ArrayList<SpreadSheet> spreadSheets = spf.getSpreadSheet("Pras", false);
		
		if(spreadSheets == null || spreadSheets.size() == 0){
			System.out.println("No SpreadSheet Exists!");
			return;
		}
		
		System.out.println("Number of SpreadSheets: "+ spreadSheets.size());
		
		SpreadSheet sp = spreadSheets.get(0);
		
		// Add an WorkSheet
		//sp.addWorkSheet("testWork1", new String[]{"date", "item", "price"});
		//sp.addWorkSheet("tabWork2", new String[]{"date", "item", "price", "person"});
		
		// Get all WorkSheets
		// ArrayList<WorkSheet> wks = sp.getAllWorkSheets();
		
		// Get selected WorkSheet
		ArrayList<WorkSheet> wks = sp.getWorkSheet("test", false);
		
		if(wks == null || wks.size() == 0){
			System.out.println("No WorkSheet exists!!");
			return;
		}
		
		System.out.println("Number of WorkSheets: "+ wks.size());
		
		WorkSheet wk = null;
		/*for(int i=0; i<wks.size(); i++){
			
			 * Search for WorkSheet having name "tab_*"
			 
			if(wks.get(i).getTitle().startsWith("tab_")){
				wk = wks.get(i);
				break;
			}
		}*/
		wk = wks.get(0);
		
		System.out.println("wk="+ wk.getTitle());

		HashMap<String, String> rs = new HashMap<String, String>();
		rs.put("date", "1st Jan 2011");
		rs.put("item", "Item3");
		rs.put("price", "250");
		wk.addRecord(sp.getKey(), rs);
		
		/*
		 * WorkSheet Record Handling
		 */
		// Get Data
		ArrayList<Record> records = wk.getRecords(sp.getKey());
		
		if(records == null || records.size() == 0){
			System.out.println("No Record exists!!");
			return;
		}
		
		System.out.println("Number of Records: "+ records.size());
		
		// Display Record
		for(int i=0; i<records.size(); i++){
			Record r = records.get(i);
			HashMap<String, String> data = r.getData();
			System.out.println("Data: "+ data);
		}
		
		// Delete Record
		//wk.deleteRecord(records.get(0));
		
		// Update Record
		//Record toUpdate = records.get(0);
		
		//toUpdate.addData("Name", "Update_Name");
		//wk.updateRecord(toUpdate);
	}
	
}
