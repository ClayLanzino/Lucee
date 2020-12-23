/**
 * Copyright (c) 2014, the Railo Company Ltd.
 * Copyright (c) 2015, Lucee Assosication Switzerland
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either 
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public 
 * License along with this library.  If not, see <http://www.gnu.org/licenses/>.
 * 
 */
package lucee.runtime.config;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.Version;
import org.w3c.dom.DOMException;
import org.xml.sax.SAXException;

import com.allaire.cfx.CustomTag;

import lucee.commons.digest.MD5;
import lucee.commons.io.CharsetUtil;
import lucee.commons.io.FileUtil;
import lucee.commons.io.IOUtil;
import lucee.commons.io.SystemUtil;
import lucee.commons.io.cache.Cache;
import lucee.commons.io.compress.ZipUtil;
import lucee.commons.io.log.Log;
import lucee.commons.io.log.LogUtil;
import lucee.commons.io.res.Resource;
import lucee.commons.io.res.ResourceProvider;
import lucee.commons.io.res.ResourcesImpl;
import lucee.commons.io.res.filter.ResourceFilter;
import lucee.commons.io.res.filter.ResourceNameFilter;
import lucee.commons.io.res.util.FileWrapper;
import lucee.commons.io.res.util.ResourceUtil;
import lucee.commons.lang.ClassException;
import lucee.commons.lang.ClassUtil;
import lucee.commons.lang.ExceptionUtil;
import lucee.commons.lang.StringUtil;
import lucee.commons.net.HTTPUtil;
import lucee.commons.net.IPRange;
import lucee.commons.net.URLEncoder;
import lucee.commons.net.http.HTTPEngine;
import lucee.commons.net.http.HTTPResponse;
import lucee.loader.engine.CFMLEngine;
import lucee.loader.engine.CFMLEngineFactory;
import lucee.loader.osgi.BundleCollection;
import lucee.loader.util.ExtensionFilter;
import lucee.runtime.PageContext;
import lucee.runtime.cache.CacheConnection;
import lucee.runtime.cache.CacheUtil;
import lucee.runtime.cfx.CFXTagException;
import lucee.runtime.cfx.CFXTagPool;
import lucee.runtime.converter.ConverterException;
import lucee.runtime.converter.JSONConverter;
import lucee.runtime.converter.JSONDateFormat;
import lucee.runtime.converter.WDDXConverter;
import lucee.runtime.db.ClassDefinition;
import lucee.runtime.db.DataSource;
import lucee.runtime.db.ParamSyntax;
import lucee.runtime.engine.ThreadLocalPageContext;
import lucee.runtime.exp.ApplicationException;
import lucee.runtime.exp.ExpressionException;
import lucee.runtime.exp.HTTPException;
import lucee.runtime.exp.PageException;
import lucee.runtime.exp.SecurityException;
import lucee.runtime.extension.Extension;
import lucee.runtime.extension.ExtensionDefintion;
import lucee.runtime.extension.RHExtension;
import lucee.runtime.functions.other.CreateObject;
import lucee.runtime.functions.other.URLEncodedFormat;
import lucee.runtime.functions.string.Hash;
import lucee.runtime.functions.system.IsZipFile;
import lucee.runtime.gateway.GatewayEngineImpl;
import lucee.runtime.gateway.GatewayEntry;
import lucee.runtime.gateway.GatewayEntryImpl;
import lucee.runtime.listener.AppListenerUtil;
import lucee.runtime.listener.SerializationSettings;
import lucee.runtime.monitor.Monitor;
import lucee.runtime.net.amf.AMFEngine;
import lucee.runtime.net.ntp.NtpClient;
import lucee.runtime.op.Caster;
import lucee.runtime.op.Decision;
import lucee.runtime.orm.ORMConfiguration;
import lucee.runtime.orm.ORMConfigurationImpl;
import lucee.runtime.orm.ORMEngine;
import lucee.runtime.osgi.BundleBuilderFactory;
import lucee.runtime.osgi.BundleFile;
import lucee.runtime.osgi.BundleInfo;
import lucee.runtime.osgi.OSGiUtil;
import lucee.runtime.osgi.OSGiUtil.BundleDefinition;
import lucee.runtime.reflection.Reflector;
import lucee.runtime.regex.RegexFactory;
import lucee.runtime.search.SearchEngine;
import lucee.runtime.security.SecurityManager;
import lucee.runtime.security.SecurityManagerImpl;
import lucee.runtime.security.SerialNumber;
import lucee.runtime.type.Array;
import lucee.runtime.type.ArrayImpl;
import lucee.runtime.type.Collection.Key;
import lucee.runtime.type.KeyImpl;
import lucee.runtime.type.Query;
import lucee.runtime.type.QueryImpl;
import lucee.runtime.type.Struct;
import lucee.runtime.type.StructImpl;
import lucee.runtime.type.dt.TimeSpan;
import lucee.runtime.type.scope.Cluster;
import lucee.runtime.type.scope.ClusterNotSupported;
import lucee.runtime.type.scope.ClusterRemote;
import lucee.runtime.type.scope.ScopeContext;
import lucee.runtime.type.util.ArrayUtil;
import lucee.runtime.type.util.ComponentUtil;
import lucee.runtime.type.util.KeyConstants;
import lucee.runtime.type.util.ListUtil;
import lucee.runtime.video.VideoExecuter;
import lucee.runtime.video.VideoExecuterNotSupported;
import lucee.transformer.library.ClassDefinitionImpl;
import lucee.transformer.library.function.FunctionLibException;
import lucee.transformer.library.tag.TagLibException;

/**
 * 
 */
public final class ConfigAdmin {

	private static final BundleInfo[] EMPTY = new BundleInfo[0];
	private ConfigPro config;
	private Struct root;
	private Password password;

	/**
	 * 
	 * @param config
	 * @param password
	 * @return returns a new instance of the class
	 * @throws SAXException
	 * @throws IOException
	 * @throws PageException
	 */
	public static ConfigAdmin newInstance(Config config, Password password) throws IOException, PageException, SAXException {
		return new ConfigAdmin((ConfigPro) config, password);
	}

	private void checkWriteAccess() throws SecurityException {
		ConfigWebUtil.checkGeneralWriteAccess(config, password);
	}

	private void checkReadAccess() throws SecurityException {
		ConfigWebUtil.checkGeneralReadAccess(config, password);
	}

	/**
	 * @param password
	 * @throws IOException
	 * @throws DOMException
	 * @throws ExpressionException
	 */
	public void setPassword(Password password) throws SecurityException, DOMException, IOException {
		checkWriteAccess();
		PasswordImpl.writeToStruct(root, password, false);
	}

	/*
	 * public void setVersion(double version) { setVersion(doc,version);
	 * 
	 * }
	 */

	public static void setVersion(Struct root, Version version) {
		root.setEL("version", version.getMajor() + "." + version.getMinor());

	}
	/*
	 * public void setId(String id) {
	 * 
	 * Element root=doc.getDocumentElement(); if(!StringUtil.isEmpty(root.get("id"))) return;
	 * root.setEL("id",id); try { store(config); } catch (Exception e) {} }
	 */

	/**
	 * @param contextPath
	 * @param password
	 * @throws FunctionLibException
	 * @throws TagLibException
	 * @throws IOException
	 * @throws ClassNotFoundException
	 * @throws SAXException
	 * @throws PageException
	 * @throws BundleException
	 */
	public void removePassword(String contextPath) throws PageException, SAXException, ClassException, IOException, TagLibException, FunctionLibException, BundleException {
		checkWriteAccess();
		if (contextPath == null || contextPath.length() == 0 || !(config instanceof ConfigServerImpl)) {
			// config.setPassword(password); do nothing!
		}
		else {
			ConfigServerImpl cs = (ConfigServerImpl) config;
			ConfigWebImpl cw = (ConfigWebImpl) cs.getConfigWeb(contextPath);
			if (cw != null) cw.updatePassword(false, cw.getPassword(), null);
		}
	}

	private ConfigAdmin(ConfigPro config, Password password) throws IOException, PageException, SAXException {
		this.config = config;
		this.password = password;
		root = ConfigWebFactory.loadDocument(config.getConfigFile());
	}

	public static void checkForChangesInConfigFile(Config config) {
		ConfigPro ci = (ConfigPro) config;
		if (!ci.checkForChangesInConfigFile()) return;

		Resource file = config.getConfigFile();
		long diff = file.lastModified() - ci.lastModified();
		if (diff < 10 && diff > -10) return;
		// reload
		try {
			ConfigAdmin admin = ConfigAdmin.newInstance(ci, null);
			admin._reload();
			LogUtil.log(ThreadLocalPageContext.getConfig(config), Log.LEVEL_INFO, ConfigAdmin.class.getName(), "reloaded the configuration [" + file + "] automatically");
		}
		catch (Throwable t) {
			ExceptionUtil.rethrowIfNecessary(t);
		}
	}

	private void addResourceProvider(String scheme, ClassDefinition cd, String arguments) throws PageException {
		checkWriteAccess();

		// Struct resources = _getRootElement("resources");
		Array rpElements = ConfigWebUtil.getAsArray("resources", "resource-provider", root);
		// Element[] rpElements = ConfigWebFactory.getChildren(resources, "resource-provider");
		String s;
		// update
		if (rpElements != null) {
			Struct rpElement;
			for (int i = 1; i <= rpElements.size(); i++) {
				rpElement = Caster.toStruct(rpElements.getE(i));
				s = Caster.toString(rpElement.get("scheme"));
				if (!StringUtil.isEmpty(s) && s.equalsIgnoreCase(scheme)) {
					setClass(rpElement, null, "", cd);
					rpElement.setEL("scheme", scheme);
					rpElement.setEL("arguments", arguments);
					return;
				}
			}
		}
		// Insert
		Struct el = new StructImpl(Struct.TYPE_LINKED);
		setClass(el, null, "", cd);
		el.setEL("scheme", scheme);
		el.setEL("arguments", arguments);
		rpElements.appendEL(el);
	}

	public static synchronized void _storeAndReload(ConfigPro config)
			throws PageException, SAXException, ClassException, IOException, TagLibException, FunctionLibException, BundleException, ConverterException {
		ConfigAdmin admin = new ConfigAdmin(config, null);
		admin._store();
		admin._reload();
	}

	private synchronized void _storeAndReload()
			throws PageException, SAXException, ClassException, IOException, TagLibException, FunctionLibException, BundleException, ConverterException {
		_store();
		_reload();
	}

	public synchronized void storeAndReload()
			throws PageException, SAXException, ClassException, IOException, TagLibException, FunctionLibException, BundleException, ConverterException {
		checkWriteAccess();
		_store();
		_reload();
	}

	private synchronized void _store() throws PageException, ConverterException, IOException {
		JSONConverter json = new JSONConverter(true, CharsetUtil.UTF8, JSONDateFormat.PATTERN_CF, true, true);
		String str = json.serialize(null, root, SerializationSettings.SERIALIZE_AS_ROW);
		IOUtil.write(config.getConfigFile(), str, CharsetUtil.UTF8, false);
	}

	private synchronized void _reload() throws PageException, SAXException, ClassException, IOException, TagLibException, FunctionLibException, BundleException {

		// if(storeInMemoryData)XMLCaster.writeTo(doc,config.getConfigFile());
		CFMLEngine engine = ConfigWebUtil.getEngine(config);
		if (config instanceof ConfigServerImpl) {

			ConfigServerImpl cs = (ConfigServerImpl) config;
			ConfigServerFactory.reloadInstance(engine, cs);
			ConfigWeb[] webs = cs.getConfigWebs();
			for (int i = 0; i < webs.length; i++) {
				ConfigWebFactory.reloadInstance(engine, (ConfigServerImpl) config, (ConfigWebImpl) webs[i], true);
			}
		}
		else {
			ConfigServerImpl cs = ((ConfigWebImpl) config).getConfigServerImpl();
			ConfigWebFactory.reloadInstance(engine, cs, (ConfigWebImpl) config, false);
		}
	}

	/*
	 * private void createAbort() { try {
	 * ConfigWebFactory.getChildByName(doc.getDocumentElement(),"cfabort",true); } catch(Throwable t)
	 * {ExceptionUtil.rethrowIfNecessary(t);} }
	 */

	public void setTaskMaxThreads(Integer maxThreads) throws SecurityException {
		checkWriteAccess();
		boolean hasAccess = ConfigWebUtil.hasAccess(config, SecurityManager.TYPE_SETTING);
		if (!hasAccess) throw new SecurityException("no access to update task settings");
		Struct mail = _getRootElement("remote-clients");
		mail.setEL("max-threads", Caster.toString(maxThreads, ""));
	}

	/**
	 * sets Mail Logger to Config
	 * 
	 * @param logFile
	 * @param level
	 * @throws PageException
	 */
	public void setMailLog(Config config, String logFile, String level) throws PageException {
		ConfigPro ci = (ConfigPro) config;
		checkWriteAccess();
		boolean hasAccess = ConfigWebUtil.hasAccess(config, SecurityManager.TYPE_MAIL);

		if (!hasAccess) throw new SecurityException("no access to update mail server settings");
		ConfigWebUtil.getFile(config, config.getRootDirectory(), logFile, FileUtil.TYPE_FILE);

		Array loggers = ConfigWebUtil.getAsArray("logging", "logger", root);
		// Element[] children = XMLUtil.getChildElementsAsArray(logging);
		Struct logger = null;
		Struct tmp;
		for (int i = 0; i < loggers.size(); i++) {
			tmp = Caster.toStruct(loggers.get(i, null), null);
			if (tmp == null) continue;

			if ("mail".equalsIgnoreCase(ConfigWebUtil.getAsString("name", tmp, ""))) {
				logger = tmp;
				break;
			}
		}
		if (logger == null) {
			logger = new StructImpl(Struct.TYPE_LINKED);
			loggers.appendEL(logger);
		}
		logger.setEL("name", "mail");
		if ("console".equalsIgnoreCase(logFile)) {
			setClass(logger, null, "appender-", ci.getLogEngine().appenderClassDefintion("console"));
			setClass(logger, null, "layout-", ci.getLogEngine().layoutClassDefintion("pattern"));
		}
		else {
			setClass(logger, null, "appender-", ci.getLogEngine().appenderClassDefintion("resource"));
			setClass(logger, null, "layout-", ci.getLogEngine().layoutClassDefintion("classic"));
			logger.setEL("appender-arguments", "path:" + logFile);
		}
		logger.setEL("log-level", level);
	}

	/**
	 * sets if spool is enable or not
	 * 
	 * @param spoolEnable
	 * @throws SecurityException
	 */
	public void setMailSpoolEnable(Boolean spoolEnable) throws SecurityException {
		checkWriteAccess();
		boolean hasAccess = ConfigWebUtil.hasAccess(config, SecurityManager.TYPE_MAIL);

		if (!hasAccess) throw new SecurityException("no access to update mail server settings");
		Struct mail = _getRootElement("mail");
		mail.setEL("spool-enable", Caster.toString(spoolEnable, ""));
	}

	/**
	 * sets the timeout for the spooler for one job
	 * 
	 * @param timeout
	 * @throws SecurityException
	 */
	public void setMailTimeout(Integer timeout) throws SecurityException {
		checkWriteAccess();
		boolean hasAccess = ConfigWebUtil.hasAccess(config, SecurityManager.TYPE_MAIL);
		if (!hasAccess) throw new SecurityException("no access to update mail server settings");
		Struct mail = _getRootElement("mail");
		mail.setEL("timeout", Caster.toString(timeout, ""));
	}

	/**
	 * sets the charset for the mail
	 * 
	 * @param charset
	 * @throws SecurityException
	 */
	public void setMailDefaultCharset(String charset) throws PageException {
		checkWriteAccess();
		boolean hasAccess = ConfigWebUtil.hasAccess(config, SecurityManager.TYPE_MAIL);
		if (!hasAccess) throw new SecurityException("no access to update mail server settings");

		if (!StringUtil.isEmpty(charset)) {
			try {
				IOUtil.checkEncoding(charset);
			}
			catch (IOException e) {
				throw Caster.toPageException(e);
			}
		}

		Struct mail = _getRootElement("mail");
		mail.setEL("default-encoding", charset);
		// config.setMailDefaultEncoding(charset);
	}

	/**
	 * insert or update a mailserver on system
	 * 
	 * @param hostName
	 * @param username
	 * @param password
	 * @param port
	 * @param ssl
	 * @param tls
	 * @throws PageException
	 */
	public void updateMailServer(int id, String hostName, String username, String password, int port, boolean tls, boolean ssl, long lifeTimeSpan, long idleTimeSpan,
			boolean reuseConnections) throws PageException {
		checkWriteAccess();
		boolean hasAccess = ConfigWebUtil.hasAccess(config, SecurityManager.TYPE_MAIL);
		if (!hasAccess) throw new SecurityException("no access to update mail server settings");

		Struct mail = _getRootElement("mail");
		if (port < 1) port = 21;

		if (hostName == null || hostName.trim().length() == 0) throw new ExpressionException("Host (SMTP) cannot be an empty value");
		hostName = hostName.trim();

		Array children = ConfigWebUtil.getAsArray("server", mail);

		boolean checkId = id > 0;

		// Update
		Struct server = null;
		String _hostName, _username;
		for (int i = 1; i <= children.size(); i++) {
			Struct el = Caster.toStruct(children.get(i, null), null);
			if (el == null) continue;

			if (checkId) {
				if (i + 1 == id) {
					server = el;
					break;
				}
			}
			else {
				_hostName = StringUtil.emptyIfNull(Caster.toString(el.get("smtp", null)));
				_username = StringUtil.emptyIfNull(Caster.toString(el.get("username", null)));
				if (_hostName.equalsIgnoreCase(hostName) && _username.equals(StringUtil.emptyIfNull(username))) {
					server = el;
					break;
				}
			}
		}

		// Insert
		if (server == null) {
			server = new StructImpl(Struct.TYPE_LINKED);
			children.appendEL(server);
		}
		server.setEL("smtp", hostName);
		server.setEL("username", username);
		server.setEL("password", ConfigWebUtil.encrypt(password));
		server.setEL("port", Caster.toString(port));
		server.setEL("tls", Caster.toString(tls));
		server.setEL("ssl", Caster.toString(ssl));
		server.setEL("life", Caster.toString(lifeTimeSpan));
		server.setEL("idle", Caster.toString(idleTimeSpan));
		server.setEL("reuse-connection", Caster.toString(reuseConnections));
	}

	/**
	 * removes a mailserver from system
	 * 
	 * @param hostName
	 * @throws SecurityException
	 */
	public void removeMailServer(String hostName, String username) throws SecurityException {
		checkWriteAccess();
		Array children = ConfigWebUtil.getAsArray("mail", "server", root);
		// Element mail = _getRootElement("mail");
		// Element[] children = ConfigWebFactory.getChildren(mail, "server");
		String _hostName, _username;
		if (children.size() > 0) {
			for (int i = children.size(); i > 0; i--) {
				Struct el = Caster.toStruct(children.get(i, null), null);
				if (el == null) continue;
				_hostName = Caster.toString(el.get("smtp", null), null);
				_username = Caster.toString(el.get("username", null), null);
				if (StringUtil.emptyIfNull(_hostName).equalsIgnoreCase(StringUtil.emptyIfNull(hostName))
						&& StringUtil.emptyIfNull(_username).equalsIgnoreCase(StringUtil.emptyIfNull(username))) {
					children.removeEL(i);
				}
			}
		}
	}

	public void removeLogSetting(String name) throws SecurityException {
		checkWriteAccess();
		Array children = ConfigWebUtil.getAsArray("logging", "logger", root);
		if (children.size() > 0) {
			String _name;
			for (int i = children.size(); i > 0; i--) {
				Struct el = Caster.toStruct(children.get(i, null), null);
				if (el == null) continue;

				_name = Caster.toString(el.get("name", null), null);

				if (_name != null && _name.equalsIgnoreCase(name)) {
					children.removeEL(i);
				}
			}
		}
	}

	static void updateMapping(ConfigPro config, String virtual, String physical, String archive, String primary, short inspect, boolean toplevel, int listenerMode,
			int listenerType, boolean readonly, boolean reload) throws SAXException, IOException, PageException, BundleException, ConverterException {
		ConfigAdmin admin = new ConfigAdmin(config, null);
		admin._updateMapping(virtual, physical, archive, primary, inspect, toplevel, listenerMode, listenerType, readonly);
		admin._store();
		if (reload) admin._reload();
	}

	static void updateComponentMapping(ConfigPro config, String virtual, String physical, String archive, String primary, short inspect, boolean reload)
			throws SAXException, IOException, PageException, BundleException, ConverterException {
		ConfigAdmin admin = new ConfigAdmin(config, null);
		admin._updateComponentMapping(virtual, physical, archive, primary, inspect);
		admin._store();
		if (reload) admin._reload();
	}

	static void updateCustomTagMapping(ConfigPro config, String virtual, String physical, String archive, String primary, short inspect, boolean reload)
			throws SAXException, IOException, PageException, BundleException, ConverterException {
		ConfigAdmin admin = new ConfigAdmin(config, null);
		admin._updateCustomTag(virtual, physical, archive, primary, inspect);
		admin._store();
		if (reload) admin._reload();
	}

	/**
	 * insert or update a mapping on system
	 * 
	 * @param virtual
	 * @param physical
	 * @param archive
	 * @param primary
	 * @param trusted
	 * @param toplevel
	 * @throws ExpressionException
	 * @throws SecurityException
	 */
	public void updateMapping(String virtual, String physical, String archive, String primary, short inspect, boolean toplevel, int listenerMode, int listenerType,
			boolean readOnly) throws ExpressionException, SecurityException {
		checkWriteAccess();
		_updateMapping(virtual, physical, archive, primary, inspect, toplevel, listenerMode, listenerType, readOnly);
	}

	private void _updateMapping(String virtual, String physical, String archive, String primary, short inspect, boolean toplevel, int listenerMode, int listenerType,
			boolean readOnly) throws ExpressionException, SecurityException {

		boolean hasAccess = ConfigWebUtil.hasAccess(config, SecurityManager.TYPE_MAPPING);
		virtual = virtual.trim();
		if (physical == null) physical = "";
		else physical = physical.trim();
		if (archive == null) archive = "";
		else archive = archive.trim();
		primary = primary.trim();
		if (!hasAccess) throw new SecurityException("no access to update mappings");

		// check virtual
		if (virtual == null || virtual.length() == 0) throw new ExpressionException("virtual path cannot be an empty value");
		virtual = virtual.replace('\\', '/');

		if (!virtual.equals("/") && virtual.endsWith("/")) virtual = virtual.substring(0, virtual.length() - 1);

		if (virtual.charAt(0) != '/') throw new ExpressionException("virtual path must start with [/]");
		boolean isArchive = primary.equalsIgnoreCase("archive");

		if ((physical.length() + archive.length()) == 0) throw new ExpressionException("physical or archive must have a value");

		if (isArchive && archive.length() == 0) isArchive = false;

		if (!isArchive && archive.length() > 0 && physical.length() == 0) isArchive = true;

		Array children = ConfigWebUtil.getAsArray("mappings", "mapping", root);
		// Element mappings = _getRootElement("mappings");
		// Element[] children = ConfigWebFactory.getChildren(mappings, "mapping");

		Struct el = null;
		for (int i = 1; i <= children.size(); i++) {
			Struct tmp = Caster.toStruct(children.get(i, null), null);
			if (tmp == null) continue;

			String v = ConfigWebUtil.getAsString("virtual", tmp, null);
			if (!StringUtil.isEmpty(v)) {
				if (!v.equals("/") && v.endsWith("/")) v = v.substring(0, v.length() - 1);

				if (v.equals(virtual)) {
					el = tmp;
					el.remove("trusted");
					break;
				}
			}
		}

		// create element if necessary
		boolean update = el != null;
		if (el == null) {
			el = new StructImpl(Struct.TYPE_LINKED);
			children.appendEL(el);
			el.setEL("virtual", virtual);
		}

		// physical
		if (physical.length() > 0) {
			el.setEL("physical", physical);
		}
		else if (el.containsKey("physical")) {
			el.remove("physical");
		}

		// archive
		if (archive.length() > 0) {
			el.setEL("archive", archive);
		}
		else if (el.containsKey("archive")) {
			el.remove("archive");
		}

		// primary
		el.setEL("primary", isArchive ? "archive" : "physical");

		// listener-type
		String type = ConfigWebUtil.toListenerType(listenerType, null);
		if (type != null) {
			el.setEL("listener-type", type);
		}
		else if (el.containsKey("listener-type")) {
			el.remove("listener-type");
		}

		// listener-mode
		String mode = ConfigWebUtil.toListenerMode(listenerMode, null);
		if (mode != null) {
			el.setEL("listener-mode", mode);
		}
		else if (el.containsKey("listener-mode")) {
			el.remove("listener-mode");
		}

		// others
		el.setEL("inspect-template", ConfigWebUtil.inspectTemplate(inspect, ""));
		el.setEL("toplevel", Caster.toString(toplevel));
		el.setEL("readonly", Caster.toString(readOnly));

		// set / to the end
		if (!update) {
			children = ConfigWebUtil.getAsArray("mappings", "mapping", root);
			for (int i = 1; i <= children.size(); i++) {
				Struct tmp = Caster.toStruct(children.get(i, null), null);
				if (tmp == null) continue;

				String v = ConfigWebUtil.getAsString("virtual", tmp, null);

				if (v != null && v.equals("/")) {
					children.removeEL(i);
					children.appendEL(tmp);
					return;
				}

			}
		}

	}

	public void updateRestMapping(String virtual, String physical, boolean _default) throws ExpressionException, SecurityException {
		checkWriteAccess();
		boolean hasAccess = true;// TODO ConfigWebUtil.hasAccess(config,SecurityManager.TYPE_REST);
		virtual = virtual.trim();
		physical = physical.trim();
		if (!hasAccess) throw new SecurityException("no access to update REST mapping");

		// check virtual
		if (virtual == null || virtual.length() == 0) throw new ExpressionException("virtual path cannot be an empty value");
		virtual = virtual.replace('\\', '/');
		if (virtual.equals("/")) throw new ExpressionException("virtual path cannot be /");

		if (virtual.endsWith("/")) virtual = virtual.substring(0, virtual.length() - 1);

		if (virtual.charAt(0) != '/') virtual = "/" + virtual;

		if ((physical.length()) == 0) throw new ExpressionException("physical path cannot be an empty value");

		Struct rest = _getRootElement("rest");
		Array children = ConfigWebUtil.getAsArray("mapping", rest);

		// remove existing default
		if (_default) {
			for (int i = 1; i <= children.size(); i++) {
				Struct tmp = Caster.toStruct(children.get(i, null), null);
				if (tmp == null) continue;

				if (Caster.toBooleanValue(tmp.get("default", null), false)) tmp.setEL("default", "false");
			}
		}

		// Update
		String v;
		Struct el = null;
		for (int i = 1; i <= children.size(); i++) {
			Struct tmp = Caster.toStruct(children.get(i, null), null);
			if (tmp == null) continue;

			v = ConfigWebUtil.getAsString("virtual", tmp, null);
			if (v != null && v.equals(virtual)) {
				el = tmp;
			}
		}

		// Insert
		if (el == null) {
			el = new StructImpl(Struct.TYPE_LINKED);
			children.appendEL(el);
		}

		el.setEL("virtual", virtual);
		el.setEL("physical", physical);
		el.setEL("default", Caster.toString(_default));
	}

	/**
	 * delete a mapping on system
	 * 
	 * @param virtual
	 * @throws ExpressionException
	 * @throws SecurityException
	 */
	public void removeMapping(String virtual) throws ExpressionException, SecurityException {
		checkWriteAccess();
		_removeMapping(virtual);
	}

	public void _removeMapping(String virtual) throws ExpressionException {
		// check parameters
		if (virtual == null || virtual.length() == 0) throw new ExpressionException("virtual path cannot be an empty value");
		virtual = virtual.replace('\\', '/');

		if (!virtual.equals("/") && virtual.endsWith("/")) virtual = virtual.substring(0, virtual.length() - 1);
		if (virtual.charAt(0) != '/') throw new ExpressionException("virtual path must start with [/]");

		Array children = ConfigWebUtil.getAsArray("mappings", "mapping", root);
		for (int i = children.size(); i > 0; i--) {
			Struct tmp = Caster.toStruct(children.get(i, null), null);
			if (tmp == null) continue;

			String v = ConfigWebUtil.getAsString("virtual", tmp, null);
			if (v != null) {
				if (!v.equals("/") && v.endsWith("/")) v = v.substring(0, v.length() - 1);
				if (v != null && v.equals(virtual)) {
					children.removeEL(i);
				}
			}
		}
	}

	public void removeRestMapping(String virtual) throws ExpressionException, SecurityException {
		checkWriteAccess();
		// check parameters
		if (virtual == null || virtual.length() == 0) throw new ExpressionException("virtual path cannot be an empty value");
		virtual = virtual.replace('\\', '/');
		if (virtual.equals("/")) throw new ExpressionException("virtual path cannot be /");

		if (virtual.endsWith("/")) virtual = virtual.substring(0, virtual.length() - 1);
		if (virtual.charAt(0) != '/') virtual = "/" + virtual;

		Array children = ConfigWebUtil.getAsArray("rest", "mapping", root);
		for (int i = children.size(); i > 0; i--) {
			Struct tmp = Caster.toStruct(children.get(i, null), null);
			if (tmp == null) continue;

			String v = ConfigWebUtil.getAsString("virtual", tmp, null);
			if (v != null) {
				if (!v.equals("/") && v.endsWith("/")) v = v.substring(0, v.length() - 1);
				if (v != null && v.equals(virtual)) {
					children.removeEL(i);
				}
			}
		}
	}

	/**
	 * delete a customtagmapping on system
	 * 
	 * @param virtual
	 * @throws SecurityException
	 */
	public void removeCustomTag(String virtual) throws SecurityException {
		checkWriteAccess();

		Array children = ConfigWebUtil.getAsArray("custom-tag", "mapping", root);
		for (int i = children.size(); i > 0; i--) {
			Struct tmp = Caster.toStruct(children.get(i, null), null);
			if (tmp == null) continue;

			if (virtual.equals(createVirtual(tmp))) {
				children.removeEL(i);
			}
		}
	}

