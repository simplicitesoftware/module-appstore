package com.simplicite.extobjects.SimStore;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.simplicite.util.AppLog;
import com.simplicite.util.ExternalObject;
import com.simplicite.util.Globals;
import com.simplicite.util.Grant;
import com.simplicite.util.ObjectDB;
import com.simplicite.util.Tool;
import com.simplicite.util.exceptions.PlatformException;
import com.simplicite.util.tools.HTMLTool;
import com.simplicite.util.tools.Parameters;

/**
 * App store
 */
public class StoStore extends ExternalObject {
	private static final long serialVersionUID = 1L;

	private class Version implements Comparable<Version> {
		private String version;

		public Version(String version) {
			if (version == null)
				throw new IllegalArgumentException("Version can not be null");
			if (!version.matches("^[0-9]+(\\.[0-9]+)*(-.*)?$"))
				throw new IllegalArgumentException("Invalid version format: " + version);
			this.version = version;
		}

		public final String get() {
			return version;
		}

		private String[] split() {
			return get().replaceFirst("-.*$", "").split("\\.");
		}

		public String getMajor() {
			String[] parts = split();
			return parts.length>0 ? parts[0] : "";
		}

		public String getMinor() {
			String[] parts = split();
			return parts.length>1 ? parts[0] + "." + parts[1] : getMajor();
		}

		@Override
		public int compareTo(Version that) {
			if (that == null)
				return 1;
			String[] thisParts = split();
			String[] thatParts = that.split();
			int length = Math.max(thisParts.length, thatParts.length);
			for (int i = 0; i < length; i++) {
				int thisPart = i < thisParts.length ?
					Integer.parseInt(thisParts[i]) : 0;
				int thatPart = i < thatParts.length ?
					Integer.parseInt(thatParts[i]) : 0;
				if (thisPart < thatPart)
					return -1;
				if (thisPart > thatPart)
					return 1;
			}
			return 0;
		}

		@Override
		public boolean equals(Object that) {
			if (this == that)
				return true;
			if (that == null || getClass() != that.getClass())
				return false;
			return compareTo((Version) that) == 0;
		}

		@Override
		public int hashCode() {
			return get().hashCode();
		}

		public boolean isOlderThan(Object that) {
			if (this == that || that == null || getClass() != that.getClass())
				return false;
			return compareTo((Version) that) < 0;
		}

		public boolean isNewerThan(Object that) {
			if (this == that || that == null || getClass() != that.getClass())
				return false;
			return compareTo((Version) that) > 0;
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
				return javascript("StoStore.fire(" + data.toString() + ")");
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

	private static String readURL(String url, int timeout) {
		try {
			HttpClient client = HttpClient.newHttpClient();
			HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMillis((long)timeout * 1000)).build();
			return client.send(request, BodyHandlers.ofString()).body();
		} catch(Exception e) {
			return null;
		}
	}

	private JSONObject getVersions() {
		String resUrl = Globals.getPlatformResourcesURL();
		JSONObject versions = null;
		try {
			Version version = new Version(Globals.getPlatformFullVersion());
			String major = version.getMajor();
			String minor = version.getMinor();

			JSONObject current = new JSONObject(readURL(resUrl + "versions.json", 1))
				.getJSONObject("platform").getJSONObject(minor);
			current.put("expired", "expired".equals(current.optString("maintenance", "")));

			JSONObject local = new JSONObject()
				.put("version", Globals.getPlatformFullVersion())
				.put("date", Globals.getPlatformBuildDate());

			// If the local revision is older than the current revision of the same minor version
			if (version.isOlderThan(new Version(current.getString("version"))))
				versions = new JSONObject()
					.put("major", major)
					.put("minor", minor)
					.put("current", current)
					.put("local", local)
					.put("resources_url", resUrl);
		} catch (Exception e) {
			AppLog.error(getClass(), "getVersions", "Unable to get versions from URL: " + resUrl, e, getGrant());
		}
		return versions;
	}

	private JSONObject getData(Grant g) throws Exception {
		JSONArray stores = new JSONArray();
		String s = g.getParameter("STORE_SOURCE", "[]");
		JSONArray sources;
		try {
			sources = new JSONArray(s);
		} catch (JSONException e) {
			sources = new JSONArray(s); // old syntax (single store)
		}

		for (int i=0; i<sources.length(); i++) {
			String url = null;
			try {
				url = sources.getString(i);
				JSONObject store = new JSONObject(readURL(url, 10));

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
				AppLog.error(getClass(), "getData", "Unable to get store data" + (url==null ? "" : " from URL: " + url), e, getGrant());
			}
		}

		return new JSONObject()
			.put("install_url", HTMLTool.getExternalObjectURL("StoStore")) // facilitate install URL
			.put("stores", stores)
			.put("versions", getVersions());
	}

	private String getIncompatibilityMessage(String minVersion, String maxVersion){
		try{
			Version version = new Version(Globals.getPlatformFullVersion());
			String msg = null;

			if (AppLog.isDebug())
				AppLog.debug("Version " + version + " vs " + minVersion + " " + maxVersion, getGrant());

			if (!Tool.isEmpty(minVersion) && version.isOlderThan(new Version(minVersion)))
				msg = "Requires Simplicité >= " + minVersion;

			if (!Tool.isEmpty(maxVersion) && version.isNewerThan(new Version(maxVersion)))
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
			return new JSONObject().put("error", e.getMessage());
		}
	}

	private static String installAndGetId(JSONObject app, Grant g, boolean track) throws PlatformException {
		if (app==null) {
			throw new PlatformException("STO_ERR_MODULE_NOT_FOUND");
		}
		else if (!Tool.isEmpty(app.getString("module_installed"))) {
			throw new PlatformException("STO_ERR_MODULE_ALREADY_PRESENT");
		}
		else {
			ObjectDB module = g.getTmpObject("Module");
			module.setFieldValue("mdl_name", app.getString("module_name"));
			module.setFieldValue("mdl_version", "1");
			module.setFieldValue("mdl_url", getModuleSettingsFromApp(app));
			String create = module.create();
			if (create!=null) {
				AppLog.error(StoStore.class, "install", "Create module error: " + create, null, g);
				throw new PlatformException("STO_ERR_MODULE_CREATION");
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