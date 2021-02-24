package com.simplicite.extobjects.SimStore;

import org.json.JSONArray;
import org.json.JSONObject;

import com.simplicite.objects.System.Module;
import com.simplicite.util.AppLog;
import com.simplicite.util.ExternalObject;
import com.simplicite.util.Grant;
import com.simplicite.util.Tool;
import com.simplicite.util.tools.HTMLTool;
import com.simplicite.util.tools.Parameters;

public class StoStore extends ExternalObject {
	private static final long serialVersionUID = 1L;

	@Override
	public Object display(Parameters params) {
		setDecoration(false);
		try {
			String installModule = params.getParameter("install", "");
			int storeIdx = params.getIntParameter("storeidx", 0);
			JSONObject data = getData(getGrant());


			// EMPTY PARAM => display page
			if (Tool.isEmpty(installModule) || ! params.has("storeidx")) {
				addMustache();
				return javascript("Store.fire(" + data.toString() + ")");
			}

			// HAS PARAM => call install
			else {
				setJSONMIMEType();
				return jsonResponseFromInstallAttempt(data, installModule, storeIdx, getGrant());
			}
		} catch (Exception e) {
			AppLog.error(getClass(), "display", null, e, getGrant());
			return e.getMessage();
		}
	}

	private JSONObject getData(Grant g) throws Exception{
		JSONArray stores = new JSONArray();
		String[] sources = g.getParameter("STORE_SOURCE").split("\r\n");
		for (int i=0; i<sources.length; i++) {
			JSONObject store = new JSONObject(Tool.readUrl(sources[i]));

			// use a store id to identify tabs
			store.put("idx", i);
			if (!store.has("name"))
				store.put("name", "AppStore "+(sources.length>1 ? i+1 : ""));

			// facilitate install status
			JSONArray apps = store.getJSONArray("apps");
			for (int j=0; j<apps.length(); j++) {
				apps.getJSONObject(j).put(
					"module_installed",
					moduleId(apps.getJSONObject(j).optString("module_name"), getGrant())
				);
				apps.getJSONObject(j).put("store_idx", i);
			}
			stores.put(store);
		}

		JSONObject data = new JSONObject();
		// facilitate install url
		data.put("install_url", HTMLTool.getExternalObjectURL("StoStore"));
		data.put("delete_url", HTMLTool.getExternalObjectURL("ModuleDelete", "nav=add&row_id="));
		data.put("stores", stores);
		return data;
	}

	private JSONObject jsonResponseFromInstallAttempt(JSONObject data, String installModule, int storeIdx, Grant g) {
		JSONObject store = data.getJSONArray("stores").getJSONObject(storeIdx);
		JSONObject app = getAppFromModuleName(store.getJSONArray("apps"), installModule);
		try {
			String moduleId = installAndGetId(app, g);
			//TODO do directly in method?
			app.put("module_installed", moduleId);
			return data;
		}
		catch (Exception e) {
			return new JSONObject("{'error': '"+e.getMessage()+"'}");
		}
	}

	private static String installAndGetId(JSONObject app, Grant g) throws Exception {
		if (app==null) {
			throw new Exception("STO_ERR_MODULE_NOT_FOUND");
		}
		else if (!Tool.isEmpty(app.getString("module_installed"))) {
			throw new Exception("STO_ERR_MODULE_ALREADY_PRESENT");
		}
		else {
			Module module = (Module) g.getTmpObject("Module");
			module.setFieldValue("mdl_name", app.getString("module_name"));
			module.setFieldValue("mdl_version", "1");
			module.setFieldValue("mdl_url", getModuleSettingsFromApp(app));
			String create = module.create();
			if (create!=null) {
				AppLog.error(StoStore.class, "install", "Create module error: "+create, null, g);
				throw new Exception("STO_ERR_MODULE_CREATION");
			}
			else {
				module.importModule(); // logs in imports superviser
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