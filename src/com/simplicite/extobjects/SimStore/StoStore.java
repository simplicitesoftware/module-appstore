package com.simplicite.extobjects.SimStore;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import com.simplicite.util.AppLog;
import com.simplicite.util.ExternalObject;
import com.simplicite.util.Grant;
import com.simplicite.util.ObjectDB;
import com.simplicite.util.Tool;
import com.simplicite.util.Globals;
import com.simplicite.util.tools.HTMLTool;
import com.simplicite.util.tools.Parameters;

/**
 * App store
 */
public class StoStore extends ExternalObject {
	private static final long serialVersionUID = 1L;

	private class Version implements Comparable<Version> {
	    private String version;
	
	    public final String get() {
	        return this.version;
	    }
	
	    public Version(String version) {
	        if(version == null)
	            throw new IllegalArgumentException("Version can not be null");
	        if(!version.matches("^[0-9]+(\\.[0-9]+)*(-.*)?$"))
	            throw new IllegalArgumentException("Invalid version format: " + version);
	        this.version = version;
	    }
	
	    @Override public int compareTo(Version that) {
	        if(that == null)
	            return 1;
	        String[] thisParts = this.get().replaceFirst("-.*$", "").split("\\.");
	        String[] thatParts = that.get().replaceFirst("-.*$", "").split("\\.");
	        int length = Math.max(thisParts.length, thatParts.length);
	        for(int i = 0; i < length; i++) {
	            int thisPart = i < thisParts.length ?
	                Integer.parseInt(thisParts[i]) : 0;
	            int thatPart = i < thatParts.length ?
	                Integer.parseInt(thatParts[i]) : 0;
	            if(thisPart < thatPart)
	                return -1;
	            if(thisPart > thatPart)
	                return 1;
	        }
	        return 0;
	    }
	
	    @Override public boolean equals(Object that) {
	        if(this == that)
	            return true;
	        if(that == null)
	            return false;
	        if(this.getClass() != that.getClass())
	            return false;
	        return this.compareTo((Version) that) == 0;
	    }
	}

	@Override
	public Object display(Parameters params) {
		setDecoration(false);
		try {
			String installModule = params.getParameter("install", "");
			int storeIdx = params.getIntParameter("storeidx", 0);
			JSONObject data = getData(getGrant());

			// EMPTY PARAM => display page
			if (Tool.isEmpty(installModule) || !params.has("storeidx")) {
				addMustache();
				return javascript("Store.fire(" + data.toString() + ")");
			}
			// HAS PARAM => call install
			else {
				setJSONMIMEType();
				boolean track = params.getBooleanParameter("track", false);
				return jsonResponseFromInstallAttempt(data, installModule, storeIdx, getGrant(), track);
			}
		} catch (Exception e) {
			AppLog.error(getClass(), "display", null, e, getGrant());
			return e.getMessage();
		}
	}

	private JSONObject getData(Grant g) throws Exception{
		JSONArray stores = new JSONArray();
		String s = g.getParameter("STORE_SOURCE", "[]");
		JSONArray sources;
		try {
			sources = new JSONArray(s);
		} catch (JSONException e) {
			sources = new JSONArray(s); // old syntax (single store)
		}
		for (int i=0; i<sources.length(); i++) {
			try {
				JSONObject store = new JSONObject(Tool.readUrl(sources.getString(i)));

				// use a store id to identify tabs
				store.put("idx", i);
				if (!store.has("name"))
					store.put("name", "AppStore "+(sources.length()>1 ? i+1 : ""));
	
				// facilitate install status
				JSONArray apps = store.getJSONArray("apps");
				for (int j=0; j<apps.length(); j++) {
					apps.getJSONObject(j).put(
						"module_installed",
						moduleId(apps.getJSONObject(j).optString("module_name"), getGrant())
					);
					apps.getJSONObject(j).put(
						"incompatible",
						getIncompatibilityMessage(
							apps.getJSONObject(j).optString("min_version"),
							apps.getJSONObject(j).optString("max_version")
						)
					);
					apps.getJSONObject(j).put("store_idx", i);
				}
				stores.put(store);
			} catch (Exception e) {
				AppLog.error(getClass(), "getData", null, e, getGrant());
			}
		}

		JSONObject data = new JSONObject();
		// facilitate install url
		data.put("install_url", HTMLTool.getExternalObjectURL("StoStore"));
		data.put("stores", stores);
		return data;
	}
	
	private String getIncompatibilityMessage(String minVersion, String maxVersion){
		try{
			Version platform = new Version(Globals.getPlatformFullVersion());
			String msg = null;

			AppLog.info("Version " + platform + " vs " + minVersion + " " + maxVersion, getGrant());
			
			if(!Tool.isEmpty(minVersion) && platform.compareTo(new Version(minVersion))<0)
				msg = "Requires Simplicité >= " + minVersion;
			
			if(!Tool.isEmpty(maxVersion) && platform.compareTo(new Version(maxVersion))>0)
				msg = msg==null ? "Requires Simplicité <= "+maxVersion : msg+" and <= "+maxVersion;
			
			return msg;
		}
		catch(IllegalArgumentException e){
			AppLog.error(e, getGrant());
			return null;
		}
	}

	private JSONObject jsonResponseFromInstallAttempt(JSONObject data, String installModule, int storeIdx, Grant g, boolean track) {
		JSONObject store = data.getJSONArray("stores").getJSONObject(storeIdx);
		JSONObject app = getAppFromModuleName(store.getJSONArray("apps"), installModule);
		try {
			String moduleId = installAndGetId(app, g, track);
			//TODO do directly in method?
			app.put("module_installed", moduleId);
			return data;
		}
		catch (Exception e) {
			return new JSONObject("{'error': '"+e.getMessage()+"'}");
		}
	}

	private static String installAndGetId(JSONObject app, Grant g, boolean track) throws Exception {
		if (app==null) {
			throw new Exception("STO_ERR_MODULE_NOT_FOUND");
		}
		else if (!Tool.isEmpty(app.getString("module_installed"))) {
			throw new Exception("STO_ERR_MODULE_ALREADY_PRESENT");
		}
		else {
			ObjectDB module = g.getTmpObject("Module");
			module.setFieldValue("mdl_name", app.getString("module_name"));
			module.setFieldValue("mdl_version", "1");
			module.setFieldValue("mdl_url", getModuleSettingsFromApp(app));
			String create = module.create();
			if (create!=null) {
				AppLog.error(StoStore.class, "install", "Create module error: "+create, null, g);
				throw new Exception("STO_ERR_MODULE_CREATION");
			}
			else {
				// async use in UI tracker = ModuleImport must be launched by front
				if (!track)
					module.invokeAction("ModuleImport"); // logs in imports superviser
				return module.getRowId();
			}
		}
	}

	private static String moduleId(String moduleName, Grant g) {
		return g.simpleQuery("select row_id from m_module where mdl_name='"+moduleName+"'");
	}

	private static JSONObject getAppFromModuleName(JSONArray apps, String moduleName) {
		for (int i=0; i<apps.length(); i++) {
			if (apps.getJSONObject(i).optString("module_name").equals(moduleName)) {
				return apps.getJSONObject(i);
			}
		}
		return null;
	}

	private static String getModuleSettingsFromApp(JSONObject app) {
		String settings;
		try{
			settings = app.getJSONObject("module_settings").toString();
		}
		catch(Exception e) {
			settings = app.optString("module_settings");
		}
		return settings;
	}
}