	public void removeComponentMapping(String virtual) throws SecurityException {
		checkWriteAccess();

		Array children = ConfigWebUtil.getAsArray("component", "mapping", root);
		String v;
		for (int i = children.size(); i > 0; i--) {
			Struct tmp = Caster.toStruct(children.get(i, null), null);
			if (tmp == null) continue;

			if (virtual.equals(createVirtual(tmp))) {
				children.removeEL(i);
			}
		}
	}

	/**
	 * insert or update a mapping for Custom Tag
	 * 
	 * @param virtual
	 * @param physical
	 * @param archive
	 * @param primary
	 * @param trusted
	 * @throws ExpressionException
	 * @throws SecurityException
	 */
	public void updateCustomTag(String virtual, String physical, String archive, String primary, short inspect) throws ExpressionException, SecurityException {
		checkWriteAccess();
		_updateCustomTag(virtual, physical, archive, primary, inspect);
	}

	private void _updateCustomTag(String virtual, String physical, String archive, String primary, short inspect) throws ExpressionException, SecurityException {
		boolean hasAccess = ConfigWebUtil.hasAccess(config, SecurityManager.TYPE_CUSTOM_TAG);
		if (!hasAccess) throw new SecurityException("no access to change custom tag settings");
		if (physical == null) physical = "";
		if (archive == null) archive = "";

		// virtual="/custom-tag";
		if (StringUtil.isEmpty(virtual)) virtual = createVirtual(physical, archive);

		boolean isArchive = primary.equalsIgnoreCase("archive");
		if (isArchive && archive.length() == 0) {
			throw new ExpressionException("archive must have a value when primary has value archive");
		}
		if (!isArchive && physical.length() == 0) {
			throw new ExpressionException("physical must have a value when primary has value physical");
		}

		Array children = ConfigWebUtil.getAsArray("custom-tag", "mapping", root);
		// Struct mappings = _getRootElement("custom-tag");

		// Update
		String v;
		// Element[] children = ConfigWebFactory.getChildren(mappings, "mapping");
		for (int i = 1; i <= children.size(); i++) {
			Struct el = Caster.toStruct(children.get(i, null), null);
			if (el == null) continue;

			v = createVirtual(el);
			if (v.equals(virtual)) {
				el.setEL("virtual", v);
				el.setEL("physical", physical);
				el.setEL("archive", archive);
				el.setEL("primary", primary.equalsIgnoreCase("archive") ? "archive" : "physical");
				el.setEL("inspect-template", ConfigWebUtil.inspectTemplate(inspect, ""));
				el.removeEL(KeyImpl.init("trusted"));
				return;
			}
		}

		// Insert
		Struct el = new StructImpl(Struct.TYPE_LINKED);
		children.appendEL(el);
		if (physical.length() > 0) el.setEL("physical", physical);
		if (archive.length() > 0) el.setEL("archive", archive);
		el.setEL("primary", primary.equalsIgnoreCase("archive") ? "archive" : "physical");
		el.setEL("inspect-template", ConfigWebUtil.inspectTemplate(inspect, ""));
		el.setEL("virtual", virtual);
	}

	public void updateComponentMapping(String virtual, String physical, String archive, String primary, short inspect) throws ExpressionException, SecurityException {
		checkWriteAccess();
		_updateComponentMapping(virtual, physical, archive, primary, inspect);
	}

	private void _updateComponentMapping(String virtual, String physical, String archive, String primary, short inspect) throws ExpressionException {
		primary = primary.equalsIgnoreCase("archive") ? "archive" : "physical";
		if (physical == null) physical = "";
		else physical = physical.trim();

		if (archive == null) archive = "";
		else archive = archive.trim();

		boolean isArchive = primary.equalsIgnoreCase("archive");
		if (isArchive && archive.length() == 0) {
			throw new ExpressionException("archive must have a value when primary has value archive");
		}
		if (!isArchive && physical.length() == 0) {
			throw new ExpressionException("physical must have a value when primary has value physical");
		}

		Array children = ConfigWebUtil.getAsArray("component", "mapping", root);
		// Element mappings = _getRootElement("component");
		// Element[] children = ConfigWebFactory.getChildren(mappings, "mapping");
		Struct el;

		// Update
		String v;
		for (int i = 1; i <= children.size(); i++) {
			el = Caster.toStruct(children.get(i, null), null);
			if (el == null) continue;

			v = createVirtual(el); // if there is no virtual definition (old records), we use the position
			if (v.equals(virtual)) {
				el.setEL("virtual", v); // set to make sure it exists for the future
				el.setEL("physical", physical);
				el.setEL("archive", archive);
				el.setEL("primary", primary.equalsIgnoreCase("archive") ? "archive" : "physical");
				el.setEL("inspect-template", ConfigWebUtil.inspectTemplate(inspect, ""));
				el.removeEL(KeyImpl.init("trusted"));
				return;
			}
		}

		// Insert
		el = new StructImpl(Struct.TYPE_LINKED);
		children.appendEL(el);
		if (physical.length() > 0) el.setEL("physical", physical);
		if (archive.length() > 0) el.setEL("archive", archive);
		el.setEL("primary", primary.equalsIgnoreCase("archive") ? "archive" : "physical");
		el.setEL("inspect-template", ConfigWebUtil.inspectTemplate(inspect, ""));
		el.setEL("virtual", virtual);
	}

	public static String createVirtual(Struct data) {
		String str = ConfigWebFactory.getAttr(data, "virtual");
		if (!StringUtil.isEmpty(str)) return str;
		return createVirtual(ConfigWebFactory.getAttr(data, "physical"), ConfigWebFactory.getAttr(data, "archive"));
	}

	public static String createVirtual(String physical, String archive) {
		return "/" + MD5.getDigestAsString(physical + ":" + archive, "");
	}

	public void updateJar(Resource resJar) throws IOException, BundleException {
		updateJar(config, resJar, true);
	}

	public static void updateJar(Config config, Resource resJar, boolean reloadWhenClassicJar) throws IOException, BundleException {
		BundleFile bf = BundleFile.getInstance(resJar);

		// resJar is a bundle
		if (bf.isBundle()) {
			bf = installBundle(config, bf);
			OSGiUtil.loadBundle(bf);
			return;
		}

		Resource lib = ((ConfigPro) config).getLibraryDirectory();
		if (!lib.exists()) lib.mkdir();
		Resource fileLib = lib.getRealResource(resJar.getName());

		// if there is an existing, has the file changed?
		if (fileLib.length() != resJar.length()) {
			IOUtil.closeEL(config.getClassLoader());
			ResourceUtil.copy(resJar, fileLib);
			if (reloadWhenClassicJar) ConfigWebUtil.reloadLib(config);
		}
	}

	/*
	 * important! returns null when not a bundle!
	 */
	static BundleFile installBundle(Config config, Resource resJar, String extVersion, boolean convert2bundle) throws IOException, BundleException {

		BundleFile bf = BundleFile.getInstance(resJar);

		// resJar is a bundle
		if (bf.isBundle()) {
			return installBundle(config, bf);
		}

		if (!convert2bundle) return null;

		// name
		String name = bf.getSymbolicName();
		if (StringUtil.isEmpty(name)) name = BundleBuilderFactory.createSymbolicName(resJar);

		// version
		Version version = bf.getVersion();
		if (version == null) version = OSGiUtil.toVersion(extVersion);

		LogUtil.log(ThreadLocalPageContext.getConfig(config), Log.LEVEL_INFO, ConfigAdmin.class.getName(), "failed to load [" + resJar + "] as OSGi Bundle");
		BundleBuilderFactory bbf = new BundleBuilderFactory(resJar, name);
		bbf.setVersion(version);
		bbf.setIgnoreExistingManifest(false);
		bbf.build();

		bf = BundleFile.getInstance(resJar);
		LogUtil.log(ThreadLocalPageContext.getConfig(config), Log.LEVEL_INFO, ConfigAdmin.class.getName(), "converted  [" + resJar + "] to an OSGi Bundle");
		return installBundle(config, bf);
	}

	private static BundleFile installBundle(Config config, BundleFile bf) throws IOException, BundleException {

		// does this bundle already exists
		BundleFile _bf = OSGiUtil.getBundleFile(bf.getSymbolicName(), bf.getVersion(), null, null, false, null);
		if (_bf != null) return _bf;

		CFMLEngine engine = CFMLEngineFactory.getInstance();
		CFMLEngineFactory factory = engine.getCFMLEngineFactory();

		// copy to jar directory
		File jar = new File(factory.getBundleDirectory(), bf.getSymbolicName() + "-" + bf.getVersion().toString() + (".jar"));

		InputStream is = bf.getInputStream();
		OutputStream os = new FileOutputStream(jar);
		try {
			IOUtil.copy(is, os, false, false);
		}
		finally {
			IOUtil.close(is, os);
		}

		return BundleFile.getInstance(jar);
	}

	/**
	 * 
	 * @param config
	 * @param is
	 * @param name
	 * @param extensionVersion if given jar is no bundle the extension version is used for the bundle
	 *            created
	 * @param closeStream
	 * @return
	 * @throws IOException
	 * @throws BundleException
	 */
	static Bundle updateBundle(Config config, InputStream is, String name, String extensionVersion, boolean closeStream) throws IOException, BundleException {
		Object obj = installBundle(config, is, name, extensionVersion, closeStream, false);
		if (!(obj instanceof BundleFile)) throw new BundleException("input is not an OSGi Bundle.");

		BundleFile bf = (BundleFile) obj;
		return OSGiUtil.loadBundle(bf);
	}

	/**
	 * @param config
	 * @param is
	 * @param name
	 * @param extensionVersion
	 * @param closeStream
	 * @param convert2bundle
	 * @return return the Bundle File or the file in case it is not a bundle.
	 * @throws IOException
	 * @throws BundleException
	 */
	public static Object installBundle(Config config, InputStream is, String name, String extensionVersion, boolean closeStream, boolean convert2bundle)
			throws IOException, BundleException {
		Resource tmp = SystemUtil.getTempDirectory().getRealResource(name);
		OutputStream os = tmp.getOutputStream();
		IOUtil.copy(is, os, closeStream, true);

		BundleFile bf = installBundle(config, tmp, extensionVersion, convert2bundle);
		if (bf != null) {
			tmp.delete();
			return bf;
		}
		return tmp;
	}

	static void updateJar(Config config, InputStream is, String name, boolean closeStream) throws IOException, BundleException {
		Resource tmp = SystemUtil.getTempDirectory().getRealResource(name);
		try {
			IOUtil.copy(is, tmp, closeStream);
			updateJar(config, tmp, true);
		}
		finally {
			tmp.delete();
		}
	}

	/**
	 * insert or update a Java CFX Tag
	 * 
	 * @param name
	 * @param strClass
	 * @throws PageException
	 */
	public void updateJavaCFX(String name, ClassDefinition cd) throws PageException {
		checkWriteAccess();
		boolean hasAccess = ConfigWebUtil.hasAccess(config, SecurityManager.TYPE_CFX_SETTING);

		if (!hasAccess) throw new SecurityException("no access to change cfx settings");

		if (name == null || name.length() == 0) throw new ExpressionException("class name can't be an empty value");

		Array children = ConfigWebUtil.getAsArray("ext-tags", "ext-tag", root);

		// Update
		for (int i = 1; i <= children.size(); i++) {
			Struct tmp = Caster.toStruct(children.get(i, null), null);
			if (tmp == null) continue;
			String n = ConfigWebUtil.getAsString("name", tmp, null);

			if (n != null && n.equalsIgnoreCase(name)) {
				Struct el = tmp;
				if (!"java".equalsIgnoreCase(ConfigWebUtil.getAsString("type", el, ""))) throw new ExpressionException("there is already a c++ cfx tag with this name");
				setClass(el, CustomTag.class, "", cd);
				el.setEL("type", "java");
				return;
			}

		}

		// Insert
		Struct el = new StructImpl(Struct.TYPE_LINKED);
		children.appendEL(el);
		setClass(el, CustomTag.class, "", cd);
		el.setEL("name", name);
		el.setEL("type", "java");
	}

	public void verifyCFX(String name) throws PageException {
		CFXTagPool pool = config.getCFXTagPool();
		CustomTag ct = null;
		try {
			ct = pool.getCustomTag(name);
		}
		catch (CFXTagException e) {
			throw Caster.toPageException(e);
		}
		finally {
			if (ct != null) pool.releaseCustomTag(ct);
		}

	}

	public void verifyJavaCFX(String name, ClassDefinition cd) throws PageException {
		try {
			Class clazz = cd.getClazz();
			if (!Reflector.isInstaneOf(clazz, CustomTag.class, false))
				throw new ExpressionException("class [" + cd + "] must implement interface [" + CustomTag.class.getName() + "]");
		}
		catch (ClassException e) {
			throw Caster.toPageException(e);
		}
		catch (BundleException e) {
			throw Caster.toPageException(e);
		}

		if (StringUtil.startsWithIgnoreCase(name, "cfx_")) name = name.substring(4);
		if (StringUtil.isEmpty(name)) throw new ExpressionException("class name can't be an empty value");
	}

	/**
	 * remove a CFX Tag
	 * 
	 * @param name
	 * @throws ExpressionException
	 * @throws SecurityException
	 */
	public void removeCFX(String name) throws ExpressionException, SecurityException {
		checkWriteAccess();
		// check parameters
		if (name == null || name.length() == 0) throw new ExpressionException("name for CFX Tag can be an empty value");

		Array children = ConfigWebUtil.getAsArray("ext-tags", "ext-tag", root);
		for (int i = children.size(); i > 0; i--) {
			Struct tmp = Caster.toStruct(children.get(i, null), null);
			if (tmp == null) continue;

			String n = ConfigWebUtil.getAsString("name", tmp, null);
			if (n != null && n.equalsIgnoreCase(name)) {
				children.removeEL(i);
			}
		}
	}

	/**
	 * update or insert new database connection
	 * 
	 * @param name
	 * @param clazzName
	 * @param dsn
	 * @param username
	 * @param password
	 * @param host
	 * @param database
	 * @param port
	 * @param connectionLimit
	 * @param connectionTimeout
	 * @param blob
	 * @param clob
	 * @param allow
	 * @param storage
	 * @param custom
	 * @throws PageException
	 */
	public void updateDataSource(String id, String name, String newName, ClassDefinition cd, String dsn, String username, String password, String host, String database, int port,
			int connectionLimit, int idleTimeout, int liveTimeout, long metaCacheTimeout, boolean blob, boolean clob, int allow, boolean validate, boolean storage, String timezone,
			Struct custom, String dbdriver, ParamSyntax paramSyntax, boolean literalTimestampWithTSOffset, boolean alwaysSetTimeout, boolean requestExclusive,
			boolean alwaysResetConnections) throws PageException {

		checkWriteAccess();
		SecurityManager sm = config.getSecurityManager();
		short access = sm.getAccess(SecurityManager.TYPE_DATASOURCE);
		boolean hasAccess = true;
		boolean hasInsertAccess = true;
		int maxLength = 0;

		if (access == SecurityManager.VALUE_YES) hasAccess = true;
		else if (access == SecurityManager.VALUE_NO) hasAccess = false;
		else if (access >= SecurityManager.VALUE_1 && access <= SecurityManager.VALUE_10) {
			int existingLength = getDatasourceLength(config);
			maxLength = access - SecurityManager.NUMBER_OFFSET;
			hasInsertAccess = maxLength > existingLength;
			// print.ln("maxLength:"+maxLength);
			// print.ln("existingLength:"+existingLength);
		}
		// print.ln("hasAccess:"+hasAccess);
		// print.ln("hasInsertAccess:"+hasInsertAccess);

		// boolean hasAccess=ConfigWebUtil.hasAccess(config,SecurityManager.TYPE_DATASOURCE);
		if (!hasAccess) throw new SecurityException("no access to update datsource connections");

		// check parameters
		if (name == null || name.length() == 0) throw new ExpressionException("name can't be an empty value");

		Array children = ConfigWebUtil.getAsArray("data-sources", "data-source", root);
		for (int i = 1; i <= children.size(); i++) {
			Struct tmp = Caster.toStruct(children.get(i, null), null);
			if (tmp == null) continue;

			String n = ConfigWebUtil.getAsString("name", tmp, "");

			if (n.equalsIgnoreCase(name)) {
				Struct el = tmp;
				if (password.equalsIgnoreCase("****************")) password = ConfigWebUtil.getAsString("password", el, null);

				if (!StringUtil.isEmpty(newName) && !newName.equals(name)) el.setEL("name", newName);
				setClass(el, null, "", cd);

				if (!StringUtil.isEmpty(id)) el.setEL(KeyConstants._id, id);
				else if (el.containsKey(KeyConstants._id)) el.removeEL(KeyConstants._id);

				el.setEL("dsn", dsn);
				el.setEL("username", username);
				el.setEL("password", ConfigWebUtil.encrypt(password));

				el.setEL("host", host);
				if (!StringUtil.isEmpty(timezone)) el.setEL(KeyConstants._timezone, timezone);
				else if (el.containsKey(KeyConstants._timezone)) el.removeEL(KeyConstants._timezone);
				el.setEL("database", database);
				el.setEL("port", Caster.toString(port));
				el.setEL("connectionLimit", Caster.toString(connectionLimit));
				el.setEL("connectionTimeout", Caster.toString(idleTimeout));
				el.setEL("liveTimeout", Caster.toString(liveTimeout));
				el.setEL("metaCacheTimeout", Caster.toString(metaCacheTimeout));
				el.setEL("blob", Caster.toString(blob));
				el.setEL("clob", Caster.toString(clob));
				el.setEL("allow", Caster.toString(allow));
				el.setEL("validate", Caster.toString(validate));
				el.setEL("storage", Caster.toString(storage));
				el.setEL("custom", toStringURLStyle(custom));

				if (!StringUtil.isEmpty(dbdriver)) el.setEL("dbdriver", Caster.toString(dbdriver));

				// Param Syntax
				el.setEL("param-delimiter", (paramSyntax.delimiter));
				el.setEL("param-leading-delimiter", (paramSyntax.leadingDelimiter));
				el.setEL("param-separator", (paramSyntax.separator));

				if (literalTimestampWithTSOffset) el.setEL("literal-timestamp-with-tsoffset", "true");
				else if (el.containsKey("literal-timestamp-with-tsoffset")) el.removeEL(KeyImpl.init("literal-timestamp-with-tsoffset"));

				if (alwaysSetTimeout) el.setEL("always-set-timeout", "true");
				else if (el.containsKey("always-set-timeout")) el.removeEL(KeyImpl.init("always-set-timeout"));

				if (requestExclusive) el.setEL("request-exclusive", "true");
				else if (el.containsKey("request-exclusive")) el.removeEL(KeyImpl.init("request-exclusive"));

				if (alwaysResetConnections) el.setEL("always-reset-connections", "true");
				else if (el.containsKey("always-reset-connections")) el.removeEL(KeyImpl.init("always-reset-connections"));

				return;
			}
		}

		if (!hasInsertAccess) throw new SecurityException("Unable to add a datasource connection, the maximum count of [" + maxLength + "] datasources has been reached. "
				+ " This can be configured in the Server Admin, under Security, Access");

		// Insert
		Struct el = new StructImpl(Struct.TYPE_LINKED);
		children.appendEL(el);
		if (!StringUtil.isEmpty(newName)) el.setEL("name", newName);
		else el.setEL("name", name);
		setClass(el, null, "", cd);
		el.setEL("dsn", dsn);

		if (!StringUtil.isEmpty(id)) el.setEL(KeyConstants._id, id);
		else if (el.containsKey(KeyConstants._id)) el.removeEL(KeyConstants._id);

		if (username.length() > 0) el.setEL(KeyConstants._username, username);
		if (password.length() > 0) el.setEL(KeyConstants._password, ConfigWebUtil.encrypt(password));

		el.setEL("host", host);
		if (!StringUtil.isEmpty(timezone)) el.setEL("timezone", timezone);
		el.setEL("database", database);
		if (port > -1) el.setEL("port", Caster.toString(port));
		if (connectionLimit > -1) el.setEL("connectionLimit", Caster.toString(connectionLimit));
		if (idleTimeout > -1) el.setEL("connectionTimeout", Caster.toString(idleTimeout));
		if (liveTimeout > -1) el.setEL("liveTimeout", Caster.toString(liveTimeout));
		if (metaCacheTimeout > -1) el.setEL("metaCacheTimeout", Caster.toString(metaCacheTimeout));

		el.setEL("blob", Caster.toString(blob));
		el.setEL("clob", Caster.toString(clob));
		el.setEL("validate", Caster.toString(validate));
		el.setEL("storage", Caster.toString(storage));
		if (allow > -1) el.setEL("allow", Caster.toString(allow));
		el.setEL("custom", toStringURLStyle(custom));

		if (!StringUtil.isEmpty(dbdriver)) el.setEL("dbdriver", Caster.toString(dbdriver));

		// Param Syntax
		el.setEL("param-delimiter", (paramSyntax.delimiter));
		el.setEL("param-leading-delimiter", (paramSyntax.leadingDelimiter));
		el.setEL("param-separator", (paramSyntax.separator));

		if (literalTimestampWithTSOffset) el.setEL("literal-timestamp-with-tsoffset", "true");
		if (alwaysSetTimeout) el.setEL("always-set-timeout", "true");
		if (requestExclusive) el.setEL("request-exclusive", "true");
		if (alwaysResetConnections) el.setEL("always-reset-connections", "true");

	}

	static void removeJDBCDriver(ConfigPro config, ClassDefinition cd, boolean reload) throws IOException, SAXException, PageException, BundleException, ConverterException {
		ConfigAdmin admin = new ConfigAdmin(config, null);
		admin._removeJDBCDriver(cd);
		admin._store(); // store is necessary, otherwise it get lost

		if (reload) admin._reload();
	}

	private void _removeJDBCDriver(ClassDefinition cd) throws PageException {

		if (!cd.isBundle()) throw new ApplicationException("missing bundle name");

		Array children = ConfigWebUtil.getAsArray("jdbc", "driver", root);

		// Remove
		for (int i = children.size(); i > 0; i--) {
			Struct tmp = Caster.toStruct(children.get(i, null), null);
			if (tmp == null) continue;

			String n = ConfigWebUtil.getAsString("class", tmp, "");
			if (n.equalsIgnoreCase(cd.getClassName())) {
				children.removeEL(i);
				break;
			}
		}

		// now unload (maybe not necessary)
		if (cd.isBundle()) {
			Bundle bl = OSGiUtil.getBundleLoaded(cd.getName(), cd.getVersion(), null);
			if (bl != null) {
				try {
					OSGiUtil.uninstall(bl);
				}
				catch (BundleException e) {}
			}
		}
	}

	private void _removeStartupHook(ClassDefinition cd) throws PageException {

		if (!cd.isBundle()) throw new ApplicationException("missing bundle name");

		Array children = ConfigWebUtil.getAsArray("startup", "hook", root);
		// Remove
		for (int i = children.size(); i > 0; i--) {
			Struct tmp = Caster.toStruct(children.get(i, null), null);
			if (tmp == null) continue;

			String n = ConfigWebUtil.getAsString("class", tmp, "");
			if (n.equalsIgnoreCase(cd.getClassName())) {
				children.removeEL(i);
				break;
			}
		}

		// now unload (maybe not necessary)
		if (cd.isBundle()) {
			unloadStartupIfNecessary(config, cd, true);
			Bundle bl = OSGiUtil.getBundleLoaded(cd.getName(), cd.getVersion(), null);
			if (bl != null) {
				try {
					OSGiUtil.uninstall(bl);
				}
				catch (BundleException e) {}
			}
		}
	}

	private void unloadStartupIfNecessary(ConfigPro config, ClassDefinition<?> cd, boolean force) {
		ConfigBase.Startup startup = config.getStartups().get(cd.getClassName());
		if (startup == null) return;
		if (startup.cd.equals(cd) && !force) return;

		try {
			Method fin = Reflector.getMethod(startup.instance.getClass(), "finalize", new Class[0], null);
			if (fin != null) {
				fin.invoke(startup.instance, new Object[0]);
			}
			config.getStartups().remove(cd.getClassName());
		}
		catch (Exception e) {}
	}

	public void updateJDBCDriver(String label, String id, ClassDefinition cd) throws PageException {
		checkWriteAccess();
		_updateJDBCDriver(label, id, cd);
	}

	private void _updateJDBCDriver(String label, String id, ClassDefinition cd) throws PageException {

		// check if label exists
		if (StringUtil.isEmpty(label)) throw new ApplicationException("missing label for jdbc driver [" + cd.getClassName() + "]");
		// check if it is a bundle
		if (!cd.isBundle()) throw new ApplicationException("missing bundle name for [" + label + "]");

		Array children = ConfigWebUtil.getAsArray("jdbc", "driver", root);

		// Update
		Struct child = null;
		for (int i = 1; i <= children.size(); i++) {
			Struct tmp = Caster.toStruct(children.get(i, null), null);
			if (tmp == null) continue;

			String n = ConfigWebUtil.getAsString("class", tmp, "");
			if (n.equalsIgnoreCase(cd.getClassName())) {
				child = tmp;
				break;
			}
		}

		// Insert
		if (child == null) {
			child = new StructImpl(Struct.TYPE_LINKED);
			children.appendEL(child);
		}

		child.setEL("label", label);
		if (!StringUtil.isEmpty(id)) child.setEL(KeyConstants._id, id);
		else child.removeEL(KeyConstants._id);
		// make sure the class exists
		setClass(child, null, "", cd);

		// now unload again, JDBC driver can be loaded when necessary
		if (cd.isBundle()) {
			Bundle bl = OSGiUtil.getBundleLoaded(cd.getName(), cd.getVersion(), null);
			if (bl != null) {
				try {
					OSGiUtil.uninstall(bl);
				}
				catch (BundleException e) {}
			}
		}
	}

	private void _updateStartupHook(ClassDefinition cd) throws PageException {
		unloadStartupIfNecessary(config, cd, false);
		// check if it is a bundle
		if (!cd.isBundle()) throw new ApplicationException("missing bundle info");

		Array children = ConfigWebUtil.getAsArray("startup", "hook", root);

		// Update
		Struct child = null;
		for (int i = 1; i <= children.size(); i++) {
			Struct tmp = Caster.toStruct(children.get(i, null), null);
			if (tmp == null) continue;

			String n = ConfigWebUtil.getAsString("class", tmp, null);
			if (n.equalsIgnoreCase(cd.getClassName())) {
				child = tmp;
				break;
			}
		}

		// Insert
		if (child == null) {
			child = new StructImpl(Struct.TYPE_LINKED);
			children.appendEL(child);
		}

		// make sure the class exists
		setClass(child, null, "", cd);

		// now unload again, JDBC driver can be loaded when necessary
		if (cd.isBundle()) {
			Bundle bl = OSGiUtil.getBundleLoaded(cd.getName(), cd.getVersion(), null);
			if (bl != null) {
				try {
					OSGiUtil.uninstall(bl);
				}
				catch (BundleException e) {}
			}
		}
	}

	public void updateGatewayEntry(String id, ClassDefinition cd, String componentPath, String listenerCfcPath, int startupMode, Struct custom, boolean readOnly)
			throws PageException {
		checkWriteAccess();
		boolean hasAccess = ConfigWebUtil.hasAccess(config, SecurityManagerImpl.TYPE_GATEWAY);
		if (!hasAccess) throw new SecurityException("no access to update gateway entry");

		_updateGatewayEntry(id, cd, componentPath, listenerCfcPath, startupMode, custom, readOnly);
	}

	void _updateGatewayEntry(String id, ClassDefinition cd, String componentPath, String listenerCfcPath, int startupMode, Struct custom, boolean readOnly) throws PageException {

		// check parameters
		id = id.trim();
		if (StringUtil.isEmpty(id)) throw new ExpressionException("id can't be an empty value");

		if ((cd == null || StringUtil.isEmpty(cd.getClassName())) && StringUtil.isEmpty(componentPath)) throw new ExpressionException("you must define className or componentPath");

		Array children = ConfigWebUtil.getAsArray("gateways", "gateway", root);

		// Update
		for (int i = 1; i <= children.size(); i++) {
			Struct el = Caster.toStruct(children.get(i, null), null);
			if (el == null) continue;

			String n = ConfigWebUtil.getAsString("id", el, "");
			if (n.equalsIgnoreCase(id)) {
				setClass(el, null, "", cd);
				el.setEL("cfc-path", componentPath);
				el.setEL("listener-cfc-path", listenerCfcPath);
				el.setEL("startup-mode", GatewayEntryImpl.toStartup(startupMode, "automatic"));
				el.setEL("custom", toStringURLStyle(custom));
				el.setEL("read-only", Caster.toString(readOnly));
				return;
			}

		}
		// Insert
		Struct el = new StructImpl(Struct.TYPE_LINKED);
		children.appendEL(el);
		el.setEL("id", id);
		el.setEL("cfc-path", componentPath);
		el.setEL("listener-cfc-path", listenerCfcPath);
		el.setEL("startup-mode", GatewayEntryImpl.toStartup(startupMode, "automatic"));
		setClass(el, null, "", cd);
		el.setEL("custom", toStringURLStyle(custom));
		el.setEL("read-only", Caster.toString(readOnly));

	}

	static void removeSearchEngine(ConfigPro config, boolean reload) throws IOException, SAXException, PageException, BundleException, ConverterException {
		ConfigAdmin admin = new ConfigAdmin(config, null);
		admin._removeSearchEngine();
		admin._store();
		if (reload) admin._reload();
	}

	private void _removeSearchEngine() {
		Struct orm = _getRootElement("search");
		removeClass(orm, "engine-");
	}

	private void _removeAMFEngine() {
		Struct flex = _getRootElement("flex");
		removeClass(flex, "");
		flex.removeEL(KeyImpl.init("configuration"));
		flex.removeEL(KeyImpl.init("caster"));

		// old arguments
		flex.removeEL(KeyImpl.init("config"));
		flex.removeEL(KeyImpl.init("caster-class"));
		flex.removeEL(KeyImpl.init("caster-class-arguments"));
	}

	public void updateSearchEngine(ClassDefinition cd) throws PageException {
		checkWriteAccess();
		_updateSearchEngine(cd);

	}

	private void _updateSearchEngine(ClassDefinition cd) throws PageException {
		Struct orm = _getRootElement("search");
		setClass(orm, SearchEngine.class, "engine-", cd);
	}

