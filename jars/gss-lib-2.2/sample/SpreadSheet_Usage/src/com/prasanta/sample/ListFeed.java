package com.prasanta.sample;

import java.util.ArrayList;
import java.util.HashMap;

import com.pras.SpreadSheet;
import com.pras.SpreadSheetFactory;
import com.pras.WorkSheet;
import com.pras.WorkSheetRow;

/**
 * This class demonstrates usage of List Based Feed-
 * - Add Listbased WorkSheet
 * - Add Row
 * - Update Row
 * - Delete Row
 * - Get List of Rows
 * 
 * @author Prasanta Paul
 *
 */
public class ListFeed {
	
	
	/**
	 * Create List based WorkSheet
	 * @param args
	 */
	public static void createListWorkSheet(String[] args){
		String[] cols = {"id", "devicename", "vendor"};
		SpreadSheetFactory spf = SpreadSheetFactory.getInstance("email", "password");
		
		// Get selected SpreadSheet 	
		ArrayList<SpreadSheet> spreadSheets = spf.getSpreadSheet("Pras", false);
		
		if(spreadSheets == null || spreadSheets.size() == 0){
			System.out.println("No SpreadSheet Exists!");
			return;
		}
		
		System.out.println("Number of SpreadSheets: "+ spreadSheets.size());
		
		SpreadSheet sp = spreadSheets.get(0);
		System.out.println("### Creating WorkSheet for ListFeed ###");
		WorkSheet workSheet = sp.addListWorkSheet("Device_List", 10, cols);
		
		HashMap<String, String> row_data = new HashMap<String, String>();
		row_data.put("id", "1");
		row_data.put("devicename", "Samsung Ace");
		row_data.put("vendor", "Samsung");
		
		// Add entries
		WorkSheetRow list_row1 = workSheet.addListRow(row_data);
		
		row_data.put("id", "2");
		row_data.put("devicename", "Optimus");
		row_data.put("vendor", "LG");
		
		WorkSheetRow list_row2 = workSheet.addListRow(row_data);
		
		row_data.put("id", "3");
		row_data.put("devicename", "Xdroid");
		row_data.put("vendor", "Motorola");
		
		WorkSheetRow list_row3 = workSheet.addListRow(row_data);
		
		// Update previous Row
		row_data.put("id", "2");
		row_data.put("devicename", "Optimus");
		row_data.put("vendor", "LG-New");
		workSheet.updateListRow(sp.getEntry().getKey(), list_row2, row_data);
		
		workSheet.deleteListRow(sp.getEntry().getKey(), list_row3);
		
		ArrayList<WorkSheetRow> rows = workSheet.getData(true);
		System.out.println("List Feed: "+ rows);
		
		// Example of Structured Query-
		/*
		 * For List Based feed use lower case Column Name
		 * Note: Column name should be in lower case
		 * Retrieve data where age < 30 and order by "name" column
		 */
//		ArrayList<WorkSheetRow> rows = workSheet.getData(false, false, HttpConHandler.encode("\"age\"") + "<" + HttpConHandler.encode("30"), "column:name");
//		
//		System.out.println("Count: "+ rows.size());
//		
//		for(int i=0; i<rows.size(); i++){
//			WorkSheetRow r = rows.get(i);
//			
//			ArrayList<WorkSheetCell> cls = r.getCells();
//			for(int j=0; j<cls.size(); j++){
//				WorkSheetCell c = cls.get(j);
//				System.out.println("Col Name: "+ c.getName() +" Value: "+ c.getValue());
//			}
//		}
	}
	
	/**
	 * Displays All WorkSheet (Table and List)
	 */
	public static void displayWorkSheetInfo(){
		SpreadSheetFactory spf = SpreadSheetFactory.getInstance("email", "password");
		
		// Get selected SpreadSheet 	
		ArrayList<SpreadSheet> spreadSheets = spf.getSpreadSheet("Pras", false);
		
		if(spreadSheets == null || spreadSheets.size() == 0){
			System.out.println("No SpreadSheet Exists!");
			return;
		}
		
		System.out.println("Number of SpreadSheets: "+ spreadSheets.size());
		
		SpreadSheet sp = spreadSheets.get(0);
		
		ArrayList<WorkSheet> wks = sp.getAllWorkSheets();
		for(int i=0; i<wks.size(); i++){
			System.out.println("WorkSheets: "+ wks.get(i).getTitle());
			String[] cols = wks.get(i).getColumns();
			for(int j=0; j<cols.length; j++){
				System.out.println("WorkSheet- Columns: "+ cols[j]);
			}
		}
	}
	
	public static void main(String[] args){
		displayWorkSheetInfo();
	}
}
