package edu.utah.hci.bioinfo.SBApps;

import java.util.ArrayList;
import org.json.JSONArray;
import org.json.JSONObject;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;

public class API {

	private String sbUrl = null;
	private String userToken = null;

	public API (Credentials c) {
		sbUrl = c.getUrl();
		if (sbUrl.endsWith("/")== false) sbUrl = sbUrl+"/";
		userToken = c.getToken();
	}

	public JsonNode query(String query, boolean partialURL, boolean verbose) throws Exception {
		String url = query;
		if (partialURL) url = sbUrl+query;
		HttpResponse <JsonNode> response = Unirest.get(url).header("X-SBG-Auth-Token", userToken).asJson();
		
		//standard response/
		if (response.getStatus() == 200) return response.getBody();
		
		//hit rate limit? too many API calls
		else if (response.getStatus() == 429) {
			if (verbose) {
				Util.e("\nProblem executing API query: "+url);
				Util.e("Body: "+ response.getBody());
				System.err.print("Waiting 5 min before making further calls" );
				for (int x=0; x<31; x++) {
					Thread.sleep(10000);
					System.err.print(".");
				}
				Util.e("");
				return query(query, partialURL, verbose);
			}
		}
		else {
			if (verbose) {
				Util.e("\nProblem executing API query: "+url);
				Util.e("Status: "+response.getStatus());
				Util.e("Body: "+ response.getBody());
			}
			return null;
		} 
		return null;
	}

	/*
	public JsonNode bulkFileDetailQuery(ArrayList<String> fileIds) {
		JSONArray ja = new JSONArray();
		try {
			//make json object with the ids
			for (String id: fileIds) ja.put(id);
			JSONObject jo = new JSONObject();
			jo.put("file_ids", ja);

			HttpResponse<JsonNode> response = Unirest.post(sbUrl+"bulk/files/get")
					.header("X-SBG-Auth-Token", userToken)
					.header("content-type","application/json")
					.body(jo)
					.asJson();

			if (response.getStatus() == 200) return response.getBody();
			else {
				Util.e("\nProblem executing API bulk file query: "+sbUrl+"bulk/files/get"+ja.toString());
				Util.e("Status: "+response.getStatus());
				Util.e("Body: "+ response.getBody());
				System.exit(1);
				return null;
			} 

		} catch (Exception e) {
			e.printStackTrace();
			Util.printErrorExit("Problem executing API bulk file query "+sbUrl+"bulk/files/get"+ja.toString());
			return null;
		}
	}*/

}