	private void _updateAMFEngine(ClassDefinition cd, String caster, String config) throws PageException {
		Struct flex = _getRootElement("flex");
		setClass(flex, AMFEngine.class, "", cd);
		if (caster != null) flex.setEL("caster", caster);
		if (config != null) flex.setEL("configuration", config);
		// old arguments
		flex.removeEL(KeyImpl.init("config"));
		flex.removeEL(KeyImpl.init("caster-class"));
		flex.removeEL(KeyImpl.init("caster-class-arguments"));
	}

	public void removeSearchEngine() throws SecurityException {
		checkWriteAccess();

		Struct orm = _getRootElement("search");
		removeClass(orm, "engine-");

	}

	static void removeORMEngine(ConfigPro config, boolean reload) throws IOException, SAXException, PageException, BundleException, ConverterException {
		ConfigAdmin admin = new ConfigAdmin(config, null);
		admin._removeORMEngine();
		admin._store();
		if (reload) admin._reload();
	}

	private void _removeORMEngine() {
		Struct orm = _getRootElement("orm");
		removeClass(orm, "engine-");
		removeClass(orm, "");// in the beginning we had no prefix
	}

	private void _removeWebserviceHandler() {
		Struct orm = _getRootElement("webservice");
		removeClass(orm, "");
	}

	public void removeORMEngine() throws SecurityException {
		checkWriteAccess();
		_removeORMEngine();
	}

	public void updateORMEngine(ClassDefinition cd) throws PageException {
		checkWriteAccess();
		_updateORMEngine(cd);

	}

	private void _updateORMEngine(ClassDefinition cd) throws PageException {
		Struct orm = _getRootElement("orm");
		removeClass(orm, "");// in the beginning we had no prefix
		setClass(orm, ORMEngine.class, "engine-", cd);
	}

	private void _updateWebserviceHandler(ClassDefinition cd) throws PageException {
		Struct orm = _getRootElement("webservice");
		setClass(orm, null, "", cd);
	}

	public void updateCacheConnection(String name, ClassDefinition cd, int _default, Struct custom, boolean readOnly, boolean storage) throws PageException {

		checkWriteAccess();
		boolean hasAccess = ConfigWebUtil.hasAccess(config, SecurityManagerImpl.TYPE_CACHE);
		if (!hasAccess) throw new SecurityException("no access to update cache connection");

		// check parameters
		name = name.trim();
		if (StringUtil.isEmpty(name)) throw new ExpressionException("name can't be an empty value");
		// else if(name.equals("template") || name.equals("object"))
		// throw new ExpressionException("name ["+name+"] is not allowed for a cache connection, the
		// following names are reserved words [object,template]");

		try {
			Class clazz;
			if (cd.getClassName() != null && cd.getClassName().endsWith(".EHCacheLite"))
				clazz = ClassUtil.loadClass(config.getClassLoader(), "org.lucee.extension.cache.eh.EHCache");
			else clazz = ClassUtil.loadClass(config.getClassLoader(), cd.getClassName());

			if (!Reflector.isInstaneOf(clazz, Cache.class, false)) throw new ExpressionException("class [" + clazz.getName() + "] is not of type [" + Cache.class.getName() + "]");
		}
		catch (ClassException e) {
			throw new ExpressionException(e.getMessage());
		}

		Struct parent = _getRootElement("cache");

		if (name.equalsIgnoreCase(Caster.toString(parent.get("default-template", null), null))) rem(parent, "default-template");
		if (name.equalsIgnoreCase(Caster.toString(parent.get("default-object", null), null))) rem(parent, "default-object");
		if (name.equalsIgnoreCase(Caster.toString(parent.get("default-query", null), null))) rem(parent, "default-query");
		if (name.equalsIgnoreCase(Caster.toString(parent.get("default-resource", null), null))) rem(parent, "default-resource");
		if (name.equalsIgnoreCase(Caster.toString(parent.get("default-function", null), null))) rem(parent, "default-function");
		if (name.equalsIgnoreCase(Caster.toString(parent.get("default-include", null), null))) rem(parent, "default-include");

		if (_default == ConfigPro.CACHE_TYPE_OBJECT) {
			parent.setEL("default-object", name);
		}
		else if (_default == ConfigPro.CACHE_TYPE_TEMPLATE) {
			parent.setEL("default-template", name);
		}
		else if (_default == ConfigPro.CACHE_TYPE_QUERY) {
			parent.setEL("default-query", name);
		}
		else if (_default == ConfigPro.CACHE_TYPE_RESOURCE) {
			parent.setEL("default-resource", name);
		}
		else if (_default == ConfigPro.CACHE_TYPE_FUNCTION) {
			parent.setEL("default-function", name);
		}
		else if (_default == ConfigPro.CACHE_TYPE_INCLUDE) {
			parent.setEL("default-include", name);
		}
		else if (_default == ConfigPro.CACHE_TYPE_HTTP) {
			parent.setEL("default-http", name);
		}
		else if (_default == ConfigPro.CACHE_TYPE_FILE) {
			parent.setEL("default-file", name);
		}
		else if (_default == ConfigPro.CACHE_TYPE_WEBSERVICE) {
			parent.setEL("default-webservice", name);
		}

		// Update
		// boolean isUpdate=false;
		Array children = ConfigWebUtil.getAsArray("connection", parent);
		for (int i = 1; i <= children.size(); i++) {
			Struct el = Caster.toStruct(children.get(i, null), null);
			if (el == null) continue;

			String n = ConfigWebUtil.getAsString("name", el, "");
			if (n.equalsIgnoreCase(name)) {
				setClass(el, null, "", cd);
				el.setEL("custom", toStringURLStyle(custom));
				el.setEL("read-only", Caster.toString(readOnly));
				el.setEL("storage", Caster.toString(storage));
				return;
			}

		}

		// Insert
		Struct el = new StructImpl(Struct.TYPE_LINKED);
		children.appendEL(el);
		el.setEL("name", name);
		setClass(el, null, "", cd);
		el.setEL("custom", toStringURLStyle(custom));
		el.setEL("read-only", Caster.toString(readOnly));
		el.setEL("storage", Caster.toString(storage));

	}

	private void rem(Struct sct, String key) {
		sct.removeEL(KeyImpl.init(key));
	}

	public void removeCacheDefaultConnection(int type) throws PageException {
		checkWriteAccess();

		boolean hasAccess = ConfigWebUtil.hasAccess(config, SecurityManagerImpl.TYPE_CACHE);
		if (!hasAccess) throw new SecurityException("no access to update cache connections");

		Struct parent = _getRootElement("cache");
		if (type == ConfigPro.CACHE_TYPE_OBJECT) {
			rem(parent, "default-object");
		}
		else if (type == ConfigPro.CACHE_TYPE_TEMPLATE) {
			rem(parent, "default-template");
		}
		else if (type == ConfigPro.CACHE_TYPE_QUERY) {
			rem(parent, "default-query");
		}
		else if (type == ConfigPro.CACHE_TYPE_RESOURCE) {
			rem(parent, "default-resource");
		}
		else if (type == ConfigPro.CACHE_TYPE_FUNCTION) {
			rem(parent, "default-function");
		}
		else if (type == ConfigPro.CACHE_TYPE_INCLUDE) {
			rem(parent, "default-include");
		}
		else if (type == ConfigPro.CACHE_TYPE_HTTP) {
			rem(parent, "default-http");
		}
		else if (type == ConfigPro.CACHE_TYPE_FILE) {
			rem(parent, "default-file");
		}
		else if (type == ConfigPro.CACHE_TYPE_WEBSERVICE) {
			rem(parent, "default-webservice");
		}
	}

	public void updateCacheDefaultConnection(int type, String name) throws PageException {
		checkWriteAccess();
		boolean hasAccess = ConfigWebUtil.hasAccess(config, SecurityManagerImpl.TYPE_CACHE);

		if (!hasAccess) throw new SecurityException("no access to update cache default connections");

		Struct parent = _getRootElement("cache");
		if (type == ConfigPro.CACHE_TYPE_OBJECT) {
			parent.setEL("default-object", name);
		}
		else if (type == ConfigPro.CACHE_TYPE_TEMPLATE) {
			parent.setEL("default-template", name);
		}
		else if (type == ConfigPro.CACHE_TYPE_QUERY) {
			parent.setEL("default-query", name);
		}
		else if (type == ConfigPro.CACHE_TYPE_RESOURCE) {
			parent.setEL("default-resource", name);
		}
		else if (type == ConfigPro.CACHE_TYPE_FUNCTION) {
			parent.setEL("default-function", name);
		}
		else if (type == ConfigPro.CACHE_TYPE_INCLUDE) {
			parent.setEL("default-include", name);
		}
		else if (type == ConfigPro.CACHE_TYPE_HTTP) {
			parent.setEL("default-http", name);
		}
		else if (type == ConfigPro.CACHE_TYPE_FILE) {
			parent.setEL("default-file", name);
		}
		else if (type == ConfigPro.CACHE_TYPE_WEBSERVICE) {
			parent.setEL("default-webservice", name);
		}
	}

	public void removeResourceProvider(String scheme) throws PageException {
		checkWriteAccess();
		SecurityManager sm = config.getSecurityManager();
		short access = sm.getAccess(SecurityManager.TYPE_FILE);
		boolean hasAccess = access == SecurityManager.VALUE_YES;

		if (!hasAccess) throw new SecurityException("no access to remove resource provider");

		_removeResourceProvider(scheme);
	}

	public void _removeResourceProvider(String scheme) throws PageException {

		Array children = ConfigWebUtil.getAsArray("resources", "resource-provider", root);

		// remove
		for (int i = children.size(); i > 0; i--) {
			Struct tmp = Caster.toStruct(children.get(i, null), null);
			if (tmp == null) continue;

			String elScheme = ConfigWebUtil.getAsString("scheme", tmp, "");
			if (elScheme.equalsIgnoreCase(scheme)) {
				children.removeEL(i);
				break;
			}
		}
	}

	public void updateResourceProvider(String scheme, ClassDefinition cd, Struct arguments) throws PageException {
		updateResourceProvider(scheme, cd, toStringCSSStyle(arguments));
	}

	public void _updateResourceProvider(String scheme, ClassDefinition cd, Struct arguments) throws PageException {
		_updateResourceProvider(scheme, cd, toStringCSSStyle(arguments));
	}

	public void updateResourceProvider(String scheme, ClassDefinition cd, String arguments) throws PageException {
		checkWriteAccess();
		SecurityManager sm = config.getSecurityManager();
		short access = sm.getAccess(SecurityManager.TYPE_FILE);
		boolean hasAccess = access == SecurityManager.VALUE_YES;

		if (!hasAccess) throw new SecurityException("no access to update resources");
		_updateResourceProvider(scheme, cd, arguments);
	}

	public void _updateResourceProvider(String scheme, ClassDefinition cd, String arguments) throws PageException {

		// check parameters
		if (StringUtil.isEmpty(scheme)) throw new ExpressionException("scheme can't be an empty value");

		Array children = ConfigWebUtil.getAsArray("resources", "resource-provider", root);

		// Update
		for (int i = 1; i <= children.size(); i++) {
			Struct el = Caster.toStruct(children.get(i, null), null);
			if (el == null) continue;

			String elScheme = ConfigWebUtil.getAsString("scheme", el, null);
			if (elScheme.equalsIgnoreCase(scheme)) {
				setClass(el, null, "", cd);
				el.setEL("scheme", scheme);
				el.setEL("arguments", arguments);
				return;
			}
		}

		// Insert
		Struct el = new StructImpl();
		children.appendEL(el);
		el.setEL("scheme", scheme);
		el.setEL("arguments", arguments);
		setClass(el, null, "", cd);
	}

	public void updateDefaultResourceProvider(ClassDefinition cd, String arguments) throws PageException {
		checkWriteAccess();
		SecurityManager sm = config.getSecurityManager();
		short access = sm.getAccess(SecurityManager.TYPE_FILE);
		boolean hasAccess = access == SecurityManager.VALUE_YES;

		if (!hasAccess) throw new SecurityException("no access to update resources");

		Array children = ConfigWebUtil.getAsArray("resources", "default-resource-provider", root);

		// Update
		for (int i = 1; i <= children.size(); i++) {
			Struct el = Caster.toStruct(children.get(i, null), null);
			if (el == null) continue;

			el.setEL("arguments", arguments);
			return;
		}

		// Insert
		Struct el = new StructImpl(Struct.TYPE_LINKED);
		children.appendEL(el);
		el.setEL("arguments", arguments);
		setClass(el, null, "", cd);
	}

	private int getDatasourceLength(ConfigPro config) {
		Map ds = config.getDataSourcesAsMap();
		Iterator it = ds.keySet().iterator();
		int len = 0;

		while (it.hasNext()) {
			if (!((DataSource) ds.get(it.next())).isReadOnly()) len++;
		}
		return len;
	}

	private static String toStringURLStyle(Struct sct) {
		if (sct == null) return "";
		Iterator<Entry<Key, Object>> it = sct.entryIterator();
		Entry<Key, Object> e;
		StringBuilder rtn = new StringBuilder();
		while (it.hasNext()) {
			e = it.next();
			if (rtn.length() > 0) rtn.append('&');
			rtn.append(URLEncoder.encode(e.getKey().getString()));
			rtn.append('=');
			rtn.append(URLEncoder.encode(Caster.toString(e.getValue(), "")));
		}
		return rtn.toString();
	}

	private static String toStringCSSStyle(Struct sct) {
		// Collection.Key[] keys = sct.keys();
		StringBuilder rtn = new StringBuilder();
		Iterator<Entry<Key, Object>> it = sct.entryIterator();
		Entry<Key, Object> e;

		while (it.hasNext()) {
			e = it.next();
			if (rtn.length() > 0) rtn.append(';');
			rtn.append(encode(e.getKey().getString()));
			rtn.append(':');
			rtn.append(encode(Caster.toString(e.getValue(), "")));
		}
		return rtn.toString();
	}

	private static String encode(String str) {
		try {
			return URLEncodedFormat.invoke(str, "UTF-8", false);
		}
		catch (PageException e) {
			return URLEncoder.encode(str);
		}
	}

	public Query getResourceProviders() throws PageException {
		checkReadAccess();
		// check parameters
		Struct parent = _getRootElement("resources");
		Array elProviders = ConfigWebUtil.getAsArray("resource-provider", parent);
		Array elDefaultProviders = ConfigWebUtil.getAsArray("default-resource-provider", parent);

		ResourceProvider[] providers = config.getResourceProviders();
		ResourceProvider defaultProvider = config.getDefaultResourceProvider();

		Query qry = new QueryImpl(new String[] { "support", "scheme", "caseSensitive", "default", "class", "bundleName", "bundleVersion", "arguments" },
				elProviders.size() + elDefaultProviders.size(), "resourceproviders");
		int row = 1;
		for (int i = 1; i <= elDefaultProviders.size(); i++) {
			Struct tmp = Caster.toStruct(elDefaultProviders.get(i, null), null);
			if (tmp == null) continue;
			getResourceProviders(new ResourceProvider[] { defaultProvider }, qry, tmp, row++, Boolean.TRUE);
		}
		for (int i = 1; i <= elProviders.size(); i++) {
			Struct tmp = Caster.toStruct(elProviders.get(i, null), null);
			if (tmp == null) continue;
			getResourceProviders(providers, qry, tmp, row++, Boolean.FALSE);
		}
		return qry;
	}

	private void getResourceProviders(ResourceProvider[] providers, Query qry, Struct p, int row, Boolean def) throws PageException {
		Array support = new ArrayImpl();
		String cn = ConfigWebUtil.getAsString("class", p, null);
		String name = ConfigWebUtil.getAsString("bundle-name", p, null);
		String version = ConfigWebUtil.getAsString("bundle-version", p, null);
		ClassDefinition cd = new ClassDefinitionImpl(cn, name, version, ThreadLocalPageContext.getConfig().getIdentification());

		qry.setAt("scheme", row, p.get("scheme"));
		qry.setAt("arguments", row, p.get("arguments"));

		qry.setAt("class", row, cd.getClassName());
		qry.setAt("bundleName", row, cd.getName());
		qry.setAt("bundleVersion", row, cd.getVersionAsString());
		for (int i = 0; i < providers.length; i++) {
			if (providers[i].getClass().getName().equals(cd.getClassName())) {
				if (providers[i].isAttributesSupported()) support.append("attributes");
				if (providers[i].isModeSupported()) support.append("mode");
				qry.setAt("support", row, ListUtil.arrayToList(support, ","));
				qry.setAt("scheme", row, providers[i].getScheme());
				qry.setAt("caseSensitive", row, Caster.toBoolean(providers[i].isCaseSensitive()));
				qry.setAt("default", row, def);
				break;
			}
		}
	}

	public void removeJDBCDriver(String className) throws ExpressionException, SecurityException {
		checkWriteAccess();
		// check parameters
		if (StringUtil.isEmpty(className)) throw new ExpressionException("class name for jdbc driver cannot be empty");

		Array children = ConfigWebUtil.getAsArray("jdbc", "driver", root);
		for (int i = children.size(); i > 0; i--) {
			Struct tmp = Caster.toStruct(children.get(i, null), null);
			if (tmp == null) continue;

			String n = ConfigWebUtil.getAsString("class", tmp, null);
			if (n != null && n.equalsIgnoreCase(className)) {
				children.removeEL(i);
			}
		}
	}

	/**
	 * remove a DataSource Connection
	 * 
	 * @param name
	 * @throws ExpressionException
	 * @throws SecurityException
	 */
	public void removeDataSource(String name) throws ExpressionException, SecurityException {
		checkWriteAccess();
		// check parameters
		if (name == null || name.length() == 0) throw new ExpressionException("name for Datasource Connection can be an empty value");

		Array children = ConfigWebUtil.getAsArray("data-sources", "data-source", root);
		for (int i = children.size(); i > 0; i--) {
			Struct tmp = Caster.toStruct(children.get(i, null), null);
			if (tmp == null) continue;

			String n = ConfigWebUtil.getAsString("name", tmp, null);
			if (n != null && n.equalsIgnoreCase(name)) {
				children.removeEL(i);
			}
		}
	}

	public void removeCacheConnection(String name) throws ExpressionException, SecurityException {
		checkWriteAccess();

		boolean hasAccess = ConfigWebUtil.hasAccess(config, SecurityManagerImpl.TYPE_CACHE);
		if (!hasAccess) throw new SecurityException("no access to remove cache connection");

		// check parameters
		if (StringUtil.isEmpty(name)) throw new ExpressionException("name for Cache Connection can not be an empty value");

		Struct parent = _getRootElement("cache");

		// remove default flag
		if (name.equalsIgnoreCase(Caster.toString(parent.get("default-object", null), null))) rem(parent, "default-object");
		if (name.equalsIgnoreCase(Caster.toString(parent.get("default-template", null), null))) rem(parent, "default-template");
		if (name.equalsIgnoreCase(Caster.toString(parent.get("default-query", null), null))) rem(parent, "default-query");
		if (name.equalsIgnoreCase(Caster.toString(parent.get("default-resource", null), null))) rem(parent, "default-resource");

		// remove element
		Array children = ConfigWebUtil.getAsArray("connection", parent);
		for (int i = children.size(); i > 0; i--) {
			Struct tmp = Caster.toStruct(children.get(i, null), null);
			if (tmp == null) continue;

			String n = ConfigWebUtil.getAsString("name", tmp, "");
			if (n != null && n.equalsIgnoreCase(name)) {
				Map<String, CacheConnection> conns = config.getCacheConnections();
				CacheConnection cc = conns.get(n.toLowerCase());
				if (cc != null) {
					CacheUtil.releaseEL(cc);
					// CacheUtil.removeEL( config instanceof ConfigWeb ? (ConfigWeb) config : null, cc );
				}

				children.removeEL(i);
			}
		}
	}

	public boolean cacheConnectionExists(String name) throws ExpressionException, SecurityException {
		checkReadAccess();
		if (!ConfigWebUtil.hasAccess(config, SecurityManagerImpl.TYPE_CACHE)) throw new SecurityException("no access to check cache connection");
		if (name == null || name.isEmpty()) throw new ExpressionException("name for Cache Connection can not be an empty value");

		Array children = ConfigWebUtil.getAsArray("cache", "connection", root);
		for (int i = 1; i <= children.size(); i++) {
			Struct tmp = Caster.toStruct(children.get(i, null), null);
			if (tmp == null) continue;

			String n = ConfigWebUtil.getAsString("name", tmp, null);
			if (n != null && n.equalsIgnoreCase(name)) return true;
		}
		return false;
	}

	public void removeGatewayEntry(String name) throws PageException {
		checkWriteAccess();

		boolean hasAccess = ConfigWebUtil.hasAccess(config, SecurityManagerImpl.TYPE_GATEWAY);
		if (!hasAccess) throw new SecurityException("no access to remove gateway entry");

		_removeGatewayEntry(name);
		_removeAMFEngine();
	}

	protected void _removeGatewayEntry(String name) throws PageException {
		if (StringUtil.isEmpty(name)) throw new ExpressionException("name for Gateway Id can be an empty value");

		Array children = ConfigWebUtil.getAsArray("gateways", "gateway", root);

		// remove element
		for (int i = children.size(); i > 0; i--) {
			Struct tmp = Caster.toStruct(children.get(i, null), null);
			if (tmp == null) continue;

			String n = ConfigWebUtil.getAsString("id", tmp, null);
			if (n != null && n.equalsIgnoreCase(name)) {

				if (config instanceof ConfigWeb) {
					_removeGatewayEntry((ConfigWebPro) config, n);
				}
				else {
					ConfigWeb[] cws = ((ConfigServerImpl) config).getConfigWebs();
					for (ConfigWeb cw: cws) {
						_removeGatewayEntry((ConfigWebPro) cw, name);
					}
				}
				children.removeEL(i);
			}
		}
	}

	private void _removeGatewayEntry(ConfigWebPro cw, String name) {
		GatewayEngineImpl engine = (GatewayEngineImpl) cw.getGatewayEngine();
		Map<String, GatewayEntry> conns = engine.getEntries();
		GatewayEntry ge = conns.get(name);
		if (ge != null) {
			engine.remove(ge);
		}
	}

	public void removeRemoteClient(String url) throws ExpressionException, SecurityException {
		checkWriteAccess();

		// SNSN

		boolean hasAccess = ConfigWebUtil.hasAccess(config, SecurityManagerImpl.TYPE_REMOTE);
		if (!hasAccess) throw new SecurityException("no access to remove remote client settings");

		// check parameters
		if (StringUtil.isEmpty(url)) throw new ExpressionException("url for Remote Client can be an empty value");

		Array children = ConfigWebUtil.getAsArray("remote-clients", "remote-client", root);
		for (int i = children.size(); i > 0; i--) {
			Struct tmp = Caster.toStruct(children.get(i, null), null);
			if (tmp == null) continue;

			String n = ConfigWebUtil.getAsString("url", tmp, null);
			if (n != null && n.equalsIgnoreCase(url)) {
				children.removeEL(i);
			}
		}
	}

	/**
	 * update PSQ State
	 * 
	 * @param psq Preserver Single Quote
	 * @throws SecurityException
	 */
	public void updatePSQ(Boolean psq) throws SecurityException {
		checkWriteAccess();
		boolean hasAccess = ConfigWebUtil.hasAccess(config, SecurityManager.TYPE_DATASOURCE);

		if (!hasAccess) throw new SecurityException("no access to update datsource connections");

		Struct datasources = _getRootElement("data-sources");
		datasources.setEL("psq", Caster.toString(psq, ""));
		if (datasources.containsKey("preserve-single-quote")) rem(datasources, "preserve-single-quote");
	}

	public void updateInspectTemplate(String str) throws SecurityException {
		checkWriteAccess();
		boolean hasAccess = ConfigWebUtil.hasAccess(config, SecurityManager.TYPE_SETTING);

		if (!hasAccess) throw new SecurityException("no access to update");

		Struct datasources = _getRootElement("java");
		datasources.setEL("inspect-template", str);

	}

	public void updateTypeChecking(Boolean typeChecking) throws SecurityException {
		checkWriteAccess();
		boolean hasAccess = ConfigWebUtil.hasAccess(config, SecurityManager.TYPE_SETTING);

		if (!hasAccess) throw new SecurityException("no access to update");

		Struct datasources = _getRootElement("application");
		if (typeChecking == null) rem(datasources, "type-checking");
		else datasources.setEL("type-checking", Caster.toString(typeChecking.booleanValue()));

	}

	public void updateCachedAfterTimeRange(TimeSpan ts) throws SecurityException, ApplicationException {
		checkWriteAccess();
		boolean hasAccess = ConfigWebUtil.hasAccess(config, SecurityManager.TYPE_SETTING);
		if (!hasAccess) throw new SecurityException("no access to update");

		Struct el = _getRootElement("application");
		if (ts == null) rem(el, "cached-after");
		else {
			if (ts.getMillis() < 0) throw new ApplicationException("value cannot be a negative number");
			el.setEL("cached-after", ts.getDay() + "," + ts.getHour() + "," + ts.getMinute() + "," + ts.getSecond());
		}
	}

	/**
	 * sets the scope cascading type
	 * 
	 * @param type (SCOPE_XYZ)
	 * @throws SecurityException
	 */
	public void updateScopeCascadingType(String type) throws SecurityException {
		checkWriteAccess();
		boolean hasAccess = ConfigWebUtil.hasAccess(config, SecurityManager.TYPE_SETTING);

		if (!hasAccess) throw new SecurityException("no access to update scope setting");

		Struct scope = _getRootElement("scope");
		if (type.equalsIgnoreCase("strict")) scope.setEL("cascading", "strict");
		else if (type.equalsIgnoreCase("small")) scope.setEL("cascading", "small");
		else if (type.equalsIgnoreCase("standard")) scope.setEL("cascading", "standard");
		else scope.setEL("cascading", "standard");

	}

	/**
	 * sets the scope cascading type
	 * 
	 * @param type (SCOPE_XYZ)
	 * @throws SecurityException
	 */
	public void updateScopeCascadingType(short type) throws SecurityException {
		checkWriteAccess();
		boolean hasAccess = ConfigWebUtil.hasAccess(config, SecurityManager.TYPE_SETTING);
		if (!hasAccess) throw new SecurityException("no access to update scope setting");

		// lucee.print.ln("********........type:"+type);
		Struct scope = _getRootElement("scope");
		if (type == ConfigWeb.SCOPE_STRICT) scope.setEL("cascading", "strict");
		else if (type == ConfigWeb.SCOPE_SMALL) scope.setEL("cascading", "small");
		else if (type == ConfigWeb.SCOPE_STANDARD) scope.setEL("cascading", "standard");

	}

	/**
	 * sets if allowed implicid query call
	 * 
	 * @param allow
	 * @throws SecurityException
	 */
	public void updateAllowImplicidQueryCall(Boolean allow) throws SecurityException {
		checkWriteAccess();
		boolean hasAccess = ConfigWebUtil.hasAccess(config, SecurityManager.TYPE_SETTING);

		if (!hasAccess) throw new SecurityException("no access to update scope setting");

		Struct scope = _getRootElement("scope");
		scope.setEL("cascade-to-resultset", Caster.toString(allow, ""));

	}

	public void updateMergeFormAndUrl(Boolean merge) throws SecurityException {
		checkWriteAccess();
		boolean hasAccess = ConfigWebUtil.hasAccess(config, SecurityManager.TYPE_SETTING);

		if (!hasAccess) throw new SecurityException("no access to update scope setting");

		Struct scope = _getRootElement("scope");
		scope.setEL("merge-url-form", Caster.toString(merge, ""));

	}

	/**
	 * updates request timeout value
	 * 
	 * @param span
	 * @throws SecurityException
	 * @throws ApplicationException
	 */
	public void updateRequestTimeout(TimeSpan span) throws SecurityException, ApplicationException {
		checkWriteAccess();
		boolean hasAccess = ConfigWebUtil.hasAccess(config, SecurityManager.TYPE_SETTING);

		if (!hasAccess) throw new SecurityException("no access to update scope setting");

		Struct scope = _getRootElement("scope");

		Struct application = _getRootElement("application");
		if (span != null) {
			if (span.getMillis() <= 0) throw new ApplicationException("value must be a positive number");
			application.setEL("requesttimeout", span.getDay() + "," + span.getHour() + "," + span.getMinute() + "," + span.getSecond());
		}
		else rem(application, "requesttimeout");

		// remove deprecated attribute
		if (scope.containsKey("requesttimeout")) rem(scope, "requesttimeout");
	}

	/**
	 * updates session timeout value
	 * 
	 * @param span
	 * @throws SecurityException
	 */
	public void updateSessionTimeout(TimeSpan span) throws SecurityException {
		checkWriteAccess();
		boolean hasAccess = ConfigWebUtil.hasAccess(config, SecurityManager.TYPE_SETTING);

		if (!hasAccess) throw new SecurityException("no access to update scope setting");

		Struct scope = _getRootElement("scope");
		if (span != null) scope.setEL("sessiontimeout", span.getDay() + "," + span.getHour() + "," + span.getMinute() + "," + span.getSecond());
		else rem(scope, "sessiontimeout");
	}

	public void updateClientStorage(String storage) throws SecurityException, ApplicationException {
		updateStorage("client", storage);
	}

	public void updateSessionStorage(String storage) throws SecurityException, ApplicationException {
		updateStorage("session", storage);
	}

	private void updateStorage(String storageName, String storage) throws SecurityException, ApplicationException {
		checkWriteAccess();
		boolean hasAccess = ConfigWebUtil.hasAccess(config, SecurityManager.TYPE_SETTING);

		if (!hasAccess) throw new SecurityException("no access to update scope setting");
		storage = validateStorage(storage);

		Struct scope = _getRootElement("scope");
		if (!StringUtil.isEmpty(storage, true)) scope.setEL(storageName + "storage", storage);
		else rem(scope, storageName + "storage");
	}

