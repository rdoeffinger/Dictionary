package com.prasanta.sample;

import java.util.ArrayList;

import com.pras.Collaborator;
import com.pras.SpreadSheet;
import com.pras.SpreadSheetFactory;

/**
 * Demonstrates how to use SpreadSheet Share feature
 * @author Prasanta Paul
 *
 */
public class ShareSample {

	public static void main(String[] args){
		
		SpreadSheetFactory spf = SpreadSheetFactory.getInstance(args[0], args[1]);
		
		// Get all SpreadSheets
		ArrayList<SpreadSheet> spreadSheets = spf.getAllSpreadSheets();
		
		if(spreadSheets == null || spreadSheets.size() == 0){
			System.out.println("No SpreadSheet exists");
		}
		
		SpreadSheet firstSP = spreadSheets.get(0);
		
		System.out.println("Read Collaborators of SpreadSheet: "+ firstSP.getEntry().getTitle());
		
		// Get the list of all people to whom this SpreadSheet is shared
		ArrayList<Collaborator> collaborators = spf.getAllCollaborators(firstSP);
		
		if(collaborators == null || collaborators.size() == 0){
			System.out.println("No Collaborators");
		}
		
		Collaborator c = null;
		
		for(int i=0; i<collaborators.size(); i++){
			c = collaborators.get(i);
			
			System.out.println("EditLink: "+ c.getEditLink());
			System.out.println("Role: "+ c.getRole());
			System.out.println("Scope Type: "+ c.getScopeType());
			System.out.println("Scope Value: "+ c.getScopeValue());
		}
		
		System.out.println("<Change Access Rights: >"+ c.getScopeValue());
		spf.changeSharePermission(c, Collaborator.ROLE_READER);
	}
}
