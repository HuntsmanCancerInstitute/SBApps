package edu.utah.hci.bioinfo.SBApps;

import java.util.ArrayList;

import org.json.JSONException;
import org.json.JSONObject;

public class SBFile {
	
	//fields
	private String parentId = null;
	private String name = null;
	private String id = null;
	private boolean isFolder = false; 
	private String path = null;
	private boolean makeUrl = true;
	private String url = null;
	

	public SBFile(JSONObject ff) throws JSONException {
		parentId = ff.getString("parent");
		name = ff.getString("name");
		id = ff.getString("id");
		isFolder = ff.getString("type").equals("folder");
	}

	public String getParentId() {
		return parentId;
	}

	public String getName() {
		return name;
	}

	public String getId() {
		return id;
	}


	public boolean isFolder() {
		return isFolder;
	}

	public void setPath(ArrayList<String> paths) {
		StringBuilder sb = new StringBuilder();
		int size = paths.size();
		sb.append(paths.get(size-1));
		for (int i=size-2; i>=0; i--) {
			sb.append("/");
			sb.append(paths.get(i));
		}
		path = sb.toString();

		
	}

	public String getPath() {
		return path;
	}

	public void setMakeUrl(boolean makeUrl) {
		this.makeUrl = makeUrl;
	}
	
	public boolean isMakeUrl() {
		return makeUrl;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

}