	private String validateStorage(String storage) throws ApplicationException {
		storage = storage.trim().toLowerCase();

		// empty
		if (StringUtil.isEmpty(storage, true)) return "";

		// standard storages
		if ("cookie".equals(storage) || "memory".equals(storage) || "file".equals(storage)) return storage;

		// aliases
		if ("ram".equals(storage)) return "memory";
		if ("registry".equals(storage)) return "file";

		// datasource
		DataSource ds = config.getDataSource(storage, null);
		if (ds != null) {
			if (ds.isStorage()) return storage;
			throw new ApplicationException("datasource [" + storage + "] is not enabled to be used as session/client storage");
		}

		// cache
		CacheConnection cc = CacheUtil.getCacheConnection(ThreadLocalPageContext.get(config), storage, null);
		if (cc != null) {
			if (cc.isStorage()) return storage;
			throw new ApplicationException("cache [" + storage + "] is not enabled to be used as session/client storage");
		}

		String sdx = StringUtil.soundex(storage);

		// check if a datasource has a similar name
		DataSource[] sources = config.getDataSources();
		for (int i = 0; i < sources.length; i++) {
			if (StringUtil.soundex(sources[i].getName()).equals(sdx))
				throw new ApplicationException("no matching storage for [" + storage + "] found, did you mean [" + sources[i].getName() + "]");
		}

		// check if a cache has a similar name
		Iterator<String> it = config.getCacheConnections().keySet().iterator();
		String name;
		while (it.hasNext()) {
			name = it.next();
			if (StringUtil.soundex(name).equals(sdx)) throw new ApplicationException("no matching storage for [" + storage + "] found, did you mean [" + name + "]");
		}

		throw new ApplicationException("no matching storage for [" + storage + "] found");
	}

	/**
	 * updates session timeout value
	 * 
	 * @param span
	 * @throws SecurityException
	 */
	public void updateClientTimeout(TimeSpan span) throws SecurityException {
		checkWriteAccess();
		boolean hasAccess = ConfigWebUtil.hasAccess(config, SecurityManager.TYPE_SETTING);

		if (!hasAccess) throw new SecurityException("no access to update scope setting");

		Struct scope = _getRootElement("scope");
		if (span != null) scope.setEL("clienttimeout", span.getDay() + "," + span.getHour() + "," + span.getMinute() + "," + span.getSecond());
		else rem(scope, "clienttimeout");

		// deprecated
		if (scope.containsKey("client-max-age")) rem(scope, "client-max-age");

	}

	public void updateCFMLWriterType(String writerType) throws SecurityException, ApplicationException {
		checkWriteAccess();
		boolean hasAccess = ConfigWebUtil.hasAccess(config, SecurityManager.TYPE_SETTING);
		if (!hasAccess) throw new SecurityException("no access to update scope setting");

		Struct scope = _getRootElement("setting");
		writerType = writerType.trim();

		// remove
		if (StringUtil.isEmpty(writerType)) {
			if (scope.containsKey("cfml-writer")) rem(scope, "cfml-writer");
			return;
		}

		// update
		if (!"white-space".equalsIgnoreCase(writerType) && !"white-space-pref".equalsIgnoreCase(writerType) && !"regular".equalsIgnoreCase(writerType))
			throw new ApplicationException("invalid writer type definition [" + writerType + "], valid types are [white-space, white-space-pref, regular]");

		scope.setEL("cfml-writer", writerType.toLowerCase());
	}

	public void updateSuppressContent(Boolean value) throws SecurityException {
		checkWriteAccess();
		boolean hasAccess = ConfigWebUtil.hasAccess(config, SecurityManager.TYPE_SETTING);

		if (!hasAccess) throw new SecurityException("no access to update scope setting");

		Struct scope = _getRootElement("setting");
		scope.setEL("suppress-content", Caster.toString(value, ""));
	}

	public void updateShowVersion(Boolean value) throws SecurityException {
		checkWriteAccess();
		boolean hasAccess = ConfigWebUtil.hasAccess(config, SecurityManager.TYPE_SETTING);

		if (!hasAccess) throw new SecurityException("no access to update scope setting");

		Struct scope = _getRootElement("setting");
		scope.setEL("show-version", Caster.toString(value, ""));
	}

	public void updateAllowCompression(Boolean value) throws SecurityException {
		checkWriteAccess();
		boolean hasAccess = ConfigWebUtil.hasAccess(config, SecurityManager.TYPE_SETTING);

		if (!hasAccess) throw new SecurityException("no access to update scope setting");

		Struct scope = _getRootElement("setting");
		scope.setEL("allow-compression", Caster.toString(value, ""));
	}

	public void updateContentLength(Boolean value) throws SecurityException {
		checkWriteAccess();
		boolean hasAccess = ConfigWebUtil.hasAccess(config, SecurityManager.TYPE_SETTING);

		if (!hasAccess) throw new SecurityException("no access to update scope setting");

		Struct scope = _getRootElement("setting");
		scope.setEL("content-length", Caster.toString(value, ""));
	}

	public void updateBufferOutput(Boolean value) throws SecurityException {
		checkWriteAccess();
		boolean hasAccess = ConfigWebUtil.hasAccess(config, SecurityManager.TYPE_SETTING);

		if (!hasAccess) throw new SecurityException("no access to update scope setting");

		Struct scope = _getRootElement("setting");
		scope.setEL("buffering-output", Caster.toString(value, ""));
		if (scope.containsKey("buffer-output")) rem(scope, "buffer-output");
	}

	/**
	 * updates request timeout value
	 * 
	 * @param span
	 * @throws SecurityException
	 */
	public void updateApplicationTimeout(TimeSpan span) throws SecurityException {
		checkWriteAccess();
		boolean hasAccess = ConfigWebUtil.hasAccess(config, SecurityManager.TYPE_SETTING);

		if (!hasAccess) throw new SecurityException("no access to update scope setting");

		Struct scope = _getRootElement("scope");
		if (span != null) scope.setEL("applicationtimeout", span.getDay() + "," + span.getHour() + "," + span.getMinute() + "," + span.getSecond());
		else rem(scope, "applicationtimeout");
	}

	public void updateApplicationListener(String type, String mode) throws SecurityException {
		checkWriteAccess();
		boolean hasAccess = ConfigWebUtil.hasAccess(config, SecurityManager.TYPE_SETTING);

		if (!hasAccess) throw new SecurityException("no access to update listener type");

		Struct scope = _getRootElement("application");
		scope.setEL("listener-type", type.toLowerCase().trim());
		scope.setEL("listener-mode", mode.toLowerCase().trim());
	}

	public void updateCachedWithin(int type, Object value) throws SecurityException, ApplicationException {
		checkWriteAccess();
		boolean hasAccess = ConfigWebUtil.hasAccess(config, SecurityManager.TYPE_SETTING);
		if (!hasAccess) throw new SecurityException("no access to update cachedwithin setting");

		String t = AppListenerUtil.toCachedWithinType(type, "");
		if (t == null) throw new ApplicationException("invalid cachedwithin type definition");
		String v = Caster.toString(value, null);
		Struct app = _getRootElement("application");
		if (v != null) app.setEL("cached-within-" + t, v);
		else rem(app, "cached-within-" + t);
	}

	public void updateProxy(boolean enabled, String server, int port, String username, String password) throws SecurityException {
		checkWriteAccess();
		boolean hasAccess = ConfigWebUtil.hasAccess(config, SecurityManager.TYPE_SETTING);

		if (!hasAccess) throw new SecurityException("no access to update listener type");

		Struct proxy = _getRootElement("proxy");
		proxy.setEL("enabled", Caster.toString(enabled));
		if (!StringUtil.isEmpty(server)) proxy.setEL("server", server);
		if (port > 0) proxy.setEL("port", Caster.toString(port));
		if (!StringUtil.isEmpty(username)) proxy.setEL("username", username);
		if (!StringUtil.isEmpty(password)) proxy.setEL("password", password);
	}

	/*
	 * public void removeProxy() throws SecurityException { boolean
	 * hasAccess=ConfigWebUtil.hasAccess(config,SecurityManager.TYPE_SETTING); if(!hasAccess) throw new
	 * SecurityException("no access to remove proxy settings");
	 * 
	 * Element proxy=_getRootElement("proxy"); proxy.removeAttribute("server");
	 * proxy.removeAttribute("port"); proxy.removeAttribute("username");
	 * proxy.removeAttribute("password"); }
	 */

	/**
	 * enable or desable session management
	 * 
	 * @param sessionManagement
	 * @throws SecurityException
	 */
	public void updateSessionManagement(Boolean sessionManagement) throws SecurityException {
		checkWriteAccess();
		boolean hasAccess = ConfigWebUtil.hasAccess(config, SecurityManager.TYPE_SETTING);

		if (!hasAccess) throw new SecurityException("no access to update scope setting");

		Struct scope = _getRootElement("scope");
		scope.setEL("sessionmanagement", Caster.toString(sessionManagement, ""));
	}

	/**
	 * enable or desable client management
	 * 
	 * @param clientManagement
	 * @throws SecurityException
	 */
	public void updateClientManagement(Boolean clientManagement) throws SecurityException {
		checkWriteAccess();
		boolean hasAccess = ConfigWebUtil.hasAccess(config, SecurityManager.TYPE_SETTING);

		if (!hasAccess) throw new SecurityException("no access to update scope setting");

		Struct scope = _getRootElement("scope");
		scope.setEL("clientmanagement", Caster.toString(clientManagement, ""));
	}

	/**
	 * set if client cookies are enabled or not
	 * 
	 * @param clientCookies
	 * @throws SecurityException
	 */
	public void updateClientCookies(Boolean clientCookies) throws SecurityException {
		checkWriteAccess();
		boolean hasAccess = ConfigWebUtil.hasAccess(config, SecurityManager.TYPE_SETTING);
		if (!hasAccess) throw new SecurityException("no access to update scope setting");

		Struct scope = _getRootElement("scope");
		scope.setEL("setclientcookies", Caster.toString(clientCookies, ""));
	}

	/**
	 * set if it's develop mode or not
	 * 
	 * @param developmode
	 * @throws SecurityException
	 */
	public void updateMode(Boolean developmode) throws SecurityException {
		checkWriteAccess();
		boolean hasAccess = ConfigWebUtil.hasAccess(config, SecurityManager.TYPE_SETTING);
		if (!hasAccess) throw new SecurityException("no access to update scope setting");

		Struct mode = _getRootElement("mode");
		mode.setEL("develop", Caster.toString(developmode, ""));
	}

	/**
	 * set if domain cookies are enabled or not
	 * 
	 * @param domainCookies
	 * @throws SecurityException
	 */
	public void updateDomaincookies(Boolean domainCookies) throws SecurityException {
		checkWriteAccess();
		boolean hasAccess = ConfigWebUtil.hasAccess(config, SecurityManager.TYPE_SETTING);
		if (!hasAccess) throw new SecurityException("no access to update scope setting");

		Struct scope = _getRootElement("scope");
		scope.setEL("setdomaincookies", Caster.toString(domainCookies, ""));
	}

	/**
	 * update the locale
	 * 
	 * @param locale
	 * @throws SecurityException
	 */
	public void updateLocale(String locale) throws SecurityException {
		checkWriteAccess();
		boolean hasAccess = ConfigWebUtil.hasAccess(config, SecurityManager.TYPE_SETTING);
		if (!hasAccess) throw new SecurityException("no access to update regional setting");

		Struct scope = _getRootElement("regional");
		scope.setEL("locale", locale.trim());
	}

	public void updateMonitorEnabled(boolean updateMonitorEnabled) throws SecurityException {
		checkWriteAccess();
		_updateMonitorEnabled(updateMonitorEnabled);
	}

	void _updateMonitorEnabled(boolean updateMonitorEnabled) {
		Struct scope = _getRootElement("monitoring");
		scope.setEL("enabled", Caster.toString(updateMonitorEnabled));
	}

	public void updateScriptProtect(String strScriptProtect) throws SecurityException {
		checkWriteAccess();
		boolean hasAccess = ConfigWebUtil.hasAccess(config, SecurityManager.TYPE_SETTING);
		if (!hasAccess) throw new SecurityException("no access to update script protect");

		Struct scope = _getRootElement("application");
		scope.setEL("script-protect", strScriptProtect.trim());
	}

	public void updateAllowURLRequestTimeout(Boolean allowURLRequestTimeout) throws SecurityException {
		checkWriteAccess();
		boolean hasAccess = ConfigWebUtil.hasAccess(config, SecurityManager.TYPE_SETTING);
		if (!hasAccess) throw new SecurityException("no access to update AllowURLRequestTimeout");

		Struct scope = _getRootElement("application");
		scope.setEL("allow-url-requesttimeout", Caster.toString(allowURLRequestTimeout, ""));
	}

	public void updateScriptProtect(int scriptProtect) throws SecurityException {
		updateScriptProtect(AppListenerUtil.translateScriptProtect(scriptProtect));
	}

	/**
	 * update the timeZone
	 * 
	 * @param timeZone
	 * @throws SecurityException
	 */
	public void updateTimeZone(String timeZone) throws SecurityException {
		checkWriteAccess();
		boolean hasAccess = ConfigWebUtil.hasAccess(config, SecurityManager.TYPE_SETTING);
		if (!hasAccess) throw new SecurityException("no access to update regional setting");

		Struct regional = _getRootElement("regional");
		regional.setEL("timezone", timeZone.trim());

	}

	/**
	 * update the timeServer
	 * 
	 * @param timeServer
	 * @param useTimeServer
	 * @throws PageException
	 */
	public void updateTimeServer(String timeServer, Boolean useTimeServer) throws PageException {
		checkWriteAccess();
		if (useTimeServer != null && useTimeServer.booleanValue() && !StringUtil.isEmpty(timeServer, true)) {
			try {
				new NtpClient(timeServer).getOffset();
			}
			catch (IOException e) {
				try {
					new NtpClient(timeServer).getOffset();
				}
				catch (IOException ee) {
					throw Caster.toPageException(ee);
				}
			}
		}

		boolean hasAccess = ConfigWebUtil.hasAccess(config, SecurityManager.TYPE_SETTING);
		if (!hasAccess) throw new SecurityException("no access to update regional setting");

		Struct scope = _getRootElement("regional");
		scope.setEL("timeserver", timeServer.trim());
		if (useTimeServer != null) scope.setEL("use-timeserver", Caster.toString(useTimeServer));
		else rem(scope, "use-timeserver");
	}

	/**
	 * update the baseComponent
	 * 
	 * @param baseComponent
	 * @throws SecurityException
	 */
	public void updateBaseComponent(String baseComponentCFML, String baseComponentLucee) throws SecurityException {
		checkWriteAccess();
		boolean hasAccess = ConfigWebUtil.hasAccess(config, SecurityManager.TYPE_SETTING);
		if (!hasAccess) throw new SecurityException("no access to update component setting");
		// config.resetBaseComponentPage();
		Struct scope = _getRootElement("component");
		// if(baseComponent.trim().length()>0)
		rem(scope, "base");
		scope.setEL("base-cfml", baseComponentCFML);
		scope.setEL("base-lucee", baseComponentLucee);
	}

	public void updateComponentDeepSearch(Boolean deepSearch) throws SecurityException {
		checkWriteAccess();
		boolean hasAccess = ConfigWebUtil.hasAccess(config, SecurityManager.TYPE_SETTING);
		if (!hasAccess) throw new SecurityException("no access to update component setting");
		// config.resetBaseComponentPage();
		Struct scope = _getRootElement("component");
		// if(baseComponent.trim().length()>0)
		if (deepSearch != null) scope.setEL("deep-search", Caster.toString(deepSearch.booleanValue()));

		else {
			if (scope.containsKey("deep-search")) rem(scope, "deep-search");
		}

	}

	public void updateComponentDefaultImport(String componentDefaultImport) throws SecurityException {
		checkWriteAccess();
		boolean hasAccess = ConfigWebUtil.hasAccess(config, SecurityManager.TYPE_SETTING);
		if (!hasAccess) throw new SecurityException("no access to update component setting");
		// config.resetBaseComponentPage();
		Struct scope = _getRootElement("component");
		// if(baseComponent.trim().length()>0)
		scope.setEL("component-default-import", componentDefaultImport);
	}

	/**
	 * update the Component Data Member default access type
	 * 
	 * @param strAccess
	 * @throws SecurityException
	 * @throws ExpressionException
	 */
	public void updateComponentDataMemberDefaultAccess(String strAccess) throws SecurityException, ApplicationException {
		checkWriteAccess();

		boolean hasAccess = ConfigWebUtil.hasAccess(config, SecurityManager.TYPE_SETTING);
		if (!hasAccess) throw new SecurityException("no access to update component setting");

		Struct scope = _getRootElement("component");

		if (StringUtil.isEmpty(strAccess)) {
			scope.setEL("data-member-default-access", "");
		}
		else {
			scope.setEL("data-member-default-access", ComponentUtil.toStringAccess(ComponentUtil.toIntAccess(strAccess)));
		}
	}

	/**
	 * update the Component Data Member default access type
	 * 
	 * @param triggerDataMember
	 * @throws SecurityException
	 */
	public void updateTriggerDataMember(Boolean triggerDataMember) throws SecurityException {
		checkWriteAccess();
		boolean hasAccess = ConfigWebUtil.hasAccess(config, SecurityManager.TYPE_SETTING);
		if (!hasAccess) throw new SecurityException("no access to update trigger-data-member");

		Struct scope = _getRootElement("component");
		scope.setEL("trigger-data-member", Caster.toString(triggerDataMember, ""));
	}

	public void updateComponentUseShadow(Boolean useShadow) throws SecurityException {
		checkWriteAccess();
		boolean hasAccess = ConfigWebUtil.hasAccess(config, SecurityManager.TYPE_SETTING);
		if (!hasAccess) throw new SecurityException("no access to update use-shadow");

		Struct scope = _getRootElement("component");
		scope.setEL("use-shadow", Caster.toString(useShadow, ""));
	}

	public void updateComponentLocalSearch(Boolean componentLocalSearch) throws SecurityException {
		checkWriteAccess();
		boolean hasAccess = ConfigWebUtil.hasAccess(config, SecurityManager.TYPE_SETTING);
		if (!hasAccess) throw new SecurityException("no access to update component Local Search");

		Struct scope = _getRootElement("component");
		scope.setEL("local-search", Caster.toString(componentLocalSearch, ""));
	}

	public void updateComponentPathCache(Boolean componentPathCache) throws SecurityException {
		checkWriteAccess();
		boolean hasAccess = ConfigWebUtil.hasAccess(config, SecurityManager.TYPE_SETTING);
		if (!hasAccess) throw new SecurityException("no access to update component Cache Path");

		Struct scope = _getRootElement("component");
		if (!Caster.toBooleanValue(componentPathCache, false)) config.clearComponentCache();
		scope.setEL("use-cache-path", Caster.toString(componentPathCache, ""));
	}

	public void updateCTPathCache(Boolean ctPathCache) throws SecurityException {
		checkWriteAccess();
		if (!ConfigWebUtil.hasAccess(config, SecurityManager.TYPE_CUSTOM_TAG)) throw new SecurityException("no access to update custom tag setting");

		if (!Caster.toBooleanValue(ctPathCache, false)) config.clearCTCache();
		Struct scope = _getRootElement("custom-tag");
		scope.setEL("use-cache-path", Caster.toString(ctPathCache, ""));
	}

	public void updateSecurity(String varUsage) throws SecurityException {
		checkWriteAccess();
		Struct el = _getRootElement("security");

		if (el != null) {
			if (!StringUtil.isEmpty(varUsage)) el.setEL("variable-usage", Caster.toString(varUsage));
			else rem(el, "variable-usage");
		}

	}

	/**
	 * updates if debugging or not
	 * 
	 * @param debug if value is null server setting is used
	 * @throws SecurityException
	 */
	public void updateDebug(Boolean debug, Boolean template, Boolean database, Boolean exception, Boolean tracing, Boolean dump, Boolean timer, Boolean implicitAccess,
			Boolean queryUsage) throws SecurityException {
		checkWriteAccess();
		boolean hasAccess = ConfigWebUtil.hasAccess(config, SecurityManager.TYPE_DEBUGGING);
		if (!hasAccess) throw new SecurityException("no access to change debugging settings");
		Struct debugging = _getRootElement("debugging");

		if (debug != null) debugging.setEL("debug", Caster.toString(debug.booleanValue()));
		else rem(debugging, "debug");

		if (database != null) debugging.setEL("database", Caster.toString(database.booleanValue()));
		else rem(debugging, "database");

		if (template != null) debugging.setEL("templenabled", Caster.toString(template.booleanValue()));
		else rem(debugging, "templenabled");

		if (exception != null) debugging.setEL("exception", Caster.toString(exception.booleanValue()));
		else rem(debugging, "exception");

		if (tracing != null) debugging.setEL("tracing", Caster.toString(tracing.booleanValue()));
		else rem(debugging, "tracing");

		if (dump != null) debugging.setEL("dump", Caster.toString(dump.booleanValue()));
		else rem(debugging, "dump");

		if (timer != null) debugging.setEL("timer", Caster.toString(timer.booleanValue()));
		else rem(debugging, "timer");

		if (implicitAccess != null) debugging.setEL("implicit-access", Caster.toString(implicitAccess.booleanValue()));
		else rem(debugging, "implicit-access");

		if (queryUsage != null) debugging.setEL("query-usage", Caster.toString(queryUsage.booleanValue()));
		else rem(debugging, "query-usage");

	}

	/**
	 * updates the DebugTemplate
	 * 
	 * @param template
	 * @throws SecurityException
	 */
	public void updateDebugTemplate(String template) throws SecurityException {
		checkWriteAccess();
		boolean hasAccess = ConfigWebUtil.hasAccess(config, SecurityManager.TYPE_SETTING);
		if (!hasAccess) throw new SecurityException("no access to change debugging settings");

		Struct debugging = _getRootElement("debugging");
		// if(template.trim().length()>0)
		debugging.setEL("template", template);
	}

	/**
	 * updates the ErrorTemplate
	 * 
	 * @param template
	 * @throws SecurityException
	 */
	public void updateErrorTemplate(int statusCode, String template) throws SecurityException {
		checkWriteAccess();
		boolean hasAccess = ConfigWebUtil.hasAccess(config, SecurityManager.TYPE_SETTING);
		if (!hasAccess) throw new SecurityException("no access to change error settings");

		Struct error = _getRootElement("error");
		// if(template.trim().length()>0)
		error.setEL("template-" + statusCode, template);
	}

	public void updateErrorStatusCode(Boolean doStatusCode) throws SecurityException {
		checkWriteAccess();
		boolean hasAccess = ConfigWebUtil.hasAccess(config, SecurityManager.TYPE_SETTING);
		if (!hasAccess) throw new SecurityException("no access to change error settings");

		Struct error = _getRootElement("error");
		error.setEL("status-code", Caster.toString(doStatusCode, ""));
	}

	public void updateRegexType(String type) throws PageException {
		checkWriteAccess();
		boolean hasAccess = ConfigWebUtil.hasAccess(config, SecurityManager.TYPE_SETTING);
		if (!hasAccess) throw new SecurityException("no access to change regex settings");

		Struct regex = _getRootElement("regex");
		if (StringUtil.isEmpty(type)) rem(regex, "type");
		else regex.setEL("type", RegexFactory.toType(RegexFactory.toType(type), "perl"));
	}

	/**
	 * updates the DebugTemplate
	 * 
	 * @param template
	 * @throws SecurityException
	 */
	public void updateComponentDumpTemplate(String template) throws SecurityException {
		checkWriteAccess();
		boolean hasAccess = ConfigWebUtil.hasAccess(config, SecurityManager.TYPE_SETTING);
		if (!hasAccess) throw new SecurityException("no access to update component setting");

		Struct component = _getRootElement("component");
		// if(template.trim().length()>0)
		component.setEL("dump-template", template);
	}

	private Struct _getRootElement(String name) {
		return ConfigWebUtil.getAsStruct(name, root);
	}

	/**
	 * @param setting
	 * @param file
	 * @param directJavaAccess
	 * @param mail
	 * @param datasource
	 * @param mapping
	 * @param customTag
	 * @param cfxSetting
	 * @param cfxUsage
	 * @param debugging
	 * @param search
	 * @param scheduledTasks
	 * @param tagExecute
	 * @param tagImport
	 * @param tagObject
	 * @param tagRegistry
	 * @throws SecurityException
	 */
	public void updateDefaultSecurity(short setting, short file, Resource[] fileAccess, short directJavaAccess, short mail, short datasource, short mapping, short remote,
			short customTag, short cfxSetting, short cfxUsage, short debugging, short search, short scheduledTasks, short tagExecute, short tagImport, short tagObject,
			short tagRegistry, short cache, short gateway, short orm, short accessRead, short accessWrite) throws SecurityException {
		checkWriteAccess();
		if (!(config instanceof ConfigServer)) throw new SecurityException("can't change security settings from this context");

		Struct security = _getRootElement("security");
		updateSecurityFileAccess(security, fileAccess, file);
		security.setEL("setting", SecurityManagerImpl.toStringAccessValue(setting));
		security.setEL("file", SecurityManagerImpl.toStringAccessValue(file));
		security.setEL("direct_java_access", SecurityManagerImpl.toStringAccessValue(directJavaAccess));
		security.setEL("mail", SecurityManagerImpl.toStringAccessValue(mail));
		security.setEL("datasource", SecurityManagerImpl.toStringAccessValue(datasource));
		security.setEL("mapping", SecurityManagerImpl.toStringAccessValue(mapping));
		security.setEL("remote", SecurityManagerImpl.toStringAccessValue(remote));
		security.setEL("custom_tag", SecurityManagerImpl.toStringAccessValue(customTag));
		security.setEL("cfx_setting", SecurityManagerImpl.toStringAccessValue(cfxSetting));
		security.setEL("cfx_usage", SecurityManagerImpl.toStringAccessValue(cfxUsage));
		security.setEL("debugging", SecurityManagerImpl.toStringAccessValue(debugging));
		security.setEL("search", SecurityManagerImpl.toStringAccessValue(search));
		security.setEL("scheduled_task", SecurityManagerImpl.toStringAccessValue(scheduledTasks));

		security.setEL("tag_execute", SecurityManagerImpl.toStringAccessValue(tagExecute));
		security.setEL("tag_import", SecurityManagerImpl.toStringAccessValue(tagImport));
		security.setEL("tag_object", SecurityManagerImpl.toStringAccessValue(tagObject));
		security.setEL("tag_registry", SecurityManagerImpl.toStringAccessValue(tagRegistry));
		security.setEL("cache", SecurityManagerImpl.toStringAccessValue(cache));
		security.setEL("gateway", SecurityManagerImpl.toStringAccessValue(gateway));
		security.setEL("orm", SecurityManagerImpl.toStringAccessValue(orm));

		security.setEL("access_read", SecurityManagerImpl.toStringAccessRWValue(accessRead));
		security.setEL("access_write", SecurityManagerImpl.toStringAccessRWValue(accessWrite));

	}

	private void removeSecurityFileAccess(Struct parent) {
		Array children = ConfigWebUtil.getAsArray("file-access", parent);
		// remove existing
		if (children.size() > 0) {
			for (int i = children.size(); i > 0; i--) {
				children.removeEL(i);
			}
		}
	}

	private void updateSecurityFileAccess(Struct parent, Resource[] fileAccess, short file) {
		removeSecurityFileAccess(parent);

		// insert
		if (!ArrayUtil.isEmpty(fileAccess) && file != SecurityManager.VALUE_ALL) {
			Struct fa;
			Array children = ConfigWebUtil.getAsArray("file-access", parent);
			for (int i = 0; i < fileAccess.length; i++) {
				fa = new StructImpl();
				fa.setEL("path", fileAccess[i].getAbsolutePath());
				children.appendEL(fa);
			}
		}

	}

	/**
	 * update a security manager that match the given id
	 * 
	 * @param id
	 * @param setting
	 * @param file
	 * @param fileAccess
	 * @param directJavaAccess
	 * @param mail
	 * @param datasource
	 * @param mapping
	 * @param customTag
	 * @param cfxSetting
	 * @param cfxUsage
	 * @param debugging
	 * @param search
	 * @param scheduledTasks
	 * @param tagExecute
	 * @param tagImport
	 * @param tagObject
	 * @param tagRegistry
	 * @throws SecurityException
	 * @throws ApplicationException
	 */
	public void updateSecurity(String id, short setting, short file, Resource[] fileAccess, short directJavaAccess, short mail, short datasource, short mapping, short remote,
			short customTag, short cfxSetting, short cfxUsage, short debugging, short search, short scheduledTasks, short tagExecute, short tagImport, short tagObject,
			short tagRegistry, short cache, short gateway, short orm, short accessRead, short accessWrite) throws SecurityException, ApplicationException {
		checkWriteAccess();
		if (!(config instanceof ConfigServer)) throw new SecurityException("can't change security settings from this context");

		Struct security = _getRootElement("security");
		Array children = ConfigWebUtil.getAsArray("accessor", security);
		Struct accessor = null;
		for (int i = 1; i <= children.size(); i++) {
			Struct tmp = Caster.toStruct(children.get(i, null), null);
			if (tmp == null) continue;

			if (id.equals(tmp.get("id", ""))) {
				accessor = tmp;
			}
		}
		if (accessor == null) throw new ApplicationException("there is noc Security Manager for id [" + id + "]");
		updateSecurityFileAccess(accessor, fileAccess, file);

		accessor.setEL("setting", SecurityManagerImpl.toStringAccessValue(setting));
		accessor.setEL("file", SecurityManagerImpl.toStringAccessValue(file));
		accessor.setEL("direct_java_access", SecurityManagerImpl.toStringAccessValue(directJavaAccess));
		accessor.setEL("mail", SecurityManagerImpl.toStringAccessValue(mail));
		accessor.setEL("datasource", SecurityManagerImpl.toStringAccessValue(datasource));
		accessor.setEL("mapping", SecurityManagerImpl.toStringAccessValue(mapping));
		accessor.setEL("remote", SecurityManagerImpl.toStringAccessValue(remote));
		accessor.setEL("custom_tag", SecurityManagerImpl.toStringAccessValue(customTag));
		accessor.setEL("cfx_setting", SecurityManagerImpl.toStringAccessValue(cfxSetting));
		accessor.setEL("cfx_usage", SecurityManagerImpl.toStringAccessValue(cfxUsage));
		accessor.setEL("debugging", SecurityManagerImpl.toStringAccessValue(debugging));
		accessor.setEL("search", SecurityManagerImpl.toStringAccessValue(search));
		accessor.setEL("scheduled_task", SecurityManagerImpl.toStringAccessValue(scheduledTasks));
		accessor.setEL("cache", SecurityManagerImpl.toStringAccessValue(cache));
		accessor.setEL("gateway", SecurityManagerImpl.toStringAccessValue(gateway));
		accessor.setEL("orm", SecurityManagerImpl.toStringAccessValue(orm));

		accessor.setEL("tag_execute", SecurityManagerImpl.toStringAccessValue(tagExecute));
		accessor.setEL("tag_import", SecurityManagerImpl.toStringAccessValue(tagImport));
		accessor.setEL("tag_object", SecurityManagerImpl.toStringAccessValue(tagObject));
		accessor.setEL("tag_registry", SecurityManagerImpl.toStringAccessValue(tagRegistry));

		accessor.setEL("access_read", SecurityManagerImpl.toStringAccessRWValue(accessRead));
		accessor.setEL("access_write", SecurityManagerImpl.toStringAccessRWValue(accessWrite));
	}

