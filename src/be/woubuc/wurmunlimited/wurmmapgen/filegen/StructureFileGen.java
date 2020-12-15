package be.woubuc.wurmunlimited.wurmmapgen.filegen;

import be.woubuc.wurmunlimited.wurmmapgen.Logger;
import be.woubuc.wurmunlimited.wurmmapgen.WurmMapGen;
import be.woubuc.wurmunlimited.wurmmapgen.database.Village;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.file.Paths;
import java.util.ArrayList;

public final class VillageFileGen extends FileGen {
	
	public VillageFileGen() {
		setFilePath(Paths.get(WurmMapGen.dataPath, "villages.json"));
	}
	
	@SuppressWarnings("unchecked")
	protected String generateData() {
		Logger.title("Village data");
		
		// Load list of villages
		ArrayList<Village> villages = WurmMapGen.db.getVillages();
		
		// Stop right here if there are no villages on the server
		if (villages.size() == 0) {
			Logger.custom("SKIP", "No villages found");
			return null;
		}
		
		// Prepare JSON objects
		JSONObject dataObject = new JSONObject();
		JSONArray data = new JSONArray();
		
		JSONObject deedData;
		JSONArray deedBorders;
		
		for (final Village village : villages) {
			deedData = new JSONObject();
			deedBorders = new JSONArray();
			
			deedBorders.put(village.getStartX());
			deedBorders.put(village.getStartY());
			deedBorders.put(village.getEndX());
			deedBorders.put(village.getEndY());
			deedData.put("borders", deedBorders);
			
			deedData.put("name", village.getVillageName());
			deedData.put("motto", village.getMotto());
			deedData.put("permanent", village.isPermanent());
			
			deedData.put("x", village.getTokenX() + 0.5);
			deedData.put("y", village.getTokenY() + 0.5);
			
			deedData.put("mayor", village.getMayorName());
			deedData.put("citizenCount", village.getCitizenCount());
			deedData.put("citizenNames", village.getCitizenNames());
			
			data.put(deedData);
		}
		
		dataObject.put("villages", data);
		
		return dataObject.toString();
	}
	
}