	/**
	 * @return returns the default password
	 * @throws SecurityException
	 */
	public Password getDefaultPassword() throws SecurityException {
		checkReadAccess();
		if (config instanceof ConfigServerImpl) {
			return ((ConfigServerImpl) config).getDefaultPassword();
		}
		throw new SecurityException("can't access default password within this context");
	}

	/**
	 * @param password
	 * @throws SecurityException
	 * @throws IOException
	 * @throws DOMException
	 */
	public void updateDefaultPassword(String password) throws SecurityException, DOMException, IOException {
		checkWriteAccess();
		((ConfigServerImpl) config).setDefaultPassword(PasswordImpl.writeToStruct(root, password, true));
	}

	public void removeDefaultPassword() throws SecurityException {
		checkWriteAccess();
		PasswordImpl.removeFromStruct(root, true);
		((ConfigServerImpl) config).setDefaultPassword(null);
	}

	/**
	 * session type update
	 * 
	 * @param type
	 * @throws SecurityException
	 */
	public void updateSessionType(String type) throws SecurityException {
		checkWriteAccess();
		boolean hasAccess = ConfigWebUtil.hasAccess(config, SecurityManager.TYPE_SETTING);
		if (!hasAccess) throw new SecurityException("no access to update scope setting");

		type = type.toLowerCase().trim();

		Struct scope = _getRootElement("scope");
		scope.setEL("session-type", type);
	}

	public void updateLocalMode(String mode) throws SecurityException {
		checkWriteAccess();
		boolean hasAccess = ConfigWebUtil.hasAccess(config, SecurityManager.TYPE_SETTING);
		if (!hasAccess) throw new SecurityException("no access to update scope setting");

		mode = mode.toLowerCase().trim();
		Struct scope = _getRootElement("scope");
		scope.setEL("local-mode", mode);
	}

	public void updateRestList(Boolean list) throws SecurityException {
		checkWriteAccess();
		boolean hasAccess = true;// TODO ConfigWebUtil.hasAccess(config,SecurityManager.TYPE_REST);
		if (!hasAccess) throw new SecurityException("no access to update rest setting");

		Struct rest = _getRootElement("rest");
		if (list == null) {
			if (rest.containsKey("list")) rem(rest, "list");
		}
		else rest.setEL("list", Caster.toString(list.booleanValue()));
	}

	/**
	 * updates update settingd for Lucee
	 * 
	 * @param type
	 * @param location
	 * @throws SecurityException
	 */
	public void updateUpdate(String type, String location) throws SecurityException {
		checkWriteAccess();

		if (!(config instanceof ConfigServer)) {
			throw new SecurityException("can't change update setting from this context, access is denied");
		}
		Struct update = _getRootElement("update");
		update.setEL("type", type);
		try {
			location = HTTPUtil.toURL(location, HTTPUtil.ENCODED_AUTO).toString();
		}
		catch (Throwable e) {
			ExceptionUtil.rethrowIfNecessary(e);
		}
		update.setEL("location", location);
	}

	/**
	 * creates an individual security manager based on the default security manager
	 * 
	 * @param id
	 * @throws DOMException
	 * @throws PageException
	 */
	public void createSecurityManager(Password password, String id) throws DOMException, PageException {
		checkWriteAccess();
		ConfigServerImpl cs = (ConfigServerImpl) ConfigWebUtil.getConfigServer(config, password);
		SecurityManagerImpl dsm = (SecurityManagerImpl) cs.getDefaultSecurityManager().cloneSecurityManager();
		cs.setSecurityManager(id, dsm);

		Struct security = _getRootElement("security");
		Struct accessor = null;

		Array children = ConfigWebUtil.getAsArray("accessor", security);
		for (int i = 1; i <= children.size(); i++) {
			Struct tmp = Caster.toStruct(children.get(i, null), null);
			if (tmp == null) continue;

			if (id.equals(tmp.get("id"))) {
				accessor = tmp;
			}
		}
		if (accessor == null) {
			accessor = new StructImpl(Struct.TYPE_LINKED);
			children.appendEL(accessor);
		}

		updateSecurityFileAccess(accessor, dsm.getCustomFileAccess(), dsm.getAccess(SecurityManager.TYPE_FILE));

		accessor.setEL("id", id);
		accessor.setEL("setting", SecurityManagerImpl.toStringAccessValue(dsm.getAccess(SecurityManager.TYPE_SETTING)));
		accessor.setEL("file", SecurityManagerImpl.toStringAccessValue(dsm.getAccess(SecurityManager.TYPE_FILE)));
		accessor.setEL("direct_java_access", SecurityManagerImpl.toStringAccessValue(dsm.getAccess(SecurityManager.TYPE_DIRECT_JAVA_ACCESS)));
		accessor.setEL("mail", SecurityManagerImpl.toStringAccessValue(dsm.getAccess(SecurityManager.TYPE_MAIL)));
		accessor.setEL("datasource", SecurityManagerImpl.toStringAccessValue(dsm.getAccess(SecurityManager.TYPE_DATASOURCE)));
		accessor.setEL("mapping", SecurityManagerImpl.toStringAccessValue(dsm.getAccess(SecurityManager.TYPE_MAPPING)));
		accessor.setEL("custom_tag", SecurityManagerImpl.toStringAccessValue(dsm.getAccess(SecurityManager.TYPE_CUSTOM_TAG)));
		accessor.setEL("cfx_setting", SecurityManagerImpl.toStringAccessValue(dsm.getAccess(SecurityManager.TYPE_CFX_SETTING)));
		accessor.setEL("cfx_usage", SecurityManagerImpl.toStringAccessValue(dsm.getAccess(SecurityManager.TYPE_CFX_USAGE)));
		accessor.setEL("debugging", SecurityManagerImpl.toStringAccessValue(dsm.getAccess(SecurityManager.TYPE_DEBUGGING)));
		accessor.setEL("cache", SecurityManagerImpl.toStringAccessValue(dsm.getAccess(SecurityManagerImpl.TYPE_CACHE)));
		accessor.setEL("gateway", SecurityManagerImpl.toStringAccessValue(dsm.getAccess(SecurityManagerImpl.TYPE_GATEWAY)));
		accessor.setEL("orm", SecurityManagerImpl.toStringAccessValue(dsm.getAccess(SecurityManagerImpl.TYPE_ORM)));

		accessor.setEL("tag_execute", SecurityManagerImpl.toStringAccessValue(dsm.getAccess(SecurityManager.TYPE_TAG_EXECUTE)));
		accessor.setEL("tag_import", SecurityManagerImpl.toStringAccessValue(dsm.getAccess(SecurityManager.TYPE_TAG_IMPORT)));
		accessor.setEL("tag_object", SecurityManagerImpl.toStringAccessValue(dsm.getAccess(SecurityManager.TYPE_TAG_OBJECT)));
		accessor.setEL("tag_registry", SecurityManagerImpl.toStringAccessValue(dsm.getAccess(SecurityManager.TYPE_TAG_REGISTRY)));

	}

	/**
	 * remove security manager matching given id
	 * 
	 * @param id
	 * @throws PageException
	 */
	public void removeSecurityManager(Password password, String id) throws PageException {
		checkWriteAccess();
		((ConfigServerImpl) ConfigWebUtil.getConfigServer(config, password)).removeSecurityManager(id);

		Array children = ConfigWebUtil.getAsArray("security", "accessor", root);
		for (int i = children.size(); i > 0; i--) {
			Struct tmp = Caster.toStruct(children.get(i, null), null);
			if (tmp == null) continue;

			String n = ConfigWebUtil.getAsString("id", tmp, "");
			if (id.equals(n)) {
				children.removeEL(i);
			}
		}
	}

	/**
	 * run update from cfml engine
	 * 
	 * @throws PageException
	 */
	public void runUpdate(Password password) throws PageException {
		checkWriteAccess();
		ConfigServerImpl cs = (ConfigServerImpl) ConfigWebUtil.getConfigServer(config, password);
		CFMLEngineFactory factory = cs.getCFMLEngine().getCFMLEngineFactory();

		synchronized (factory) {
			try {
				cleanUp(factory);
				factory.update(cs.getPassword(), cs.getIdentification());
			}
			catch (Exception e) {
				throw Caster.toPageException(e);
			}
		}

	}

	/**
	 * run update from cfml engine
	 * 
	 * @throws PageException
	 */
	public void removeLatestUpdate(Password password) throws PageException {
		_removeUpdate(password, true);
	}

	public void removeUpdate(Password password) throws PageException {
		_removeUpdate(password, false);
	}

	private void _removeUpdate(Password password, boolean onlyLatest) throws PageException {
		checkWriteAccess();

		ConfigServerImpl cs = (ConfigServerImpl) ConfigWebUtil.getConfigServer(config, password);

		try {
			CFMLEngineFactory factory = cs.getCFMLEngine().getCFMLEngineFactory();

			if (onlyLatest) {
				factory.removeLatestUpdate(cs.getPassword());
			}
			else factory.removeUpdate(cs.getPassword());

		}
		catch (Exception e) {
			throw Caster.toPageException(e);
		}
	}

	public void changeVersionTo(Version version, Password password, IdentificationWeb id) throws PageException {
		checkWriteAccess();
		ConfigServerImpl cs = (ConfigServerImpl) ConfigWebUtil.getConfigServer(config, password);

		Log logger = cs.getLog("deploy");

		try {
			CFMLEngineFactory factory = cs.getCFMLEngine().getCFMLEngineFactory();
			cleanUp(factory);
			// do we have the core file?
			final File patchDir = factory.getPatchDirectory();
			File localPath = new File(version.toString() + ".lco");

			if (!localPath.isFile()) {
				localPath = null;
				Version v;
				final File[] patches = patchDir.listFiles(new ExtensionFilter(new String[] { ".lco" }));
				for (final File patch: patches) {
					v = CFMLEngineFactory.toVersion(patch.getName(), null);
					// not a valid file get deleted
					if (v == null) {
						patch.delete();
					}
					else {
						if (v.equals(version)) { // match!
							localPath = patch;
						}
						// delete newer files
						else if (OSGiUtil.isNewerThan(v, version)) {
							patch.delete();
						}
					}
				}
			}

			// download patch
			if (localPath == null) {

				downloadCore(factory, version, id);
			}

			logger.log(Log.LEVEL_INFO, "Update-Engine", "Installing Lucee version [" + version + "] (previous version was [" + cs.getEngine().getInfo().getVersion() + "])");

			factory.restart(password);
		}
		catch (Exception e) {
			throw Caster.toPageException(e);
		}
	}

	private void cleanUp(CFMLEngineFactory factory) throws IOException {
		final File patchDir = factory.getPatchDirectory();
		final File[] patches = patchDir.listFiles(new ExtensionFilter(new String[] { ".lco" }));
		for (final File patch: patches) {
			if (!IsZipFile.invoke(patch)) patch.delete();
		}
	}

	private File downloadCore(CFMLEngineFactory factory, Version version, Identification id) throws IOException {
		final URL updateProvider = factory.getUpdateLocation();

		final URL updateUrl = new URL(updateProvider,
				"/rest/update/provider/download/" + version.toString() + (id != null ? id.toQueryString() : "") + (id == null ? "?" : "&") + "allowRedirect=true");
		// log.debug("Admin", "download "+version+" from " + updateUrl);
		// System. out.println(updateUrl);

		// local resource
		final File patchDir = factory.getPatchDirectory();
		final File newLucee = new File(patchDir, version + (".lco"));

		int code;
		HttpURLConnection conn;
		try {
			conn = (HttpURLConnection) updateUrl.openConnection();
			conn.setRequestMethod("GET");
			conn.connect();
			code = conn.getResponseCode();
		}
		catch (final UnknownHostException e) {
			// log.error("Admin", e);
			throw e;
		}

		// the update provider is not providing a download for this
		if (code != 200) {

			int count = 0;
			final int max = 5;
			// the update provider can also provide a different (final) location for this
			while ((code == 301 || code == 302) && (count++ < max)) {
				String location = conn.getHeaderField("Location");
				// just in case we check invalid names
				if (location == null) location = conn.getHeaderField("location");
				if (location == null) location = conn.getHeaderField("LOCATION");
				if (location == null) break;
				// System. out.println("download redirected:" + location); // MUST remove

				conn.disconnect();
				URL url = new URL(location);
				try {
					conn = (HttpURLConnection) url.openConnection();
					conn.setRequestMethod("GET");
					conn.connect();
					code = conn.getResponseCode();
				}
				catch (final UnknownHostException e) {
					// log.error("Admin", e);
					throw e;
				}
			}

			// no download available!
			if (code != 200) {
				final String msg = "Lucee Core download failed (response status:" + code + ") the core for version [" + version.toString() + "] from " + updateUrl
						+ ", please download it manually and copy to [" + patchDir + "]";
				// log.debug("Admin", msg);
				conn.disconnect();
				throw new IOException(msg);
			}
		}

		// copy it to local directory
		if (newLucee.createNewFile()) {
			IOUtil.copy((InputStream) conn.getContent(), new FileOutputStream(newLucee), false, true);
			conn.disconnect();

			// when it is a loader extract the core from it
			File tmp = CFMLEngineFactory.extractCoreIfLoader(newLucee);
			if (tmp != null) {
				// System .out.println("extract core from loader"); // MUST remove
				// log.debug("Admin", "extract core from loader");

				newLucee.delete();
				tmp.renameTo(newLucee);
				tmp.delete();
				// System. out.println("exist?" + newLucee.exists()); // MUST remove

			}
		}
		else {
			conn.disconnect();
			// log.debug("Admin","File for new Version already exists, won't copy new one");
			return null;
		}
		return newLucee;
	}

	private String getCoreExtension() {
		return "lco";
	}

	private boolean isNewerThan(int left, int right) {
		return left > right;
	}

	/*
	 * private Resource getPatchDirectory(CFMLEngine engine) throws IOException { //File
	 * f=engine.getCFMLEngineFactory().getResourceRoot(); Resource res =
	 * ResourcesImpl.getFileResourceProvider().getResource(engine.getCFMLEngineFactory().getResourceRoot
	 * ().getAbsolutePath()); Resource pd = res.getRealResource("patches"); if(!pd.exists())pd.mkdirs();
	 * return pd; }
	 */

	/**
	 * run update from cfml engine
	 * 
	 * @throws PageException
	 */
	public void restart(Password password) throws PageException {
		checkWriteAccess();
		ConfigServerImpl cs = (ConfigServerImpl) ConfigWebUtil.getConfigServer(config, password);
		CFMLEngineFactory factory = cs.getCFMLEngine().getCFMLEngineFactory();

		synchronized (factory) {
			try {
				cleanUp(factory);
				factory.restart(cs.getPassword());
			}
			catch (Exception e) {
				throw Caster.toPageException(e);
			}
		}
	}

	public void restart(ConfigServerImpl cs) throws PageException {
		CFMLEngineFactory factory = cs.getCFMLEngine().getCFMLEngineFactory();

		synchronized (factory) {
			try {
				Method m = factory.getClass().getDeclaredMethod("_restart", new Class[0]);
				if (m == null) throw new ApplicationException("Cannot restart Lucee.");
				m.setAccessible(true);
				m.invoke(factory, new Object[0]);
			}
			catch (Exception e) {
				throw Caster.toPageException(e);
			}
		}
	}

	public void updateWebCharset(String charset) throws PageException {
		checkWriteAccess();

		Struct element = _getRootElement("charset");
		if (StringUtil.isEmpty(charset)) {
			if (config instanceof ConfigWeb) rem(element, "web-charset");
			else element.setEL("web-charset", "UTF-8");
		}
		else {
			charset = checkCharset(charset);
			element.setEL("web-charset", charset);
		}

		Struct el = _getRootElement("regional");
		rem(el, "default-encoding");// remove deprecated attribute

	}

	public void updateResourceCharset(String charset) throws PageException {
		checkWriteAccess();

		Struct element = _getRootElement("charset");
		if (StringUtil.isEmpty(charset)) {
			rem(element, "resource-charset");
		}
		else {
			charset = checkCharset(charset);
			element.setEL("resource-charset", charset);

		}

		// update charset

	}

	public void updateTemplateCharset(String charset) throws PageException {

		checkWriteAccess();

		Struct element = _getRootElement("charset");
		if (StringUtil.isEmpty(charset, true)) {
			rem(element, "template-charset");
		}
		else {
			charset = checkCharset(charset);
			element.setEL("template-charset", charset);
		}
	}

	private String checkCharset(String charset) throws PageException {
		charset = charset.trim();
		if ("system".equalsIgnoreCase(charset)) charset = SystemUtil.getCharset().name();
		else if ("jre".equalsIgnoreCase(charset)) charset = SystemUtil.getCharset().name();
		else if ("os".equalsIgnoreCase(charset)) charset = SystemUtil.getCharset().name();

		// check access
		boolean hasAccess = ConfigWebUtil.hasAccess(config, SecurityManager.TYPE_SETTING);
		if (!hasAccess) {
			throw new SecurityException("Access Denied to update regional setting");
		}

		// check encoding
		try {
			IOUtil.checkEncoding(charset);
		}
		catch (IOException e) {
			throw Caster.toPageException(e);
		}
		return charset;
	}

	private Resource getStoragDir(Config config) {
		Resource storageDir = config.getConfigDir().getRealResource("storage");
		if (!storageDir.exists()) storageDir.mkdirs();
		return storageDir;
	}

	public void storageSet(Config config, String key, Object value) throws ConverterException, IOException, SecurityException {
		checkWriteAccess();
		Resource storageDir = getStoragDir(config);
		Resource storage = storageDir.getRealResource(key + ".wddx");

		WDDXConverter converter = new WDDXConverter(config.getTimeZone(), true, true);
		String wddx = converter.serialize(value);
		IOUtil.write(storage, wddx, "UTF-8", false);
	}

	public Object storageGet(Config config, String key) throws ConverterException, IOException, SecurityException {
		checkReadAccess();
		Resource storageDir = getStoragDir(config);
		Resource storage = storageDir.getRealResource(key + ".wddx");
		if (!storage.exists()) throw new IOException("There is no storage named [" + key + "]");
		WDDXConverter converter = new WDDXConverter(config.getTimeZone(), true, true);
		return converter.deserialize(IOUtil.toString(storage, "UTF-8"), true);
	}

	public void updateCustomTagDeepSearch(boolean customTagDeepSearch) throws SecurityException {
		checkWriteAccess();
		if (!ConfigWebUtil.hasAccess(config, SecurityManager.TYPE_CUSTOM_TAG)) throw new SecurityException("Access Denied to update custom tag setting");

		Struct element = _getRootElement("custom-tag");
		element.setEL("custom-tag-deep-search", Caster.toString(customTagDeepSearch));
	}

	public void resetId() throws PageException {
		checkWriteAccess();
		Resource res = config.getConfigDir().getRealResource("id");
		try {
			if (res.exists()) res.remove(false);
		}
		catch (IOException e) {
			throw Caster.toPageException(e);
		}

	}

	public void updateCustomTagLocalSearch(boolean customTagLocalSearch) throws SecurityException {
		checkWriteAccess();
		if (!ConfigWebUtil.hasAccess(config, SecurityManager.TYPE_CUSTOM_TAG)) throw new SecurityException("Access Denied to update custom tag setting");

		Struct element = _getRootElement("custom-tag");
		element.setEL("custom-tag-local-search", Caster.toString(customTagLocalSearch));
	}

	public void updateCustomTagExtensions(String extensions) throws PageException {
		checkWriteAccess();
		if (!ConfigWebUtil.hasAccess(config, SecurityManager.TYPE_CUSTOM_TAG)) throw new SecurityException("Access Denied to update custom tag setting");

		// check
		Array arr = ListUtil.listToArrayRemoveEmpty(extensions, ',');
		ListUtil.trimItems(arr);
		// throw new ApplicationException("you must define at least one extension");

		// update charset
		Struct element = _getRootElement("custom-tag");
		element.setEL("extensions", ListUtil.arrayToList(arr, ","));
	}

	public void updateRemoteClient(String label, String url, String type, String securityKey, String usage, String adminPassword, String serverUsername, String serverPassword,
			String proxyServer, String proxyUsername, String proxyPassword, String proxyPort) throws PageException {
		checkWriteAccess();

		// SNSN

		boolean hasAccess = ConfigWebUtil.hasAccess(config, SecurityManagerImpl.TYPE_REMOTE);
		if (!hasAccess) throw new SecurityException("Access Denied to update remote client settings");

		Struct clients = _getRootElement("remote-clients");

		if (StringUtil.isEmpty(url)) throw new ExpressionException("[url] cannot be empty");
		if (StringUtil.isEmpty(securityKey)) throw new ExpressionException("[securityKey] cannot be empty");
		if (StringUtil.isEmpty(adminPassword)) throw new ExpressionException("[adminPassword] can not be empty");
		url = url.trim();
		securityKey = securityKey.trim();
		adminPassword = adminPassword.trim();

		Array children = ConfigWebUtil.getAsArray("remote-client", clients);

		// Update
		for (int i = 1; i <= children.size(); i++) {
			Struct el = Caster.toStruct(children.get(i, null), null);
			if (el == null) continue;

			String _url = ConfigWebUtil.getAsString("url", el, "");
			if (_url != null && _url.equalsIgnoreCase(url)) {
				el.setEL("label", label);
				el.setEL("type", type);
				el.setEL("usage", usage);
				el.setEL("server-username", serverUsername);
				el.setEL("proxy-server", proxyServer);
				el.setEL("proxy-username", proxyUsername);
				el.setEL("proxy-port", proxyPort);
				el.setEL("security-key", ConfigWebUtil.encrypt(securityKey));
				el.setEL("admin-password", ConfigWebUtil.encrypt(adminPassword));
				el.setEL("server-password", ConfigWebUtil.encrypt(serverPassword));
				el.setEL("proxy-password", ConfigWebUtil.encrypt(proxyPassword));
				return;
			}
		}

		// Insert
		Struct el = new StructImpl(Struct.TYPE_LINKED);

		el.setEL("label", label);
		el.setEL("url", url);
		el.setEL("type", type);
		el.setEL("usage", usage);
		el.setEL("server-username", serverUsername);
		el.setEL("proxy-server", proxyServer);
		el.setEL("proxy-username", proxyUsername);
		el.setEL("proxy-port", proxyPort);
		el.setEL("security-key", ConfigWebUtil.encrypt(securityKey));
		el.setEL("admin-password", ConfigWebUtil.encrypt(adminPassword));
		el.setEL("server-password", ConfigWebUtil.encrypt(serverPassword));
		el.setEL("proxy-password", ConfigWebUtil.encrypt(proxyPassword));
		children.appendEL(el);
	}

	public void updateMonitor(ClassDefinition cd, String type, String name, boolean logEnabled) throws PageException {
		checkWriteAccess();
		_updateMonitor(cd, type, name, logEnabled);
	}

	void _updateMonitor(ClassDefinition cd, String type, String name, boolean logEnabled) throws PageException {
		stopMonitor(ConfigWebUtil.toMonitorType(type, Monitor.TYPE_INTERVAL), name);

		Array children = ConfigWebUtil.getAsArray("monitoring", "monitor", root);
		Struct monitor = null;
		// Update
		for (int i = 1; i <= children.size(); i++) {
			Struct el = Caster.toStruct(children.get(i, null), null);
			if (el == null) continue;

			String _name = ConfigWebUtil.getAsString("name", el, null);
			if (_name != null && _name.equalsIgnoreCase(name)) {
				monitor = el;
				break;
			}
		}

		// Insert
		if (monitor == null) {
			monitor = new StructImpl(Struct.TYPE_LINKED);
			children.appendEL(monitor);
		}
		setClass(monitor, null, "", cd);
		monitor.setEL("type", type);
		monitor.setEL("name", name);
		monitor.setEL("log", Caster.toString(logEnabled));
	}

	private void stopMonitor(int type, String name) {
		Monitor monitor = null;
		try {
			if (Monitor.TYPE_ACTION == type) monitor = config.getActionMonitor(name);
			else if (Monitor.TYPE_REQUEST == type) monitor = config.getRequestMonitor(name);
			else if (Monitor.TYPE_REQUEST == type) monitor = config.getIntervallMonitor(name);
		}
		catch (Throwable t) {
			ExceptionUtil.rethrowIfNecessary(t);
		}
		IOUtil.closeEL(monitor);
	}

	static void removeCacheHandler(ConfigPro config, String id, boolean reload) throws IOException, SAXException, PageException, BundleException, ConverterException {
		ConfigAdmin admin = new ConfigAdmin(config, null);
		admin._removeCacheHandler(id);
		admin._store();
		if (reload) admin._reload();
	}

	private void _removeCache(ClassDefinition cd) {
		Array children = ConfigWebUtil.getAsArray("caches", "cache", root);
		for (int i = children.size(); i > 0; i--) {
			Struct el = Caster.toStruct(children.get(i, null), null);
			if (el == null) continue;

			String _class = ConfigWebUtil.getAsString("virtual", el, null);
			if (_class != null && _class.equalsIgnoreCase(cd.getClassName())) {
				children.removeEL(i);
				break;
			}
		}
	}

	private void _removeCacheHandler(String id) {
		Array children = ConfigWebUtil.getAsArray("cache-handlers", "cache-handler", root);
		for (int i = children.size(); i > 0; i--) {
			Struct el = Caster.toStruct(children.get(i, null), null);
			if (el == null) continue;

			String _id = ConfigWebUtil.getAsString("id", el, null);
			if (_id != null && _id.equalsIgnoreCase(id)) {
				children.removeEL(i);
				break;
			}
		}
	}

	public void updateCacheHandler(String id, ClassDefinition cd) throws PageException {
		checkWriteAccess();
		_updateCacheHandler(id, cd);
	}

	private void _updateCache(ClassDefinition cd) throws PageException {
		Array children = ConfigWebUtil.getAsArray("caches", "cache", root);
		Struct ch = null;
		// Update
		for (int i = 1; i <= children.size(); i++) {
			Struct el = Caster.toStruct(children.get(i, null), null);
			if (el == null) continue;

			String _class = ConfigWebUtil.getAsString("class", el, null);
			if (_class != null && _class.equalsIgnoreCase(cd.getClassName())) {
				ch = el;
				break;
			}
		}

		// Insert
		if (ch == null) {
			ch = new StructImpl(Struct.TYPE_LINKED);
			children.appendEL(ch);
		}
		setClass(ch, null, "", cd);
	}

	private void _updateCacheHandler(String id, ClassDefinition cd) throws PageException {
		Array children = ConfigWebUtil.getAsArray("cache-handlers", "cache-handler", root);
		Struct ch = null;
		// Update
		for (int i = 1; i <= children.size(); i++) {
			Struct el = Caster.toStruct(children.get(i, null), null);
			if (el == null) continue;

			String _id = ConfigWebUtil.getAsString("id", el, null);
			if (_id != null && _id.equalsIgnoreCase(id)) {
				ch = el;
				break;
			}
		}

		// Insert
		if (ch == null) {
			ch = new StructImpl(Struct.TYPE_LINKED);
			children.appendEL(ch);
		}
		ch.setEL("id", id);
		setClass(ch, null, "", cd);
	}

	public void updateExecutionLog(ClassDefinition cd, Struct args, boolean enabled) throws PageException {
		Struct el = _getRootElement("execution-log");
		setClass(el, null, "", cd);
		el.setEL("arguments", toStringCSSStyle(args));
		el.setEL("enabled", Caster.toString(enabled));
	}

	public void removeMonitor(String type, String name) throws SecurityException {
		checkWriteAccess();
		_removeMonitor(type, name);
	}

	void _removeMonitor(String type, String name) {

		stopMonitor(ConfigWebUtil.toMonitorType(type, Monitor.TYPE_INTERVAL), name);

		Array children = ConfigWebUtil.getAsArray("monitoring", "monitor", root);
		// Update
		for (int i = children.size(); i > 0; i--) {
			Struct tmp = Caster.toStruct(children.get(i, null), null);
			if (tmp == null) continue;

			String _name = ConfigWebUtil.getAsString("name", tmp, null);
			if (_name != null && _name.equalsIgnoreCase(name)) {
				children.removeEL(i);
			}
		}
	}

	public void removeCacheHandler(String id) throws PageException {
		checkWriteAccess();

		Array children = ConfigWebUtil.getAsArray("cache-handlers", "cache-handler", root);
		// Update
		for (int i = children.size(); i > 0; i--) {
			Struct tmp = Caster.toStruct(children.get(i, null), null);
			if (tmp == null) continue;

			String _id = ConfigWebUtil.getAsString("id", tmp, null);
			if (_id != null && _id.equalsIgnoreCase(id)) {
				children.removeEL(i);
			}
		}

	}

	public void updateExtensionInfo(boolean enabled) {
		Struct extensions = _getRootElement("extensions");
		extensions.setEL("enabled", Caster.toString(enabled));
	}

	public void updateRHExtensionProvider(String strUrl) throws MalformedURLException, PageException {
		Array children = ConfigWebUtil.getAsArray("extensions", "rhprovider", root);
		strUrl = strUrl.trim();

		URL _url = HTTPUtil.toURL(strUrl, HTTPUtil.ENCODED_NO);
		strUrl = _url.toExternalForm();

		// Update
		Struct el;
		String url;
		for (int i = 1; i <= children.size(); i++) {
			Struct tmp = Caster.toStruct(children.get(i, null), null);
			if (tmp == null) continue;

			url = ConfigWebUtil.getAsString("url", tmp, null);
			if (url != null && url.trim().equalsIgnoreCase(strUrl)) {
				// el.setEL("cache-timeout",Caster.toString(cacheTimeout));
				return;
			}
		}

		// Insert
		el = new StructImpl(Struct.TYPE_LINKED);
		el.setEL("url", strUrl);
		children.prepend(el);
	}

	public void updateExtensionProvider(String strUrl) throws PageException {
		Array children = ConfigWebUtil.getAsArray("extensions", "provider", root);

		strUrl = strUrl.trim();

		// Update
		Struct el;
		String url;
		for (int i = 1; i <= children.size(); i++) {
			Struct tmp = Caster.toStruct(children.get(i, null), null);
			if (tmp == null) continue;

			url = ConfigWebUtil.getAsString("url", tmp, null);
			if (url != null && url.trim().equalsIgnoreCase(strUrl)) {
				return;
			}
		}

		// Insert
		el = new StructImpl(Struct.TYPE_LINKED);
		el.setEL("url", strUrl);
		children.prepend(el);
	}

	public void removeExtensionProvider(String strUrl) {
		Array children = ConfigWebUtil.getAsArray("extensions", "provider", root);
		strUrl = strUrl.trim();
		Struct child;
		String url;
		for (int i = children.size(); i > 0; i--) {
			Struct tmp = Caster.toStruct(children.get(i, null), null);
			if (tmp == null) continue;

			url = ConfigWebUtil.getAsString("url", tmp, null);
			if (url != null && url.trim().equalsIgnoreCase(strUrl)) {
				children.removeEL(i);
				return;
			}
		}
	}

	public void removeRHExtensionProvider(String strUrl) {
		Array children = ConfigWebUtil.getAsArray("extensions", "rhprovider", root);
		strUrl = strUrl.trim();
		Struct child;
		String url;
		for (int i = children.size(); i > 0; i--) {
			Struct tmp = Caster.toStruct(children.get(i, null), null);
			if (tmp == null) continue;

			url = ConfigWebUtil.getAsString("url", tmp, null);
			if (url != null && url.trim().equalsIgnoreCase(strUrl)) {
				children.removeEL(i);
				return;
			}
		}
	}

	public void updateExtension(PageContext pc, Extension extension) throws PageException {
		checkWriteAccess();

		String uid = createUid(pc, extension.getProvider(), extension.getId());

		Array children = ConfigWebUtil.getAsArray("extensions", "extension", root);

		// Update
		Struct el;
		String provider, id;
		for (int i = 1; i <= children.size(); i++) {
			el = Caster.toStruct(children.get(i, null), null);
			if (el == null) continue;

			provider = ConfigWebUtil.getAsString("provider", el, null);
			id = ConfigWebUtil.getAsString("id", el, null);
			if (uid.equalsIgnoreCase(createUid(pc, provider, id))) {
				setExtensionAttrs(el, extension);
				return;
			}
		}

		// Insert
		el = new StructImpl(Struct.TYPE_LINKED);
		el.setEL("provider", extension.getProvider());
		el.setEL("id", extension.getId());
		setExtensionAttrs(el, extension);
		children.appendEL(el);
	}

	private String createUid(PageContext pc, String provider, String id) throws PageException {
		if (Decision.isUUId(id)) {
			return Hash.invoke(pc.getConfig(), id, null, null, 1);
		}
		return Hash.invoke(pc.getConfig(), provider + id, null, null, 1);
	}

	private void setExtensionAttrs(Struct el, Extension extension) {
		el.setEL("version", extension.getVersion());

		el.setEL("config", extension.getStrConfig());
		// el.setEL("config",new ScriptConverter().serialize(extension.getConfig()));

		el.setEL("category", extension.getCategory());
		el.setEL("description", extension.getDescription());
		el.setEL("image", extension.getImage());
		el.setEL("label", extension.getLabel());
		el.setEL("name", extension.getName());

		el.setEL("author", extension.getAuthor());
		el.setEL("type", extension.getType());
		el.setEL("codename", extension.getCodename());
		el.setEL("video", extension.getVideo());
		el.setEL("support", extension.getSupport());
		el.setEL("documentation", extension.getDocumentation());
		el.setEL("forum", extension.getForum());
		el.setEL("mailinglist", extension.getMailinglist());
		el.setEL("network", extension.getNetwork());
		el.setEL("created", Caster.toString(extension.getCreated(), null));

	}

	public void resetORMSetting() throws SecurityException {
		boolean hasAccess = ConfigWebUtil.hasAccess(config, SecurityManagerImpl.TYPE_ORM);

		if (!hasAccess) throw new SecurityException("Access Denied to update ORM Settings");

		Struct orm = _getRootElement("orm");
		if (root.containsKey("orm")) rem(root, "orm");
	}

	public void updateORMSetting(ORMConfiguration oc) throws SecurityException {
		boolean hasAccess = ConfigWebUtil.hasAccess(config, SecurityManagerImpl.TYPE_ORM);

		if (!hasAccess) throw new SecurityException("Access Denied to update ORM Settings");

		Struct orm = _getRootElement("orm");
		orm.setEL("autogenmap", Caster.toString(oc.autogenmap(), "true"));
		orm.setEL("event-handler", Caster.toString(oc.eventHandler(), ""));
		orm.setEL("event-handling", Caster.toString(oc.eventHandling(), "false"));
		orm.setEL("naming-strategy", Caster.toString(oc.namingStrategy(), ""));
		orm.setEL("flush-at-request-end", Caster.toString(oc.flushAtRequestEnd(), "true"));
		orm.setEL("cache-provider", Caster.toString(oc.getCacheProvider(), ""));
		orm.setEL("cache-config", Caster.toString(oc.getCacheConfig(), "true"));
		orm.setEL("catalog", Caster.toString(oc.getCatalog(), ""));
		orm.setEL("db-create", ORMConfigurationImpl.dbCreateAsString(oc.getDbCreate()));
		orm.setEL("dialect", Caster.toString(oc.getDialect(), ""));
		orm.setEL("schema", Caster.toString(oc.getSchema(), ""));
		orm.setEL("log-sql", Caster.toString(oc.logSQL(), "false"));
		orm.setEL("save-mapping", Caster.toString(oc.saveMapping(), "false"));
		orm.setEL("secondary-cache-enable", Caster.toString(oc.secondaryCacheEnabled(), "false"));
		orm.setEL("use-db-for-mapping", Caster.toString(oc.useDBForMapping(), "true"));
		orm.setEL("orm-config", Caster.toString(oc.getOrmConfig(), ""));
		orm.setEL("sql-script", Caster.toString(oc.getSqlScript(), "true"));

		if (oc.isDefaultCfcLocation()) {
			rem(orm, "cfc-location");
		}
		else {
			Resource[] locations = oc.getCfcLocations();
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < locations.length; i++) {
				if (i != 0) sb.append(",");
				sb.append(locations[i].getAbsolutePath());
			}
			orm.setEL("cfc-location", sb.toString());
		}

		orm.setEL("sql-script", Caster.toString(oc.getSqlScript(), "true"));

	}

	public void removeRHExtension(String id) throws PageException {
		checkWriteAccess();
		if (StringUtil.isEmpty(id, true)) return;

		Array children = ConfigWebUtil.getAsArray("extensions", "rhextension", root);
		Struct child;
		RHExtension rhe;
		for (int i = children.size(); i > 0; i--) {
			child = Caster.toStruct(children.get(i, null), null);
			if (child == null) continue;

			try {
				rhe = new RHExtension(config, child);

				// ed=ExtensionDefintion.getInstance(config,child);
			}
			catch (Throwable t) {
				ExceptionUtil.rethrowIfNecessary(t);
				continue;
			}

			if (id.equalsIgnoreCase(rhe.getId()) || id.equalsIgnoreCase(rhe.getSymbolicName())) {
				removeRHExtension(config, rhe, null, true);
				children.removeEL(i);
				// bundles=RHExtension.toBundleDefinitions(child.get("bundles"));
			}
		}
	}

	public static void updateArchive(ConfigPro config, Resource arc, boolean reload) throws PageException {
		try {
			ConfigAdmin admin = new ConfigAdmin(config, null);
			admin.updateArchive(config, arc);
			admin._store();
			if (reload) admin._reload();
		}
		catch (Throwable t) {
			ExceptionUtil.rethrowIfNecessary(t);
			throw Caster.toPageException(t);
		}
	}

	public static void updateCore(ConfigServerImpl config, Resource core, boolean reload) throws PageException {
		try {
			// get patches directory
			CFMLEngine engine = ConfigWebUtil.getEngine(config);
			ConfigServerImpl cs = config;
			Version v;
			v = CFMLEngineFactory.toVersion(core.getName(), null);
			Log logger = cs.getLog("deploy");
			File f = engine.getCFMLEngineFactory().getResourceRoot();
			Resource res = ResourcesImpl.getFileResourceProvider().getResource(f.getAbsolutePath());
			Resource pd = res.getRealResource("patches");
			if (!pd.exists()) pd.mkdirs();
			Resource pf = pd.getRealResource(core.getName());

			// move to patches directory
			core.moveTo(pf);
			core = pf;
			logger.log(Log.LEVEL_INFO, "Update-Engine", "Installing Lucee [" + v + "] (previous version was [" + cs.getEngine().getInfo().getVersion() + "] )");
			//
			ConfigAdmin admin = new ConfigAdmin(config, null);
			admin.restart(config);
		}
		catch (Throwable t) {
			ExceptionUtil.rethrowIfNecessary(t);
			DeployHandler.moveToFailedFolder(config.getDeployDirectory(), core);
			throw Caster.toPageException(t);
		}
	}

	public void updateArchive(Config config, Resource archive) throws PageException {
		Log logger = config.getLog("deploy");
		String type = null, virtual = null, name = null;
		boolean readOnly, topLevel, hidden, physicalFirst;
		short inspect;
		int listMode, listType;
		InputStream is = null;
		ZipFile file = null;
		try {
			file = new ZipFile(FileWrapper.toFile(archive));
			ZipEntry entry = file.getEntry("META-INF/MANIFEST.MF");

			// no manifest
			if (entry == null) {
				DeployHandler.moveToFailedFolder(config.getDeployDirectory(), archive);
				throw new ApplicationException("Cannot deploy " + Constants.NAME + " Archive [" + archive + "], file is to old, the file does not have a MANIFEST.");
			}

			is = file.getInputStream(entry);
			Manifest manifest = new Manifest(is);
			Attributes attr = manifest.getMainAttributes();

			// id = unwrap(attr.getValue("mapping-id"));
			type = StringUtil.unwrap(attr.getValue("mapping-type"));
			virtual = StringUtil.unwrap(attr.getValue("mapping-virtual-path"));
			name = ListUtil.trim(virtual, "/");
			readOnly = Caster.toBooleanValue(StringUtil.unwrap(attr.getValue("mapping-readonly")), false);
			topLevel = Caster.toBooleanValue(StringUtil.unwrap(attr.getValue("mapping-top-level")), false);

			listMode = ConfigWebUtil.toListenerMode(StringUtil.unwrap(attr.getValue("mapping-listener-mode")), -1);
			listType = ConfigWebUtil.toListenerType(StringUtil.unwrap(attr.getValue("mapping-listener-type")), -1);

			inspect = ConfigWebUtil.inspectTemplate(StringUtil.unwrap(attr.getValue("mapping-inspect")), Config.INSPECT_UNDEFINED);
			if (inspect == Config.INSPECT_UNDEFINED) {
				Boolean trusted = Caster.toBoolean(StringUtil.unwrap(attr.getValue("mapping-trusted")), null);
				if (trusted != null) {
					if (trusted.booleanValue()) inspect = Config.INSPECT_NEVER;
					else inspect = Config.INSPECT_ALWAYS;
				}
			}

			hidden = Caster.toBooleanValue(StringUtil.unwrap(attr.getValue("mapping-hidden")), false);
			physicalFirst = Caster.toBooleanValue(StringUtil.unwrap(attr.getValue("mapping-physical-first")), false);
		}
		catch (Throwable t) {
			ExceptionUtil.rethrowIfNecessary(t);
			DeployHandler.moveToFailedFolder(config.getDeployDirectory(), archive);
			throw Caster.toPageException(t);
		}

		finally {
			try {
				IOUtil.close(is);
			}
			catch (IOException e) {
				throw Caster.toPageException(e);
			}
			ZipUtil.close(file);
		}
		try {
			Resource trgDir = config.getConfigDir().getRealResource("archives").getRealResource(type).getRealResource(name);
			Resource trgFile = trgDir.getRealResource(archive.getName());
			trgDir.mkdirs();

			// delete existing files

			ResourceUtil.deleteContent(trgDir, null);
			ResourceUtil.moveTo(archive, trgFile, true);
			logger.log(Log.LEVEL_INFO, "archive", "Add " + type + " mapping [" + virtual + "] with archive [" + trgFile.getAbsolutePath() + "]");
			if ("regular".equalsIgnoreCase(type)) _updateMapping(virtual, null, trgFile.getAbsolutePath(), "archive", inspect, topLevel, listMode, listType, readOnly);
			else if ("cfc".equalsIgnoreCase(type)) _updateComponentMapping(virtual, null, trgFile.getAbsolutePath(), "archive", inspect);
			else if ("ct".equalsIgnoreCase(type)) _updateCustomTag(virtual, null, trgFile.getAbsolutePath(), "archive", inspect);
		}
		catch (Throwable t) {
			ExceptionUtil.rethrowIfNecessary(t);
			DeployHandler.moveToFailedFolder(config.getDeployDirectory(), archive);
			throw Caster.toPageException(t);
		}
	}

	public static void _updateRHExtension(ConfigPro config, Resource ext, boolean reload) throws PageException {
		try {
			ConfigAdmin admin = new ConfigAdmin(config, null);
			admin.updateRHExtension(config, ext, reload);
		}
		catch (Exception e) {
			throw Caster.toPageException(e);
		}
	}

	public void updateRHExtension(Config config, Resource ext, boolean reload) throws PageException {
		RHExtension rhext;
		try {
			rhext = new RHExtension(config, ext, true);
			rhext.validate();
		}
		catch (Throwable t) {
			ExceptionUtil.rethrowIfNecessary(t);
			DeployHandler.moveToFailedFolder(ext.getParentResource(), ext);
			throw Caster.toPageException(t);
		}
		updateRHExtension(config, rhext, reload);
	}

	public void updateRHExtension(Config config, RHExtension rhext, boolean reload) throws PageException {
		ConfigPro ci = (ConfigPro) config;
		Log logger = ci.getLog("deploy");
		String type = ci instanceof ConfigWeb ? "web" : "server";
		// load already installed previous version and uninstall the parts no longer needed

		RHExtension existingRH = getRHExtension(ci, rhext.getId(), null);
		if (existingRH != null) {
			// same version
			if (existingRH.getVersion().compareTo(rhext.getVersion()) == 0) {
				removeRHExtension(config, existingRH, rhext, false);
			}
			else removeRHExtension(config, existingRH, rhext, true);

		}
		// INSTALL
		try {

			// boolean clearTags=false,clearFunction=false;
			boolean reloadNecessary = false;

			// store to xml
			BundleDefinition[] existing = _updateExtension(ci, rhext);
			// _storeAndReload();
			// this must happen after "store"
			cleanBundles(rhext, ci, existing);// clean after populating the new ones
			// ConfigWebAdmin.updateRHExtension(ci,rhext);

			ZipInputStream zis = new ZipInputStream(IOUtil.toBufferedInputStream(rhext.getExtensionFile().getInputStream()));
			ZipEntry entry;
			String path;
			String fileName;
			while ((entry = zis.getNextEntry()) != null) {
				path = entry.getName();
				fileName = fileName(entry);
				// jars
				if (!entry.isDirectory() && (startsWith(path, type, "jars") || startsWith(path, type, "jar") || startsWith(path, type, "bundles")
						|| startsWith(path, type, "bundle") || startsWith(path, type, "lib") || startsWith(path, type, "libs")) && (StringUtil.endsWithIgnoreCase(path, ".jar"))) {

					Object obj = ConfigAdmin.installBundle(config, zis, fileName, rhext.getVersion(), false, false);
					// jar is not a bundle, only a regular jar
					if (!(obj instanceof BundleFile)) {
						Resource tmp = (Resource) obj;
						Resource tmpJar = tmp.getParentResource().getRealResource(ListUtil.last(path, "\\/"));
						tmp.moveTo(tmpJar);
						ConfigAdmin.updateJar(config, tmpJar, false);
					}
				}

				// flds
				if (!entry.isDirectory() && startsWith(path, type, "flds") && (StringUtil.endsWithIgnoreCase(path, ".fld") || StringUtil.endsWithIgnoreCase(path, ".fldx"))) {
					logger.log(Log.LEVEL_INFO, "extension", "Deploy fld [" + fileName + "]");
					updateFLD(zis, fileName, false);
					reloadNecessary = true;
				}
				// tlds
				if (!entry.isDirectory() && startsWith(path, type, "tlds") && (StringUtil.endsWithIgnoreCase(path, ".tld") || StringUtil.endsWithIgnoreCase(path, ".tldx"))) {
					logger.log(Log.LEVEL_INFO, "extension", "Deploy tld/tldx [" + fileName + "]");
					updateTLD(zis, fileName, false);
					reloadNecessary = true;
				}

				// tags
				if (!entry.isDirectory() && startsWith(path, type, "tags")) {
					String sub = subFolder(entry);
					logger.log(Log.LEVEL_INFO, "extension", "Deploy tag [" + sub + "]");
					updateTag(zis, sub, false);
					// clearTags=true;
					reloadNecessary = true;
				}

				// functions
				if (!entry.isDirectory() && startsWith(path, type, "functions")) {
					String sub = subFolder(entry);
					logger.log(Log.LEVEL_INFO, "extension", "Deploy function [" + sub + "]");
					updateFunction(zis, sub, false);
					// clearFunction=true;
					reloadNecessary = true;
				}

				// mappings
				if (!entry.isDirectory() && (startsWith(path, type, "archives") || startsWith(path, type, "mappings"))) {
					String sub = subFolder(entry);
					logger.log(Log.LEVEL_INFO, "extension", "deploy mapping " + sub);
					updateArchive(zis, sub, false);
					reloadNecessary = true;
					// clearFunction=true;
				}

				// event-gateway
				if (!entry.isDirectory() && (startsWith(path, type, "event-gateways") || startsWith(path, type, "eventGateways"))
						&& (StringUtil.endsWithIgnoreCase(path, "." + Constants.getCFMLComponentExtension())
								|| StringUtil.endsWithIgnoreCase(path, "." + Constants.getLuceeComponentExtension()))) {
					String sub = subFolder(entry);
					logger.log(Log.LEVEL_INFO, "extension", "Deploy event-gateway [" + sub + "]");
					updateEventGateway(zis, sub, false);
				}

				// context
				String realpath;
				if (!entry.isDirectory() && startsWith(path, type, "context") && !StringUtil.startsWith(fileName(entry), '.')) {
					realpath = path.substring(8);
					logger.log(Log.LEVEL_INFO, "extension", "Deploy context [" + realpath + "]");
					updateContext(zis, realpath, false, false);
				}
				// web contextS
				boolean first;
				if (!entry.isDirectory() && ((first = startsWith(path, type, "webcontexts")) || startsWith(path, type, "web.contexts"))
						&& !StringUtil.startsWith(fileName(entry), '.')) {
					realpath = path.substring(first ? 12 : 13);
					logger.log(Log.LEVEL_INFO, "extension", "Deploy webcontext [" + realpath + "]");
					updateWebContexts(zis, realpath, false, false);
				}
				// applications
				if (!entry.isDirectory() && (startsWith(path, type, "applications") || startsWith(path, type, "web.applications") || startsWith(path, type, "web"))
						&& !StringUtil.startsWith(fileName(entry), '.')) {
					int index;
					if (startsWith(path, type, "applications")) index = 13;
					else if (startsWith(path, type, "web.applications")) index = 17;
					else index = 4; // web

					realpath = path.substring(index);
					logger.log(Log.LEVEL_INFO, "extension", "Deploy application [" + realpath + "]");
					updateApplication(zis, realpath, false);
				}
				// configs
				if (!entry.isDirectory() && (startsWith(path, type, "config")) && !StringUtil.startsWith(fileName(entry), '.')) {
					realpath = path.substring(7);
					logger.log(Log.LEVEL_INFO, "extension", "Deploy config [" + realpath + "]");
					updateConfigs(zis, realpath, false, false);
				}
				// components
				if (!entry.isDirectory() && (startsWith(path, type, "components")) && !StringUtil.startsWith(fileName(entry), '.')) {
					realpath = path.substring(11);
					logger.log(Log.LEVEL_INFO, "extension", "Deploy component [" + realpath + "]");
					updateComponent(zis, realpath, false, false);
				}

				// plugins
				if (!entry.isDirectory() && (startsWith(path, type, "plugins")) && !StringUtil.startsWith(fileName(entry), '.')) {
					realpath = path.substring(8);
					logger.log(Log.LEVEL_INFO, "extension", "Deploy plugin [" + realpath + "]");
					updatePlugin(zis, realpath, false);
				}

				zis.closeEntry();
			}
			////////////////////////////////////////////

			// load the bundles
			if (rhext.getStartBundles()) {
				rhext.deployBundles(ci);
				BundleInfo[] bfs = rhext.getBundles();
				if (bfs != null) {
					for (BundleInfo bf: bfs) {
						OSGiUtil.loadBundleFromLocal(bf.getSymbolicName(), bf.getVersion(), null, false, null);
					}
				}
			}

			// update cache
			if (!ArrayUtil.isEmpty(rhext.getCaches())) {
				Iterator<Map<String, String>> itl = rhext.getCaches().iterator();
				Map<String, String> map;
				while (itl.hasNext()) {
					map = itl.next();
					ClassDefinition cd = RHExtension.toClassDefinition(config, map, null);
					if (cd != null && cd.isBundle()) {
						_updateCache(cd);
						reloadNecessary = true;
					}
					logger.info("extension", "Update cache [" + cd + "] from extension [" + rhext.getName() + ":" + rhext.getVersion() + "]");
				}
			}

			// update cache handler
			if (!ArrayUtil.isEmpty(rhext.getCacheHandlers())) {
				Iterator<Map<String, String>> itl = rhext.getCacheHandlers().iterator();
				Map<String, String> map;
				while (itl.hasNext()) {
					map = itl.next();
					ClassDefinition cd = RHExtension.toClassDefinition(config, map, null);
					String _id = map.get("id");
					if (!StringUtil.isEmpty(_id) && cd != null && cd.hasClass()) {
						_updateCacheHandler(_id, cd);
						reloadNecessary = true;
					}
					logger.info("extension", "Update cache handler [" + cd + "] from extension [" + rhext.getName() + ":" + rhext.getVersion() + "]");
				}
			}

			// update AMF
			if (!ArrayUtil.isEmpty(rhext.getAMFs())) {
				Iterator<Map<String, String>> itl = rhext.getAMFs().iterator();
				Map<String, String> map;
				while (itl.hasNext()) {
					map = itl.next();
					ClassDefinition cd = RHExtension.toClassDefinition(config, map, null);
					if (cd != null && cd.hasClass()) {
						_updateAMFEngine(cd, map.get("caster"), map.get("configuration"));
						reloadNecessary = true;
					}
					logger.info("extension", "Update AMF engine [" + cd + "] from extension [" + rhext.getName() + ":" + rhext.getVersion() + "]");
				}
			}

			// update Search
			if (!ArrayUtil.isEmpty(rhext.getSearchs())) {
				Iterator<Map<String, String>> itl = rhext.getSearchs().iterator();
				Map<String, String> map;
				while (itl.hasNext()) {
					map = itl.next();
					ClassDefinition cd = RHExtension.toClassDefinition(config, map, null);
					if (cd != null && cd.hasClass()) {
						_updateSearchEngine(cd);
						reloadNecessary = true;
					}
					logger.info("extension", "Update search engine [" + cd + "] from extension [" + rhext.getName() + ":" + rhext.getVersion() + "]");
				}
			}

			// update Resource
			if (!ArrayUtil.isEmpty(rhext.getResources())) {
				Iterator<Map<String, String>> itl = rhext.getResources().iterator();
				Map<String, String> map;
				while (itl.hasNext()) {
					map = itl.next();
					ClassDefinition cd = RHExtension.toClassDefinition(config, map, null);
					String scheme = map.get("scheme");
					if (cd != null && cd.hasClass() && !StringUtil.isEmpty(scheme)) {
						Struct args = new StructImpl(Struct.TYPE_LINKED);
						copyButIgnoreClassDef(map, args);
						args.remove("scheme");
						_updateResourceProvider(scheme, cd, args);
						reloadNecessary = true;
					}
					logger.info("extension", "Update resource provider [" + scheme + "] from extension [" + rhext.getName() + ":" + rhext.getVersion() + "]");
				}
			}

			// update orm
			if (!ArrayUtil.isEmpty(rhext.getOrms())) {
				Iterator<Map<String, String>> itl = rhext.getOrms().iterator();
				Map<String, String> map;
				while (itl.hasNext()) {
					map = itl.next();
					ClassDefinition cd = RHExtension.toClassDefinition(config, map, null);

					if (cd != null && cd.hasClass()) {
						_updateORMEngine(cd);
						reloadNecessary = true;
					}
					logger.info("extension", "Update orm engine [" + cd + "] from extension [" + rhext.getName() + ":" + rhext.getVersion() + "]");
				}
			}

			// update webservice
			if (!ArrayUtil.isEmpty(rhext.getWebservices())) {
				Iterator<Map<String, String>> itl = rhext.getWebservices().iterator();
				Map<String, String> map;
				while (itl.hasNext()) {
					map = itl.next();
					ClassDefinition cd = RHExtension.toClassDefinition(config, map, null);

					if (cd != null && cd.hasClass()) {
						_updateWebserviceHandler(cd);
						reloadNecessary = true;
					}
					logger.info("extension", "Update webservice handler [" + cd + "] from extension [" + rhext.getName() + ":" + rhext.getVersion() + "]");
				}
			}

			// update monitor
			if (!ArrayUtil.isEmpty(rhext.getMonitors())) {
				Iterator<Map<String, String>> itl = rhext.getMonitors().iterator();
				Map<String, String> map;
				while (itl.hasNext()) {
					map = itl.next();
					ClassDefinition cd = RHExtension.toClassDefinition(config, map, null);
					if (cd != null && cd.hasClass()) {
						_updateMonitorEnabled(true);
						_updateMonitor(cd, map.get("type"), map.get("name"), true);
						reloadNecessary = true;
					}
					logger.info("extension", "Update monitor engine [" + cd + "] from extension [" + rhext.getName() + ":" + rhext.getVersion() + "]");
				}
			}

			// update jdbc
			if (!ArrayUtil.isEmpty(rhext.getJdbcs())) {
				Iterator<Map<String, String>> itl = rhext.getJdbcs().iterator();
				Map<String, String> map;
				while (itl.hasNext()) {
					map = itl.next();
					ClassDefinition cd = RHExtension.toClassDefinition(config, map, null);
					String _label = map.get("label");
					String _id = map.get("id");
					if (cd != null && cd.isBundle()) {
						_updateJDBCDriver(_label, _id, cd);
						reloadNecessary = true;
					}
					logger.info("extension", "Update JDBC Driver [" + _label + ":" + cd + "] from extension [" + rhext.getName() + ":" + rhext.getVersion() + "]");
				}
			}

			// update startup hook
			if (!ArrayUtil.isEmpty(rhext.getStartupHooks())) {
				Iterator<Map<String, String>> itl = rhext.getStartupHooks().iterator();
				Map<String, String> map;
				while (itl.hasNext()) {
					map = itl.next();
					ClassDefinition cd = RHExtension.toClassDefinition(config, map, null);
					if (cd != null && cd.isBundle()) {
						_updateStartupHook(cd);
						reloadNecessary = true;
					}
					logger.info("extension", "Update Startup Hook [" + cd + "] from extension [" + rhext.getName() + ":" + rhext.getVersion() + "]");
				}
			}

			// update mapping
			if (!ArrayUtil.isEmpty(rhext.getMappings())) {
				Iterator<Map<String, String>> itl = rhext.getMappings().iterator();
				Map<String, String> map;

				String virtual, physical, archive, primary;
				short inspect;
				int lmode, ltype;
				boolean toplevel, readonly;
				while (itl.hasNext()) {
					map = itl.next();
					virtual = map.get("virtual");
					physical = map.get("physical");
					archive = map.get("archive");
					primary = map.get("primary");

					inspect = ConfigWebUtil.inspectTemplate(map.get("inspect"), Config.INSPECT_UNDEFINED);
					lmode = ConfigWebUtil.toListenerMode(map.get("listener-mode"), -1);
					ltype = ConfigWebUtil.toListenerType(map.get("listener-type"), -1);

					toplevel = Caster.toBooleanValue(map.get("toplevel"), false);
					readonly = Caster.toBooleanValue(map.get("readonly"), false);

					_updateMapping(virtual, physical, archive, primary, inspect, toplevel, lmode, ltype, readonly);
					reloadNecessary = true;

					logger.info("extension", "Update Mapping [" + virtual + "]");
				}
			}

			// update event-gateway-instance

			if (!ArrayUtil.isEmpty(rhext.getEventGatewayInstances())) {
				Iterator<Map<String, Object>> itl = rhext.getEventGatewayInstances().iterator();
				Map<String, Object> map;
				while (itl.hasNext()) {
					map = itl.next();
					// id
					String id = Caster.toString(map.get("id"), null);
					// class
					ClassDefinition cd = RHExtension.toClassDefinition(config, map, null);
					// component path
					String cfcPath = Caster.toString(map.get("cfc-path"), null);
					if (StringUtil.isEmpty(cfcPath)) cfcPath = Caster.toString(map.get("component-path"), null);
					// listener component path
					String listenerCfcPath = Caster.toString(map.get("listener-cfc-path"), null);
					if (StringUtil.isEmpty(listenerCfcPath)) listenerCfcPath = Caster.toString(map.get("listener-component-path"), null);
					// startup mode
					String strStartupMode = Caster.toString(map.get("startup-mode"), "automatic");
					int startupMode = GatewayEntryImpl.toStartup(strStartupMode, GatewayEntryImpl.STARTUP_MODE_AUTOMATIC);
					// read only
					boolean readOnly = Caster.toBooleanValue(map.get("read-only"), false);
					// custom
					Struct custom = Caster.toStruct(map.get("custom"), null);
					/*
					 * print.e("::::::::::::::::::::::::::::::::::::::::::"); print.e("id:"+id); print.e("cd:"+cd);
					 * print.e("cfc:"+cfcPath); print.e("listener:"+listenerCfcPath);
					 * print.e("startupMode:"+startupMode); print.e(custom);
					 */

					if (!StringUtil.isEmpty(id) && (!StringUtil.isEmpty(cfcPath) || (cd != null && cd.hasClass()))) {
						_updateGatewayEntry(id, cd, cfcPath, listenerCfcPath, startupMode, custom, readOnly);
					}

					logger.info("extension", "Update event gateway entry [" + id + "] from extension [" + rhext.getName() + ":" + rhext.getVersion() + "]");
				}
			}

			// reload
			// if(reloadNecessary){
			reloadNecessary = true;
			if (reload && reloadNecessary) _storeAndReload();
			else _store();
			// }
		}
		catch (Throwable t) {
			ExceptionUtil.rethrowIfNecessary(t);
			DeployHandler.moveToFailedFolder(rhext.getExtensionFile().getParentResource(), rhext.getExtensionFile());
			try {
				ConfigAdmin.removeRHExtensions((ConfigPro) config, new String[] { rhext.getId() }, false);
			}
			catch (Throwable t2) {
				ExceptionUtil.rethrowIfNecessary(t2);
			}
			throw Caster.toPageException(t);
		}
	}

	private void copyButIgnoreClassDef(Map<String, String> src, Struct trg) {
		Iterator<Entry<String, String>> it = src.entrySet().iterator();
		Entry<String, String> e;
		String name;
		while (it.hasNext()) {
			e = it.next();
			name = e.getKey();
			if ("class".equals(name) || "bundle-name".equals(name) || "bundlename".equals(name) || "bundleName".equals(name) || "bundle-version".equals(name)
					|| "bundleversion".equals(name) || "bundleVersion".equals(name))
				continue;
			trg.setEL(name, e.getValue());
		}
	}

	/**
	 * removes an installed extension from the system
	 * 
	 * @param config
	 * @param rhe extension to remove
	 * @param replacementRH the extension that will replace this extension, so do not remove parts
	 *            defined in this extension.
	 * @throws PageException
	 */
	private void removeRHExtension(Config config, RHExtension rhe, RHExtension replacementRH, boolean deleteExtension) throws PageException {
		ConfigPro ci = ((ConfigPro) config);
		Log logger = ci.getLog("deploy");

		// MUST check replacementRH everywhere

		try {
			// remove the bundles
			BundleDefinition[] candidatesToRemove = OSGiUtil.toBundleDefinitions(rhe.getBundles(EMPTY));
			if (replacementRH != null) {
				// spare bundles used in the new extension as well
				Map<String, BundleDefinition> notRemove = toMap(OSGiUtil.toBundleDefinitions(replacementRH.getBundles(EMPTY)));
				List<BundleDefinition> tmp = new ArrayList<OSGiUtil.BundleDefinition>();
				String key;
				for (int i = 0; i < candidatesToRemove.length; i++) {
					key = candidatesToRemove[i].getName() + "|" + candidatesToRemove[i].getVersionAsString();
					if (notRemove.containsKey(key)) continue;
					tmp.add(candidatesToRemove[i]);
				}
				candidatesToRemove = tmp.toArray(new BundleDefinition[tmp.size()]);
			}
			ConfigAdmin.cleanBundles(rhe, ci, candidatesToRemove);

			// FLD
			removeFLDs(logger, rhe.getFlds()); // MUST check if others use one of this fld

			// TLD
			removeTLDs(logger, rhe.getTlds()); // MUST check if others use one of this tld

			// Tag
			removeTags(logger, rhe.getTags());

			// Functions
			removeFunctions(logger, rhe.getFunctions());

			// Event Gateway
			removeEventGateways(logger, rhe.getEventGateways());

			// context
			removeContext(config, false, logger, rhe.getContexts()); // MUST check if others use one of this

			// web contextS
			removeWebContexts(config, false, logger, rhe.getWebContexts()); // MUST check if others use one of this

			// applications
			removeApplications(config, logger, rhe.getApplications()); // MUST check if others use one of this

			// plugins
			removePlugins(config, logger, rhe.getPlugins()); // MUST check if others use one of this

			// remove cache handler
			if (!ArrayUtil.isEmpty(rhe.getCacheHandlers())) {
				Iterator<Map<String, String>> itl = rhe.getCacheHandlers().iterator();
				Map<String, String> map;
				while (itl.hasNext()) {
					map = itl.next();
					ClassDefinition cd = RHExtension.toClassDefinition(config, map, null);
					String _id = map.get("id");

					if (!StringUtil.isEmpty(_id) && cd != null && cd.hasClass()) {
						_removeCacheHandler(_id);
						// reload=true;
					}
					logger.info("extension", "Remove cache handler [" + cd + "] from extension [" + rhe.getName() + ":" + rhe.getVersion() + "]");
				}
			}

			// remove cache
			if (!ArrayUtil.isEmpty(rhe.getCaches())) {
				Iterator<Map<String, String>> itl = rhe.getCaches().iterator();
				Map<String, String> map;
				while (itl.hasNext()) {
					map = itl.next();
					ClassDefinition cd = RHExtension.toClassDefinition(config, map, null);
					if (cd != null && cd.isBundle()) {
						_removeCache(cd);
						// reload=true;
					}
					logger.info("extension", "Remove cache handler [" + cd + "] from extension [" + rhe.getName() + ":" + rhe.getVersion() + "]");
				}
			}

			// remove Search
			if (!ArrayUtil.isEmpty(rhe.getSearchs())) {
				Iterator<Map<String, String>> itl = rhe.getSearchs().iterator();
				Map<String, String> map;
				while (itl.hasNext()) {
					map = itl.next();
					ClassDefinition cd = RHExtension.toClassDefinition(config, map, null);
					if (cd != null && cd.hasClass()) {
						_removeSearchEngine();
						// reload=true;
					}
					logger.info("extension", "Remove search engine [" + cd + "] from extension [" + rhe.getName() + ":" + rhe.getVersion() + "]");
				}
			}

			// remove resource
			if (!ArrayUtil.isEmpty(rhe.getResources())) {
				Iterator<Map<String, String>> itl = rhe.getResources().iterator();
				Map<String, String> map;
				while (itl.hasNext()) {
					map = itl.next();
					ClassDefinition cd = RHExtension.toClassDefinition(config, map, null);
					String scheme = map.get("scheme");
					if (cd != null && cd.hasClass()) {
						_removeResourceProvider(scheme);
					}
					logger.info("extension", "Remove resource [" + cd + "] from extension [" + rhe.getName() + ":" + rhe.getVersion() + "]");
				}
			}

			// remove AMF
			if (!ArrayUtil.isEmpty(rhe.getAMFs())) {
				Iterator<Map<String, String>> itl = rhe.getAMFs().iterator();
				Map<String, String> map;
				while (itl.hasNext()) {
					map = itl.next();
					ClassDefinition cd = RHExtension.toClassDefinition(config, map, null);
					if (cd != null && cd.hasClass()) {
						_removeAMFEngine();
						// reload=true;
					}
					logger.info("extension", "Remove search engine [" + cd + "] from extension [" + rhe.getName() + ":" + rhe.getVersion() + "]");
				}
			}

			// remove orm
			if (!ArrayUtil.isEmpty(rhe.getOrms())) {
				Iterator<Map<String, String>> itl = rhe.getOrms().iterator();
				Map<String, String> map;
				while (itl.hasNext()) {
					map = itl.next();
					ClassDefinition cd = RHExtension.toClassDefinition(config, map, null);

					if (cd != null && cd.hasClass()) {
						_removeORMEngine();
						// reload=true;
					}
					logger.info("extension", "Remove orm engine [" + cd + "] from extension [" + rhe.getName() + ":" + rhe.getVersion() + "]");
				}
			}

			// remove webservice
			if (!ArrayUtil.isEmpty(rhe.getWebservices())) {
				Iterator<Map<String, String>> itl = rhe.getWebservices().iterator();
				Map<String, String> map;
				while (itl.hasNext()) {
					map = itl.next();
					ClassDefinition cd = RHExtension.toClassDefinition(config, map, null);

					if (cd != null && cd.hasClass()) {
						_removeWebserviceHandler();
						// reload=true;
					}
					logger.info("extension", "Remove webservice handler [" + cd + "] from extension [" + rhe.getName() + ":" + rhe.getVersion() + "]");
				}
			}

			// remove monitor
			if (!ArrayUtil.isEmpty(rhe.getMonitors())) {
				Iterator<Map<String, String>> itl = rhe.getMonitors().iterator();
				Map<String, String> map;
				String name;
				while (itl.hasNext()) {
					map = itl.next();

					// ClassDefinition cd = RHExtension.toClassDefinition(config,map);

					// if(cd.hasClass()) {
					_removeMonitor(map.get("type"), name = map.get("name"));
					// reload=true;
					// }
					logger.info("extension", "Remove monitor [" + name + "] from extension [" + rhe.getName() + ":" + rhe.getVersion() + "]");
				}
			}

			// remove jdbc
			if (!ArrayUtil.isEmpty(rhe.getJdbcs())) {
				Iterator<Map<String, String>> itl = rhe.getJdbcs().iterator();
				Map<String, String> map;
				while (itl.hasNext()) {
					map = itl.next();
					ClassDefinition cd = RHExtension.toClassDefinition(config, map, null);
					if (cd != null && cd.isBundle()) {
						_removeJDBCDriver(cd);
					}
					logger.info("extension", "Remove JDBC Driver [" + cd + "] from extension [" + rhe.getName() + ":" + rhe.getVersion() + "]");
				}
			}

			// remove startup hook
			if (!ArrayUtil.isEmpty(rhe.getStartupHooks())) {
				Iterator<Map<String, String>> itl = rhe.getStartupHooks().iterator();
				Map<String, String> map;
				while (itl.hasNext()) {
					map = itl.next();
					ClassDefinition cd = RHExtension.toClassDefinition(config, map, null);
					if (cd != null && cd.isBundle()) {
						_removeStartupHook(cd);
					}
					logger.info("extension", "Remove Startup Hook [" + cd + "] from extension [" + rhe.getName() + ":" + rhe.getVersion() + "]");
				}
			}

			// remove mapping
			if (!ArrayUtil.isEmpty(rhe.getMappings())) {
				Iterator<Map<String, String>> itl = rhe.getMappings().iterator();
				Map<String, String> map;
				String virtual;
				while (itl.hasNext()) {
					map = itl.next();
					virtual = map.get("virtual");
					_removeMapping(virtual);
					logger.info("extension", "remove Mapping [" + virtual + "]");
				}
			}

			// remove event-gateway-instance
			if (!ArrayUtil.isEmpty(rhe.getEventGatewayInstances())) {
				Iterator<Map<String, Object>> itl = rhe.getEventGatewayInstances().iterator();
				Map<String, Object> map;
				String id;
				while (itl.hasNext()) {
					map = itl.next();
					id = Caster.toString(map.get("id"), null);
					if (!StringUtil.isEmpty(id)) {
						_removeGatewayEntry(id);
						logger.info("extension", "remove event gateway entry [" + id + "]");
					}
				}
			}

			// Loop Files
			ZipInputStream zis = new ZipInputStream(IOUtil.toBufferedInputStream(rhe.getExtensionFile().getInputStream()));
			String type = ci instanceof ConfigWeb ? "web" : "server";
			try {
				ZipEntry entry;
				String path;
				String fileName;
				Resource tmp;
				while ((entry = zis.getNextEntry()) != null) {
					path = entry.getName();
					fileName = fileName(entry);

					// archives
					if (!entry.isDirectory() && (startsWith(path, type, "archives") || startsWith(path, type, "mappings"))) {
						String sub = subFolder(entry);
						logger.log(Log.LEVEL_INFO, "extension", "Remove archive [" + sub + "] registered as a mapping");
						tmp = SystemUtil.getTempFile(".lar", false);
						IOUtil.copy(zis, tmp, false);
						removeArchive(tmp);
					}
					zis.closeEntry();
				}
			}
			finally {
				IOUtil.close(zis);
			}

			// now we can delete the extension
			if (deleteExtension) rhe.getExtensionFile().delete();

		}
		catch (Throwable t) {
			ExceptionUtil.rethrowIfNecessary(t);
			// failed to uninstall, so we install it again
			try {
				updateRHExtension(config, rhe.getExtensionFile(), true);
				// RHExtension.install(config, rhe.getExtensionFile());
			}
			catch (Throwable t2) {
				ExceptionUtil.rethrowIfNecessary(t2);
			}
			throw Caster.toPageException(t);
		}

	}

	private Map<String, BundleDefinition> toMap(BundleDefinition[] bundleDefinitions) {
		Map<String, BundleDefinition> rtn = new HashMap<String, OSGiUtil.BundleDefinition>();
		for (int i = 0; i < bundleDefinitions.length; i++) {
			rtn.put(bundleDefinitions[i].getName() + "|" + bundleDefinitions[i].getVersionAsString(), bundleDefinitions[i]);
		}
		return rtn;
	}

	private static boolean startsWith(String path, String type, String name) {
		return StringUtil.startsWithIgnoreCase(path, name + "/") || StringUtil.startsWithIgnoreCase(path, type + "/" + name + "/");
	}

	private static String fileName(ZipEntry entry) {
		String name = entry.getName();
		int index = name.lastIndexOf('/');
		if (index == -1) return name;
		return name.substring(index + 1);
	}

	private static String subFolder(ZipEntry entry) {
		String name = entry.getName();
		int index = name.indexOf('/');
		if (index == -1) return name;
		return name.substring(index + 1);
	}

	public void removeExtension(String provider, String id) throws SecurityException {
		checkWriteAccess();

		Array children = ConfigWebUtil.getAsArray("extensions", "extension", root);
		String _provider, _id;
		for (int i = children.size(); i > 0; i--) {
			Struct tmp = Caster.toStruct(children.get(i, null), null);
			if (tmp == null) continue;

			_provider = ConfigWebUtil.getAsString("provider", tmp, null);
			_id = ConfigWebUtil.getAsString("id", tmp, null);
			if (_provider != null && _provider.equalsIgnoreCase(provider) && _id != null && _id.equalsIgnoreCase(id)) {
				children.removeEL(i);
			}
		}
	}

	public void verifyExtensionProvider(String strUrl) throws PageException {
		HTTPResponse method = null;
		try {
			URL url = HTTPUtil.toURL(strUrl + "?wsdl", HTTPUtil.ENCODED_AUTO);
			method = HTTPEngine.get(url, null, null, 2000, true, null, null, null, null);
		}
		catch (MalformedURLException e) {
			throw new ApplicationException("Url definition [" + strUrl + "] is invalid");
		}
		catch (IOException e) {
			throw new ApplicationException("Can't invoke [" + strUrl + "]", e.getMessage());
		}

		if (method.getStatusCode() != 200) {
			int code = method.getStatusCode();
			String text = method.getStatusText();
			String msg = code + " " + text;
			throw new HTTPException(msg, null, code, text, method.getURL());
		}
		// Object o =
		CreateObject.doWebService(null, strUrl + "?wsdl");
		HTTPEngine.closeEL(method);
	}

	public void updateTLD(Resource resTld) throws IOException {
		updateLD(config.getTldFile(), resTld);
	}

	public void updateFLD(Resource resFld) throws IOException {
		updateLD(config.getFldFile(), resFld);
	}

	private void updateLD(Resource dir, Resource res) throws IOException {
		if (!dir.exists()) dir.createDirectory(true);

		Resource file = dir.getRealResource(res.getName());
		if (file.length() != res.length()) {
			ResourceUtil.copy(res, file);
		}
	}

	void updateTLD(InputStream is, String name, boolean closeStream) throws IOException {
		write(config.getTldFile(), is, name, closeStream);
	}

	void updateFLD(InputStream is, String name, boolean closeStream) throws IOException {
		write(config.getFldFile(), is, name, closeStream);
	}

	void updateTag(InputStream is, String name, boolean closeStream) throws IOException {
		write(config.getDefaultTagMapping().getPhysical(), is, name, closeStream);
	}

	void updateFunction(InputStream is, String name, boolean closeStream) throws IOException {
		write(config.getDefaultFunctionMapping().getPhysical(), is, name, closeStream);
	}

	void updateEventGateway(InputStream is, String name, boolean closeStream) throws IOException {
		write(config.getEventGatewayDirectory(), is, name, closeStream);
	}

	void updateArchive(InputStream is, String name, boolean closeStream) throws IOException, PageException {
		Resource res = write(SystemUtil.getTempDirectory(), is, name, closeStream);
		// Resource res = write(DeployHandler.getDeployDirectory(config),is,name,closeStream);
		updateArchive(config, res);
	}

	private static Resource write(Resource dir, InputStream is, String name, boolean closeStream) throws IOException {
		if (!dir.exists()) dir.createDirectory(true);
		Resource file = dir.getRealResource(name);
		Resource p = file.getParentResource();
		if (!p.exists()) p.createDirectory(true);
		IOUtil.copy(is, file.getOutputStream(), closeStream, true);
		return file;
	}

	public void removeTLD(String name) throws IOException {
		removeFromDirectory(config.getTldFile(), name);
	}

	public void removeTLDs(Log logger, String[] names) throws IOException {
		if (ArrayUtil.isEmpty(names)) return;
		Resource file = config.getTldFile();
		for (int i = 0; i < names.length; i++) {
			logger.log(Log.LEVEL_INFO, "extension", "Remove TLD file " + names[i]);
			removeFromDirectory(file, names[i]);
		}
	}

	public void removeEventGateways(Log logger, String[] relpath) throws IOException {
		if (ArrayUtil.isEmpty(relpath)) return;
		Resource dir = config.getEventGatewayDirectory();// get Event gateway Directory
		for (int i = 0; i < relpath.length; i++) {
			logger.log(Log.LEVEL_INFO, "extension", "Remove Event Gateway " + relpath[i]);
			removeFromDirectory(dir, relpath[i]);
		}
	}

	public void removeFunctions(Log logger, String[] relpath) throws IOException {
		if (ArrayUtil.isEmpty(relpath)) return;
		Resource file = config.getDefaultFunctionMapping().getPhysical();
		for (int i = 0; i < relpath.length; i++) {
			logger.log(Log.LEVEL_INFO, "extension", "Remove Function " + relpath[i]);
			removeFromDirectory(file, relpath[i]);
		}
	}

	public void removeArchive(Resource archive) throws IOException, PageException {
		Log logger = config.getLog("deploy");
		String virtual = null, type = null;
		InputStream is = null;
		ZipFile file = null;
		try {
			file = new ZipFile(FileWrapper.toFile(archive));
			ZipEntry entry = file.getEntry("META-INF/MANIFEST.MF");

			// no manifest
			if (entry == null) throw new ApplicationException("Cannot remove " + Constants.NAME + " Archive [" + archive + "], file is to old, the file does not have a MANIFEST.");

			is = file.getInputStream(entry);
			Manifest manifest = new Manifest(is);
			Attributes attr = manifest.getMainAttributes();
			virtual = StringUtil.unwrap(attr.getValue("mapping-virtual-path"));
			type = StringUtil.unwrap(attr.getValue("mapping-type"));
			logger.info("archive", "Remove " + type + " mapping [" + virtual + "]");

			if ("regular".equalsIgnoreCase(type)) removeMapping(virtual);
			else if ("cfc".equalsIgnoreCase(type)) removeComponentMapping(virtual);
			else if ("ct".equalsIgnoreCase(type)) removeCustomTag(virtual);
			else throw new ApplicationException("Invalid type [" + type + "], valid types are [regular, cfc, ct]");
		}
		catch (Throwable t) {
			ExceptionUtil.rethrowIfNecessary(t);
			throw Caster.toPageException(t);
		}
		finally {
			IOUtil.close(is);
			ZipUtil.close(file);
		}
	}

	public void removeTags(Log logger, String[] relpath) throws IOException {
		if (ArrayUtil.isEmpty(relpath)) return;
		Resource file = config.getDefaultTagMapping().getPhysical();
		for (int i = 0; i < relpath.length; i++) {
			logger.log(Log.LEVEL_INFO, "extension", "Remove Tag [" + relpath[i] + "]");
			removeFromDirectory(file, relpath[i]);
		}
	}

	public void removeFLDs(Log logger, String[] names) throws IOException {
		if (ArrayUtil.isEmpty(names)) return;

		Resource file = config.getFldFile();
		for (int i = 0; i < names.length; i++) {
			logger.log(Log.LEVEL_INFO, "extension", "Remove FLD file [" + names[i] + "]");
			removeFromDirectory(file, names[i]);
		}
	}

	public void removeFLD(String name) throws IOException {
		removeFromDirectory(config.getFldFile(), name);
	}

	private void removeFromDirectory(Resource dir, String relpath) throws IOException {
		if (dir.isDirectory()) {
			Resource file = dir.getRealResource(relpath);
			if (file.isFile()) file.remove(false);
		}
	}

	public void updateRemoteClientUsage(String code, String displayname) {
		Struct usage = config.getRemoteClientUsage();
		usage.setEL(code, displayname);

		Struct extensions = _getRootElement("remote-clients");
		extensions.setEL("usage", toStringURLStyle(usage));

	}

	public void updateClusterClass(ClassDefinition cd) throws PageException {
		if (cd.getClassName() == null) cd = new ClassDefinitionImpl(ClusterNotSupported.class.getName(), null, null, null);

		Class clazz = null;
		try {
			clazz = cd.getClazz();
		}
		catch (Exception e) {
			throw Caster.toPageException(e);
		}
		if (!Reflector.isInstaneOf(clazz, Cluster.class, false) && !Reflector.isInstaneOf(clazz, ClusterRemote.class, false)) throw new ApplicationException(
				"Class [" + clazz.getName() + "] does not implement interface [" + Cluster.class.getName() + "] or [" + ClusterRemote.class.getName() + "]");

		Struct scope = _getRootElement("scope");
		setClass(scope, null, "cluster-", cd);
		ScopeContext.clearClusterScope();
	}

	public void updateVideoExecuterClass(ClassDefinition cd) throws PageException {

		if (cd.getClassName() == null) cd = new ClassDefinitionImpl(VideoExecuterNotSupported.class.getName());

		Struct app = _getRootElement("video");
		setClass(app, VideoExecuter.class, "video-executer-", cd);
	}

	public void updateAdminSyncClass(ClassDefinition cd) throws PageException {

		if (cd.getClassName() == null) cd = new ClassDefinitionImpl(AdminSyncNotSupported.class.getName());

		Struct app = _getRootElement("application");
		setClass(app, AdminSync.class, "admin-sync-", cd);
	}

	public void removeRemoteClientUsage(String code) {
		Struct usage = config.getRemoteClientUsage();
		usage.removeEL(KeyImpl.getInstance(code));

		Struct extensions = _getRootElement("remote-clients");
		extensions.setEL("usage", toStringURLStyle(usage));

	}

	class MyResourceNameFilter implements ResourceNameFilter {
		private String name;

		public MyResourceNameFilter(String name) {
			this.name = name;
		}

		@Override
		public boolean accept(Resource parent, String name) {
			return name.equals(this.name);
		}
	}

	public void updateSerial(String serial) throws PageException {

		checkWriteAccess();
		if (!(config instanceof ConfigServer)) {
			throw new SecurityException("Can't change serial number from this context, access is denied");
		}

		if (!StringUtil.isEmpty(serial)) {
			serial = serial.trim();
			if (!new SerialNumber(serial).isValid(serial)) throw new SecurityException("Serial number is invalid");
			root.setEL("serial-number", serial);
		}
		else {
			try {
				rem(root, "serial-number");
			}
			catch (Throwable t) {
				ExceptionUtil.rethrowIfNecessary(t);
			}
		}
		try {
			rem(root, "serial");
		}
		catch (Throwable t) {
			ExceptionUtil.rethrowIfNecessary(t);
		}
	}

	public boolean updateLabel(String hash, String label) {
		// check
		if (StringUtil.isEmpty(hash, true)) return false;
		if (StringUtil.isEmpty(label, true)) return false;

		hash = hash.trim();
		label = label.trim();

		Array children = ConfigWebUtil.getAsArray("labels", "label", root);

		// Update
		for (int i = 1; i <= children.size(); i++) {
			Struct tmp = Caster.toStruct(children.get(i, null), null);
			if (tmp == null) continue;

			String h = ConfigWebUtil.getAsString("id", tmp, null);
			if (h != null) {
				if (h.equals(hash)) {
					if (label.equals(tmp.get("name", null))) return false;
					tmp.setEL("name", label);
					return true;
				}
			}
		}

		// Insert
		Struct el = new StructImpl(Struct.TYPE_LINKED);
		children.appendEL(el);
		el.setEL("id", hash);
		el.setEL("name", label);

		return true;
	}

	public void updateDebugSetting(int maxLogs) throws SecurityException {
		checkWriteAccess();
		boolean hasAccess = ConfigWebUtil.hasAccess(config, SecurityManager.TYPE_DEBUGGING);
		if (!hasAccess) throw new SecurityException("Access denied to change debugging settings");

		Struct debugging = _getRootElement("debugging");
		if (maxLogs == -1) rem(debugging, "max-records-logged");
		else debugging.setEL("max-records-logged", Caster.toString(maxLogs));
	}

	public void updateDebugEntry(String type, String iprange, String label, String path, String fullname, Struct custom) throws SecurityException, IOException {
		checkWriteAccess();
		boolean hasAccess = ConfigWebUtil.hasAccess(config, SecurityManager.TYPE_DEBUGGING);
		if (!hasAccess) throw new SecurityException("Access denied to change debugging settings");

		// leave this, this method throws an exception when ip range is not valid
		IPRange.getInstance(iprange);

		String id = MD5.getDigestAsString(label.trim().toLowerCase());
		type = type.trim();
		iprange = iprange.trim();
		label = label.trim();

		Array children = ConfigWebUtil.getAsArray("debugging", "debug-entry", root);

		// Update
		Struct el = null;
		for (int i = 1; i <= children.size(); i++) {
			Struct tmp = Caster.toStruct(children.get(i, null), null);
			if (tmp == null) continue;

			String _id = ConfigWebUtil.getAsString("id", tmp, null);
			if (_id != null) {
				if (_id.equals(id)) {
					el = tmp;
					break;
				}
			}
		}

		// Insert
		if (el == null) {
			el = new StructImpl(Struct.TYPE_LINKED);
			children.appendEL(el);
			el.setEL("id", id);
		}

		el.setEL("type", type);
		el.setEL("iprange", iprange);
		el.setEL("label", label);
		el.setEL("path", path);
		el.setEL("fullname", fullname);
		el.setEL("custom", toStringURLStyle(custom));
	}

	public void removeDebugEntry(String id) throws SecurityException {
		checkWriteAccess();
		boolean hasAccess = ConfigWebUtil.hasAccess(config, SecurityManager.TYPE_DEBUGGING);
		if (!hasAccess) throw new SecurityException("Access denied to change debugging settings");

		Array children = ConfigWebUtil.getAsArray("debugging", "debug-entry", root);
		String _id;
		if (children.size() > 0) {
			for (int i = children.size(); i > 0; i--) {
				Struct el = Caster.toStruct(children.get(i, null), null);
				if (el == null) continue;

				_id = ConfigWebUtil.getAsString("id", el, null);
				if (_id != null && _id.equalsIgnoreCase(id)) {
					children.removeEL(i);
				}
			}
		}
	}

	public void updateLoginSettings(boolean captcha, boolean rememberMe, int delay) {
		Struct login = _getRootElement("login");
		login.setEL("captcha", Caster.toString(captcha));
		login.setEL("rememberme", Caster.toString(rememberMe));
		login.setEL("delay", Caster.toString(delay));
	}

	public void updateLogSettings(String name, int level, ClassDefinition appenderCD, Struct appenderArgs, ClassDefinition layoutCD, Struct layoutArgs) throws PageException {
		checkWriteAccess();
		// TODO
		// boolean hasAccess=ConfigWebUtil.hasAccess(config,SecurityManagerImpl.TYPE_GATEWAY);
		// if(!hasAccess) throw new SecurityException("no access to update gateway entry");

		// check parameters
		name = name.trim();
		if (StringUtil.isEmpty(name)) throw new ApplicationException("Log file name cannot be empty");

		if (appenderCD == null || !appenderCD.hasClass()) throw new ExpressionException("Appender class is required");
		if (layoutCD == null || !layoutCD.hasClass()) throw new ExpressionException("Layout class is required");

		try {
			appenderCD.getClazz();
			layoutCD.getClazz();
		}
		catch (Exception e) {
			throw Caster.toPageException(e);
		}
		Array children = ConfigWebUtil.getAsArray("logging", "logger", root);

		// Update
		Struct el = null;
		for (int i = 1; i <= children.size(); i++) {
			Struct tmp = Caster.toStruct(children.get(i, null), null);
			if (tmp == null) continue;

			String n = ConfigWebUtil.getAsString("name", tmp, "");
			if (name.equalsIgnoreCase(n)) {
				el = tmp;
				break;
			}
		}
		// Insert
		if (el == null) {
			el = new StructImpl(Struct.TYPE_LINKED);
			children.appendEL(el);
			el.setEL("name", name);
		}

		el.setEL("level", LogUtil.levelToString(level, ""));
		setClass(el, null, "appender-", appenderCD);
		el.setEL("appender-arguments", toStringCSSStyle(appenderArgs));
		setClass(el, null, "layout-", layoutCD);
		el.setEL("layout-arguments", toStringCSSStyle(layoutArgs));

		if (el.containsKey("appender")) rem(el, "appender");
		if (el.containsKey("layout")) rem(el, "layout");
	}

	public void updateCompilerSettings(Boolean dotNotationUpperCase, Boolean suppressWSBeforeArg, Boolean nullSupport, Boolean handleUnQuotedAttrValueAsString,
			Integer externalizeStringGTE) throws PageException {

		Struct element = _getRootElement("compiler");

		checkWriteAccess();
		if (dotNotationUpperCase == null) {
			if (element.containsKey("dot-notation-upper-case")) rem(element, "dot-notation-upper-case");
		}
		else {
			element.setEL("dot-notation-upper-case", Caster.toString(dotNotationUpperCase));
		}

		// remove old settings
		if (element.containsKey("supress-ws-before-arg")) rem(element, "supress-ws-before-arg");

		if (suppressWSBeforeArg == null) {
			if (element.containsKey("suppress-ws-before-arg")) rem(element, "suppress-ws-before-arg");
		}
		else {
			element.setEL("suppress-ws-before-arg", Caster.toString(suppressWSBeforeArg));
		}

		// full null support
		if (nullSupport == null) {
			if (element.containsKey("full-null-support")) rem(element, "full-null-support");
		}
		else {
			element.setEL("full-null-support", Caster.toString(nullSupport));
		}

		// externalize-string-gte
		if (externalizeStringGTE == null) {
			if (element.containsKey("externalize-string-gte")) rem(element, "externalize-string-gte");
		}
		else {
			element.setEL("externalize-string-gte", Caster.toString(externalizeStringGTE));
		}

		// handle Unquoted Attribute Values As String
		if (handleUnQuotedAttrValueAsString == null) {
			if (element.containsKey("handle-unquoted-attribute-value-as-string")) rem(element, "handle-unquoted-attribute-value-as-string");
		}
		else {
			element.setEL("handle-unquoted-attribute-value-as-string", Caster.toString(handleUnQuotedAttrValueAsString));
		}

	}

	Resource[] updateWebContexts(InputStream is, String realpath, boolean closeStream, boolean store)
			throws PageException, IOException, SAXException, BundleException, ConverterException {
		List<Resource> filesDeployed = new ArrayList<Resource>();

		if (config instanceof ConfigWeb) {
			ConfigAdmin._updateContextClassic(config, is, realpath, closeStream, filesDeployed);
		}
		else ConfigAdmin._updateWebContexts(config, is, realpath, closeStream, filesDeployed, store);

		return filesDeployed.toArray(new Resource[filesDeployed.size()]);
	}

	private static void _updateWebContexts(Config config, InputStream is, String realpath, boolean closeStream, List<Resource> filesDeployed, boolean store)
			throws PageException, IOException, SAXException, BundleException, ConverterException {
		if (!(config instanceof ConfigServer)) throw new ApplicationException("Invalid context, you can only call this method from server context");
		ConfigServer cs = (ConfigServer) config;

		Resource wcd = cs.getConfigDir().getRealResource("web-context-deployment");
		Resource trg = wcd.getRealResource(realpath);
		if (trg.exists()) trg.remove(true);
		Resource p = trg.getParentResource();
		if (!p.isDirectory()) p.createDirectory(true);
		IOUtil.copy(is, trg.getOutputStream(false), closeStream, true);
		filesDeployed.add(trg);
		if (store) _storeAndReload((ConfigPro) config);
	}

	Resource[] updateConfigs(InputStream is, String realpath, boolean closeStream, boolean store)
			throws PageException, IOException, SAXException, BundleException, ConverterException {
		List<Resource> filesDeployed = new ArrayList<Resource>();
		_updateConfigs(config, is, realpath, closeStream, filesDeployed, store);
		return filesDeployed.toArray(new Resource[filesDeployed.size()]);
	}

	private static void _updateConfigs(Config config, InputStream is, String realpath, boolean closeStream, List<Resource> filesDeployed, boolean store)
			throws PageException, IOException, SAXException, BundleException, ConverterException {
		Resource configs = config.getConfigDir(); // MUST get that dynamically
		Resource trg = configs.getRealResource(realpath);
		if (trg.exists()) trg.remove(true);
		Resource p = trg.getParentResource();
		if (!p.isDirectory()) p.createDirectory(true);
		IOUtil.copy(is, trg.getOutputStream(false), closeStream, true);
		filesDeployed.add(trg);
		if (store) _storeAndReload((ConfigPro) config);
	}

	Resource[] updateComponent(InputStream is, String realpath, boolean closeStream, boolean store)
			throws PageException, IOException, SAXException, BundleException, ConverterException {
		List<Resource> filesDeployed = new ArrayList<Resource>();
		_updateComponent(config, is, realpath, closeStream, filesDeployed, store);
		return filesDeployed.toArray(new Resource[filesDeployed.size()]);
	}

	private static void _updateComponent(Config config, InputStream is, String realpath, boolean closeStream, List<Resource> filesDeployed, boolean store)
			throws PageException, IOException, SAXException, BundleException, ConverterException {
		Resource comps = config.getConfigDir().getRealResource("components"); // MUST get that dynamically
		Resource trg = comps.getRealResource(realpath);
		if (trg.exists()) trg.remove(true);
		Resource p = trg.getParentResource();
		if (!p.isDirectory()) p.createDirectory(true);
		IOUtil.copy(is, trg.getOutputStream(false), closeStream, true);
		filesDeployed.add(trg);
		if (store) _storeAndReload((ConfigPro) config);
	}

	Resource[] updateContext(InputStream is, String realpath, boolean closeStream, boolean store)
			throws PageException, IOException, SAXException, BundleException, ConverterException {
		List<Resource> filesDeployed = new ArrayList<Resource>();
		_updateContext(config, is, realpath, closeStream, filesDeployed, store);
		return filesDeployed.toArray(new Resource[filesDeployed.size()]);
	}

	private static void _updateContext(Config config, InputStream is, String realpath, boolean closeStream, List<Resource> filesDeployed, boolean store)
			throws PageException, IOException, SAXException, BundleException, ConverterException {
		Resource trg = config.getConfigDir().getRealResource("context").getRealResource(realpath);
		if (trg.exists()) trg.remove(true);
		Resource p = trg.getParentResource();
		if (!p.isDirectory()) p.createDirectory(true);
		IOUtil.copy(is, trg.getOutputStream(false), closeStream, true);
		filesDeployed.add(trg);
		if (store) _storeAndReload((ConfigPro) config);
	}

	@Deprecated
	static Resource[] updateContextClassic(ConfigPro config, InputStream is, String realpath, boolean closeStream)
			throws PageException, IOException, SAXException, BundleException, ConverterException {
		List<Resource> filesDeployed = new ArrayList<Resource>();
		ConfigAdmin._updateContextClassic(config, is, realpath, closeStream, filesDeployed);
		return filesDeployed.toArray(new Resource[filesDeployed.size()]);
	}

	@Deprecated
	private static void _updateContextClassic(Config config, InputStream is, String realpath, boolean closeStream, List<Resource> filesDeployed)
			throws PageException, IOException, SAXException, BundleException, ConverterException {
		if (config instanceof ConfigServer) {
			ConfigWeb[] webs = ((ConfigServer) config).getConfigWebs();
			if (webs.length == 0) return;
			if (webs.length == 1) {
				_updateContextClassic(webs[0], is, realpath, closeStream, filesDeployed);
				return;
			}
			try {
				byte[] barr = IOUtil.toBytes(is);
				for (int i = 0; i < webs.length; i++) {
					_updateContextClassic(webs[i], new ByteArrayInputStream(barr), realpath, true, filesDeployed);
				}
			}
			finally {
				if (closeStream) IOUtil.close(is);
			}
			return;
		}

		// ConfigWeb
		Resource trg = config.getConfigDir().getRealResource("context").getRealResource(realpath);
		if (trg.exists()) trg.remove(true);
		Resource p = trg.getParentResource();
		if (!p.isDirectory()) p.createDirectory(true);
		IOUtil.copy(is, trg.getOutputStream(false), closeStream, true);
		filesDeployed.add(trg);
		_storeAndReload((ConfigPro) config);
	}

	public boolean removeConfigs(Config config, boolean store, String... realpathes) throws PageException, IOException, SAXException, BundleException, ConverterException {
		if (ArrayUtil.isEmpty(realpathes)) return false;
		boolean force = false;
		for (int i = 0; i < realpathes.length; i++) {
			if (_removeConfigs(config, realpathes[i], store)) force = true;
		}
		return force;
	}

	private boolean _removeConfigs(Config config, String realpath, boolean _store) throws PageException, IOException, SAXException, BundleException, ConverterException {

		Resource context = config.getConfigDir(); // MUST get dyn
		Resource trg = context.getRealResource(realpath);
		if (trg.exists()) {
			trg.remove(true);
			if (_store) ConfigAdmin._storeAndReload((ConfigPro) config);
			ResourceUtil.removeEmptyFolders(context, null);
			return true;
		}
		return false;
	}

	public boolean removeComponents(Config config, boolean store, String... realpathes) throws PageException, IOException, SAXException, BundleException, ConverterException {
		if (ArrayUtil.isEmpty(realpathes)) return false;
		boolean force = false;
		for (int i = 0; i < realpathes.length; i++) {
			if (_removeComponent(config, realpathes[i], store)) force = true;
		}
		return force;
	}

	private boolean _removeComponent(Config config, String realpath, boolean _store) throws PageException, IOException, SAXException, BundleException, ConverterException {

		Resource context = config.getConfigDir().getRealResource("components"); // MUST get dyn
		Resource trg = context.getRealResource(realpath);
		if (trg.exists()) {
			trg.remove(true);
			if (_store) ConfigAdmin._storeAndReload((ConfigPro) config);
			ResourceUtil.removeEmptyFolders(context, null);
			return true;
		}
		return false;
	}

	public boolean removeContext(Config config, boolean store, Log logger, String... realpathes)
			throws PageException, IOException, SAXException, BundleException, ConverterException {
		if (ArrayUtil.isEmpty(realpathes)) return false;
		boolean force = false;
		for (int i = 0; i < realpathes.length; i++) {
			logger.log(Log.LEVEL_INFO, "extension", "remove " + realpathes[i]);
			if (_removeContext(config, realpathes[i], store)) force = true;
		}
		return force;
	}

	private boolean _removeContext(Config config, String realpath, boolean _store) throws PageException, IOException, SAXException, BundleException, ConverterException {

		Resource context = config.getConfigDir().getRealResource("context");
		Resource trg = context.getRealResource(realpath);
		if (trg.exists()) {
			trg.remove(true);
			if (_store) ConfigAdmin._storeAndReload((ConfigPro) config);
			ResourceUtil.removeEmptyFolders(context, null);
			return true;
		}
		return false;
	}

	public boolean removeWebContexts(Config config, boolean store, Log logger, String... realpathes)
			throws PageException, IOException, SAXException, BundleException, ConverterException {
		if (ArrayUtil.isEmpty(realpathes)) return false;

		if (config instanceof ConfigWeb) {
			return removeContext(config, store, logger, realpathes);
		}

		boolean force = false;
		for (int i = 0; i < realpathes.length; i++) {
			logger.log(Log.LEVEL_INFO, "extension", "Remove Context [" + realpathes[i] + "]");
			if (_removeWebContexts(config, realpathes[i], store)) force = true;
		}
		return force;
	}

	private boolean _removeWebContexts(Config config, String realpath, boolean _store) throws PageException, IOException, SAXException, BundleException, ConverterException {

		if (config instanceof ConfigServer) {
			ConfigServer cs = ((ConfigServer) config);

			// remove files from deploy folder
			Resource deploy = cs.getConfigDir().getRealResource("web-context-deployment");
			Resource trg = deploy.getRealResource(realpath);

			if (trg.exists()) {
				trg.remove(true);
				ResourceUtil.removeEmptyFolders(deploy, null);
			}

			// remove files from lucee web context
			boolean store = false;
			ConfigWeb[] webs = cs.getConfigWebs();
			for (int i = 0; i < webs.length; i++) {
				if (_removeContext(webs[i], realpath, _store)) {
					store = true;
				}
			}
			return store;
		}
		return false;
	}

	Resource[] updateApplication(InputStream is, String realpath, boolean closeStream) throws PageException, IOException, SAXException {
		List<Resource> filesDeployed = new ArrayList<Resource>();
		Resource dir;
		// server context
		if (config instanceof ConfigServer) dir = config.getConfigDir().getRealResource("web-deployment");
		// if web context we simply deploy to that webcontext, that's all
		else dir = config.getRootDirectory();

		deployFilesFromStream(config, dir, is, realpath, closeStream, filesDeployed);

		return filesDeployed.toArray(new Resource[filesDeployed.size()]);
	}

	private static void deployFilesFromStream(Config config, Resource root, InputStream is, String realpath, boolean closeStream, List<Resource> filesDeployed)
			throws PageException, IOException, SAXException {
		// MUST this makes no sense at this point
		if (config instanceof ConfigServer) {
			ConfigWeb[] webs = ((ConfigServer) config).getConfigWebs();
			if (webs.length == 0) return;
			if (webs.length == 1) {
				deployFilesFromStream(webs[0], root, is, realpath, closeStream, filesDeployed);
				return;
			}
			try {
				byte[] barr = IOUtil.toBytes(is);
				for (int i = 0; i < webs.length; i++) {
					deployFilesFromStream(webs[i], root, new ByteArrayInputStream(barr), realpath, true, filesDeployed);
				}
			}
			finally {
				if (closeStream) IOUtil.close(is);
			}
			return;
		}

		// ConfigWeb
		Resource trg = root.getRealResource(realpath);
		if (trg.exists()) trg.remove(true);
		Resource p = trg.getParentResource();
		if (!p.isDirectory()) p.createDirectory(true);
		IOUtil.copy(is, trg.getOutputStream(false), closeStream, true);
		filesDeployed.add(trg);
	}

	private void removePlugins(Config config, Log logger, String[] realpathes) throws PageException, IOException, SAXException {
		if (ArrayUtil.isEmpty(realpathes)) return;
		for (int i = 0; i < realpathes.length; i++) {
			logger.log(Log.LEVEL_INFO, "extension", "Remove plugin [" + realpathes[i] + "]");
			removeFiles(config, ((ConfigPro) config).getPluginDirectory(), realpathes[i]);
		}
	}

	private void removeApplications(Config config, Log logger, String[] realpathes) throws PageException, IOException, SAXException {
		if (ArrayUtil.isEmpty(realpathes)) return;
		for (int i = 0; i < realpathes.length; i++) {
			logger.log(Log.LEVEL_INFO, "extension", "Remove application [" + realpathes[i] + "]");
			removeFiles(config, config.getRootDirectory(), realpathes[i]);
		}
	}

	private void removeFiles(Config config, Resource root, String realpath) throws PageException, IOException, SAXException {
		if (config instanceof ConfigServer) {
			ConfigWeb[] webs = ((ConfigServer) config).getConfigWebs();
			for (int i = 0; i < webs.length; i++) {
				removeFiles(webs[i], root, realpath);
			}
			return;
		}

		// ConfigWeb
		Resource trg = root.getRealResource(realpath);
		if (trg.exists()) trg.remove(true);
	}

	public static void removeRHExtensions(ConfigPro config, String[] extensionIDs, boolean removePhysical)
			throws IOException, PageException, SAXException, BundleException, ConverterException {
		ConfigAdmin admin = new ConfigAdmin(config, null);

		Map<String, BundleDefinition> oldMap = new HashMap<>();
		BundleDefinition[] bds;
		for (String extensionID: extensionIDs) {
			try {
				bds = admin._removeExtension(config, extensionID, removePhysical);
				if (bds != null) {
					for (BundleDefinition bd: bds) {
						if (bd == null) continue;// TODO why are they Null?
						oldMap.put(bd.toString(), bd);
					}
				}
			}
			catch (Exception e) {
				LogUtil.log(config, "deploy", ConfigAdmin.class.getName(), e);
			}
		}

		admin._storeAndReload();

		if (!oldMap.isEmpty() && config instanceof ConfigServer) {
			ConfigServer cs = (ConfigServer) config;
			ConfigWeb[] webs = cs.getConfigWebs();
			for (int i = 0; i < webs.length; i++) {
				try {
					admin._storeAndReload((ConfigPro) webs[i]);
				}
				catch (Exception e) {
					LogUtil.log(config, "deploy", ConfigAdmin.class.getName(), e);
				}
			}
		}
		cleanBundles(null, config, oldMap.values().toArray(new BundleDefinition[oldMap.size()])); // clean after populating the new ones

	}

	public BundleDefinition[] _removeExtension(ConfigPro config, String extensionID, boolean removePhysical)
			throws IOException, PageException, SAXException, BundleException, ConverterException {
		if (!Decision.isUUId(extensionID)) throw new IOException("id [" + extensionID + "] is invalid, it has to be a UUID");

		Array children = ConfigWebUtil.getAsArray("extensions", "rhextension", root);

		// Update
		Struct el;
		String id;
		String[] arr;
		boolean storeChildren = false;
		BundleDefinition[] bundles;
		Log log = config.getLog("deploy");
		for (int i = children.size(); i > 0; i--) {
			el = Caster.toStruct(children.get(i, null), null);
			if (el == null) continue;

			id = ConfigWebUtil.getAsString("id", el, null);
			if (extensionID.equalsIgnoreCase(id)) {
				bundles = RHExtension.toBundleDefinitions(ConfigWebUtil.getAsString("bundles", el, null)); // get existing bundles before populate new ones

				// bundles
				arr = _removeExtensionCheckOtherUsage(children, el, "bundles");
				// removeBundles(arr,removePhysical);
				// flds
				arr = _removeExtensionCheckOtherUsage(children, el, "flds");
				removeFLDs(log, arr);
				// tlds
				arr = _removeExtensionCheckOtherUsage(children, el, "tlds");
				removeTLDs(log, arr);
				// contexts
				arr = _removeExtensionCheckOtherUsage(children, el, "contexts");
				storeChildren = removeContext(config, false, log, arr);

				// webcontexts
				arr = _removeExtensionCheckOtherUsage(children, el, "webcontexts");
				storeChildren = removeWebContexts(config, false, log, arr);

				// applications
				arr = _removeExtensionCheckOtherUsage(children, el, "applications");
				removeApplications(config, log, arr);

				// components
				arr = _removeExtensionCheckOtherUsage(children, el, "components");
				removeComponents(config, false, arr);

				// configs
				arr = _removeExtensionCheckOtherUsage(children, el, "config");
				removeConfigs(config, false, arr);

				// plugins
				arr = _removeExtensionCheckOtherUsage(children, el, "plugins");
				removePlugins(config, log, arr);
				children.removeEL(i);

				return bundles;
			}
		}
		return null;
	}

	public static void cleanBundles(RHExtension rhe, ConfigPro config, BundleDefinition[] candiatesToRemove) throws BundleException, ApplicationException, IOException {
		if (ArrayUtil.isEmpty(candiatesToRemove)) return;

		BundleCollection coreBundles = ConfigWebUtil.getEngine(config).getBundleCollection();

		// core master
		_cleanBundles(candiatesToRemove, coreBundles.core.getSymbolicName(), coreBundles.core.getVersion());

		// core slaves
		Iterator<Bundle> it = coreBundles.getSlaves();
		Bundle b;
		while (it.hasNext()) {
			b = it.next();
			_cleanBundles(candiatesToRemove, b.getSymbolicName(), b.getVersion());
		}

		// all extension
		Iterator<RHExtension> itt = config.getAllRHExtensions().iterator();
		RHExtension _rhe;
		while (itt.hasNext()) {
			_rhe = itt.next();
			if (rhe != null && rhe.equals(_rhe)) continue;
			BundleInfo[] bundles = _rhe.getBundles(null);
			if (bundles != null) {
				for (BundleInfo bi: bundles) {
					_cleanBundles(candiatesToRemove, bi.getSymbolicName(), bi.getVersion());
				}
			}
		}

		// now we only have BundlesDefs in the array no longer used
		for (BundleDefinition ctr: candiatesToRemove) {
			if (ctr != null) OSGiUtil.removeLocalBundleSilently(ctr.getName(), ctr.getVersion(), null, true);
		}
	}

	private static void _cleanBundles(BundleDefinition[] candiatesToRemove, String name, Version version) {
		BundleDefinition bd;
		for (int i = 0; i < candiatesToRemove.length; i++) {
			bd = candiatesToRemove[i];

			if (bd != null && name.equalsIgnoreCase(bd.getName())) {
				if (version == null) {
					if (bd.getVersion() == null) candiatesToRemove[i] = null; // remove that from array
				}
				else if (bd.getVersion() != null && version.equals(bd.getVersion())) {
					candiatesToRemove[i] = null; // remove that from array
				}
			}
		}
	}

	private String[] _removeExtensionCheckOtherUsage(Array children, Struct curr, String type) {
		String currVal = ConfigWebUtil.getAsString(type, curr, null);
		if (StringUtil.isEmpty(currVal)) return null;

		String otherVal;
		Struct other;
		Set<String> currSet = ListUtil.toSet(ListUtil.trimItems(ListUtil.listToStringArray(currVal, ',')));
		String[] otherArr;
		for (int i = children.size(); i > 0; i--) {
			Struct tmp = Caster.toStruct(children.get(i, null), null);
			if (tmp == null) continue;

			other = tmp;
			if (other == curr) continue;
			otherVal = ConfigWebUtil.getAsString(type, other, null);
			if (StringUtil.isEmpty(otherVal)) continue;
			otherArr = ListUtil.trimItems(ListUtil.listToStringArray(otherVal, ','));
			for (int y = 0; y < otherArr.length; y++) {
				currSet.remove(otherArr[y]);
			}
		}
		return currSet.toArray(new String[currSet.size()]);
	}

	/**
	 * 
	 * @param config
	 * @param ext
	 * @return the bundles used before when this was a update, if it is a new extension then null is
	 *         returned
	 * @throws IOException
	 * @throws BundleException
	 * @throws ApplicationException
	 */
	public BundleDefinition[] _updateExtension(ConfigPro config, RHExtension ext) throws IOException, BundleException, ApplicationException {
		if (!Decision.isUUId(ext.getId())) throw new IOException("id [" + ext.getId() + "] is invalid, it has to be a UUID");

		Array children = ConfigWebUtil.getAsArray("extensions", "rhextension", root);
		// Struct extensions = _getRootElement("extensions");
		// Element[] children = ConfigWebFactory.getChildren(extensions, "rhextension");//
		// LuceeHandledExtensions

		// Update
		Struct el;
		String id;
		BundleDefinition[] old;
		for (int i = 1; i <= children.size(); i++) {
			el = Caster.toStruct(children.get(i, null), null);
			if (el == null) continue;

			id = ConfigWebUtil.getAsString("id", el, "");
			if (ext.getId().equalsIgnoreCase(id)) {
				old = RHExtension.toBundleDefinitions(ConfigWebUtil.getAsString("bundles", el, null)); // get existing bundles before populate new ones
				ext.populate(el);
				old = minus(old, OSGiUtil.toBundleDefinitions(ext.getBundles()));
				return old;
			}
		}

		// Insert
		el = new StructImpl(Struct.TYPE_LINKED);
		ext.populate(el);
		children.appendEL(el);
		return null;
	}

	private BundleDefinition[] minus(BundleDefinition[] oldBD, BundleDefinition[] newBD) {
		List<BundleDefinition> list = new ArrayList<>();
		boolean has;
		for (BundleDefinition o: oldBD) {
			has = false;
			for (BundleDefinition n: newBD) {
				if (o.equals(n)) {
					has = true;
					break;
				}
			}
			if (!has) list.add(o);
		}
		return list.toArray(new BundleDefinition[list.size()]);
	}

	private RHExtension getRHExtension(ConfigPro config, String id, RHExtension defaultValue) {
		Array children = ConfigWebUtil.getAsArray("extensions", "rhextension", root);

		if (children != null) {
			for (int i = 1; i <= children.size(); i++) {
				Struct tmp = Caster.toStruct(children.get(i, null), null);
				if (tmp == null) continue;

				String v = ConfigWebUtil.getAsString("id", tmp, null);
				if (!id.equals(v)) continue;

				try {
					return new RHExtension(config, tmp);
				}
				catch (Exception e) {
					return defaultValue;
				}
			}
		}
		return defaultValue;
	}

	/**
	 * returns the version if the extension is available
	 * 
	 * @param config
	 * @param id
	 * @return
	 * @throws PageException
	 * @throws IOException
	 * @throws SAXException
	 */
	public static RHExtension hasRHExtensions(ConfigPro config, ExtensionDefintion ed) throws PageException, SAXException, IOException {
		ConfigAdmin admin = new ConfigAdmin(config, null);
		return admin._hasRHExtensions(config, ed);
	}

	private RHExtension _hasRHExtensions(ConfigPro config, ExtensionDefintion ed) throws PageException {

		Array children = ConfigWebUtil.getAsArray("extensions", "rhextension", root);
		RHExtension tmp;
		try {
			for (int i = 1; i <= children.size(); i++) {
				Struct sct = Caster.toStruct(children.get(i, null), null);
				if (sct == null) continue;

				tmp = null;
				try {
					tmp = new RHExtension(config, sct);
				}
				catch (Exception e) {}

				if (tmp != null && ed.equals(tmp)) return tmp;
			}
			return null;
		}
		catch (Exception e) {
			throw Caster.toPageException(e);
		}
	}

	public void updateAuthKey(String key) throws PageException {
		checkWriteAccess();
		key = key.trim();

		// merge new key and existing
		ConfigServerImpl cs = (ConfigServerImpl) config;
		String[] keys = cs.getAuthenticationKeys();
		Set<String> set = new HashSet<String>();
		for (int i = 0; i < keys.length; i++) {
			set.add(keys[i]);
		}
		set.add(key);

		root.setEL("auth-keys", authKeysAsList(set));

	}

	public void removeAuthKeys(String key) throws PageException {
		checkWriteAccess();
		key = key.trim();

		// remove key
		ConfigServerImpl cs = (ConfigServerImpl) config;
		String[] keys = cs.getAuthenticationKeys();
		Set<String> set = new HashSet<String>();
		for (int i = 0; i < keys.length; i++) {
			if (!key.equals(keys[i])) set.add(keys[i]);
		}

		root.setEL("auth-keys", authKeysAsList(set));
	}

	public void updateAPIKey(String key) throws SecurityException, ApplicationException {
		checkWriteAccess();
		key = key.trim();
		if (!Decision.isGUId(key)) throw new ApplicationException("Passed API Key [" + key + "] is not valid");
		root.setEL("api-key", key);

	}

	public void removeAPIKey() throws PageException {
		checkWriteAccess();
		if (root.containsKey("api-key")) rem(root, "api-key");
	}

	private String authKeysAsList(Set<String> set) throws PageException {
		StringBuilder sb = new StringBuilder();
		Iterator<String> it = set.iterator();
		String key;
		while (it.hasNext()) {
			key = it.next().trim();
			if (sb.length() > 0) sb.append(',');
			try {
				sb.append(URLEncoder.encode(key, "UTF-8"));
			}
			catch (UnsupportedEncodingException e) {
				throw Caster.toPageException(e);
			}
		}
		return sb.toString();
	}

	Resource[] updatePlugin(InputStream is, String realpath, boolean closeStream) throws PageException, IOException, SAXException {
		List<Resource> filesDeployed = new ArrayList<Resource>();
		deployFilesFromStream(config, config.getPluginDirectory(), is, realpath, closeStream, filesDeployed);
		return filesDeployed.toArray(new Resource[filesDeployed.size()]);
	}

	public void updatePlugin(PageContext pc, Resource src) throws PageException, IOException {
		// convert to a directory when it is a zip
		if (!src.isDirectory()) {
			if (!IsZipFile.invoke(src))
				throw new ApplicationException("Path [" + src.getAbsolutePath() + "] is invalid, it has to be a path to an existing zip file or a directory containing a plugin");
			src = ResourceUtil.toResourceExisting(pc, "zip://" + src.getAbsolutePath());
		}
		String name = ResourceUtil.getName(src.getName());
		if (!PluginFilter.doAccept(src)) throw new ApplicationException("Plugin [" + src.getAbsolutePath() + "] is invalid, missing one of the following files [Action."
				+ Constants.getCFMLComponentExtension() + " or Action." + Constants.getLuceeComponentExtension() + ",language.xml] in root, existing files are ["
				+ lucee.runtime.type.util.ListUtil.arrayToList(src.list(), ", ") + "]");

		Resource dir = config.getPluginDirectory();
		Resource trgDir = dir.getRealResource(name);
		if (trgDir.exists()) {
			trgDir.remove(true);
		}

		ResourceUtil.copyRecursive(src, trgDir);
	}

	private static void setClass(Struct el, Class instanceOfClass, String prefix, ClassDefinition cd) throws PageException {
		if (cd == null || StringUtil.isEmpty(cd.getClassName())) return;

		// validate class
		try {
			Class clazz = cd.getClazz();

			if (instanceOfClass != null && !Reflector.isInstaneOf(clazz, instanceOfClass, false))
				throw new ApplicationException("Class [" + clazz.getName() + "] is not of type [" + instanceOfClass.getName() + "]");
		}
		catch (Exception e) {
			throw Caster.toPageException(e);
		}
		el.setEL(prefix + "class", cd.getClassName().trim());
		if (cd.isBundle()) {
			el.setEL(prefix + "bundle-name", cd.getName());
			if (cd.hasVersion()) el.setEL(prefix + "bundle-version", cd.getVersionAsString());
		}
		else {
			if (el.containsKey(prefix + "bundle-name")) el.remove(prefix + "bundle-name");
			if (el.containsKey(prefix + "bundle-version")) el.remove(prefix + "bundle-version");
		}
	}

	private void removeClass(Struct el, String prefix) {
		el.removeEL(KeyImpl.init(prefix + "class"));
		el.removeEL(KeyImpl.init(prefix + "bundle-name"));
		el.removeEL(KeyImpl.init(prefix + "bundle-version"));
	}

	public final static class PluginFilter implements ResourceFilter {
		@Override
		public boolean accept(Resource res) {
			return doAccept(res);
		}

		public static boolean doAccept(Resource res) {
			return res.isDirectory() && (res.getRealResource("/Action." + Constants.getCFMLComponentExtension()).isFile()
					|| res.getRealResource("/Action." + Constants.getLuceeComponentExtension()).isFile()) && res.getRealResource("/language.xml").isFile();
		}

	}

	public void updateQueue(Integer max, Integer timeout, Boolean enable) throws SecurityException {
		checkWriteAccess();
		boolean hasAccess = ConfigWebUtil.hasAccess(config, SecurityManager.TYPE_SETTING);
		if (!hasAccess) throw new SecurityException("Accces Denied to update queue settings");

		Struct queue = _getRootElement("queue");
		// max
		if (max == null) rem(queue, "max");
		else queue.setEL("max", Caster.toString(max, ""));
		// total
		if (timeout == null) rem(queue, "timeout");
		else queue.setEL("timeout", Caster.toString(timeout, ""));
		// enable
		if (enable == null) rem(queue, "enable");
		else queue.setEL("enable", Caster.toString(enable, ""));
	}

	public void updateCGIReadonly(Boolean cgiReadonly) throws SecurityException {
		checkWriteAccess();
		boolean hasAccess = ConfigWebUtil.hasAccess(config, SecurityManager.TYPE_SETTING);
		if (!hasAccess) throw new SecurityException("Accces Denied to update scope setting");

		Struct scope = _getRootElement("scope");
		scope.setEL("cgi-readonly", Caster.toString(cgiReadonly, ""));
	}
}
