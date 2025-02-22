package io.mosip.registration.update;

import static io.mosip.registration.constants.RegistrationConstants.APPLICATION_ID;
import static io.mosip.registration.constants.RegistrationConstants.APPLICATION_NAME;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.util.*;
import java.util.Map.Entry;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import io.mosip.kernel.core.util.HMACUtils2;
import io.mosip.registration.context.ApplicationContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import io.mosip.kernel.core.exception.ExceptionUtils;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.kernel.core.util.DateUtils;
import io.mosip.kernel.core.util.FileUtils;
import io.mosip.registration.config.AppConfig;
import io.mosip.registration.constants.LoggerConstants;
import io.mosip.registration.constants.RegistrationConstants;
import io.mosip.registration.dto.ResponseDTO;
import io.mosip.registration.dto.VersionMappings;
import io.mosip.registration.exception.RegBaseCheckedException;
import io.mosip.registration.service.BaseService;
import io.mosip.registration.service.config.GlobalParamService;

/**
 * This class will update the application based on comapring the versions of the
 * jars from the Manifest. The comparison will be done by comparing the Local
 * Manifest and the meta-inf.xml file. If there is any updation available in the
 * jar then the new jar gets downloaded and the old gets archived.
 * 
 * @author YASWANTH S
 *
 */
@Component
public class SoftwareUpdateHandler extends BaseService {

	/**
	 * Instance of {@link Logger}
	 */
	private static final Logger LOGGER = AppConfig.getLogger(SoftwareUpdateHandler.class);
	private static final String SLASH = "/";
	private static final String manifestFile = "MANIFEST.MF";
	private static final String libFolder = "lib/";
	private static final String binFolder = "bin/";
	private static final String dbFolder = "db";
	private static final String lastUpdatedTag = "lastUpdated";
	private static final String SQL = "sql";
	private static final String exectionSqlFile = "initial_db_scripts.sql";
	private static final String rollBackSqlFile = "rollback_scripts.sql";
	private static final String mosip = "mosip";
	private static final String versionTag = "version";
	private static final String MOSIP_SERVICES = "mosip-services.jar";
	private static final String MOSIP_CLIENT = "mosip-client.jar";
	private static final String FEATURE = "http://apache.org/xml/features/disallow-doctype-decl";
	private static final String EXTERNAL_DTD_FEATURE = "http://apache.org/xml/features/nonvalidating/load-external-dtd";

	private static final String CONNECTION_TIMEOUT = "mosip.registration.sw.file.download.connection.timeout";

	private static final String READ_TIMEOUT = "mosip.registration.sw.file.download.read.timeout";

	private static Map<String, String> CHECKSUM_MAP;
	private String currentVersion;
	private String latestVersion;
	private Manifest localManifest;
	private Manifest serverManifest;
	private String latestVersionReleaseTimestamp;

	@Value("${mosip.reg.rollback.path}")
	private String backUpPath;

	@Value("${mosip.reg.client.url}")
	private String serverRegClientURL;

	@Value("${mosip.reg.xml.file.url}")
	private String serverMosipXmlFileUrl;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private GlobalParamService globalParamService;


	/**
	 * It will check whether any software updates are available or not.
	 * <p>
	 * The check will be done by comparing the Local Manifest file version with the
	 * version of the server meta-inf.xml file
	 * </p>
	 * 
	 * @return Boolean true - If there is any update available. false - If no
	 *         updates available
	 */
	public boolean hasUpdate() {

		LOGGER.info(LoggerConstants.LOG_REG_UPDATE, APPLICATION_NAME, APPLICATION_ID, "Checking for updates from " +
				ApplicationContext.getUpgradeServerURL());
		try {
			return !getCurrentVersion().equals(getLatestVersion());
		} catch (IOException | ParserConfigurationException | SAXException | RuntimeException exception) {
			LOGGER.error(LoggerConstants.LOG_REG_LOGIN, APPLICATION_NAME, APPLICATION_ID,
					exception.getMessage() + ExceptionUtils.getStackTrace(exception));
			return false;
		}

	}

	/**
	 * 
	 * @return Returns the current version which is read from the server meta-inf
	 *         file.
	 * @throws IOException
	 * @throws ParserConfigurationException
	 * @throws SAXException
	 */
	private String getLatestVersion() throws IOException, ParserConfigurationException, SAXException {
		LOGGER.info(LoggerConstants.LOG_REG_UPDATE, APPLICATION_NAME, APPLICATION_ID,
				"Checking for latest version started");
		// Get latest version using meta-inf.xml
		DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
		documentBuilderFactory.setFeature(FEATURE, true);
		documentBuilderFactory.setFeature(EXTERNAL_DTD_FEATURE, false);
		documentBuilderFactory.setXIncludeAware(false);
		documentBuilderFactory.setExpandEntityReferences(false);
		DocumentBuilder db = documentBuilderFactory.newDocumentBuilder();
		org.w3c.dom.Document metaInfXmlDocument = db.parse(getInputStreamOf(getURL(serverMosipXmlFileUrl)));

		setLatestVersion(getElementValue(metaInfXmlDocument, versionTag));
		setLatestVersionReleaseTimestamp(getElementValue(metaInfXmlDocument, lastUpdatedTag));

		LOGGER.info(LoggerConstants.LOG_REG_UPDATE, APPLICATION_NAME, APPLICATION_ID,
				"Checking for latest version completed");
		return latestVersion;
	}

	private String getElementValue(Document metaInfXmlDocument, String tagName) {
		NodeList list = metaInfXmlDocument.getDocumentElement().getElementsByTagName(tagName);
		String val = null;
		if (list != null && list.getLength() > 0) {
			NodeList subList = list.item(0).getChildNodes();

			if (subList != null && subList.getLength() > 0) {
				// Set Latest Version
				val = subList.item(0).getNodeValue();
			}
		}

		return val;

	}

	/**
	 * Get Current version of setup
	 * 
	 * @return current version
	 */
	public String getCurrentVersion() {
		LOGGER.info(LoggerConstants.LOG_REG_UPDATE, APPLICATION_NAME, APPLICATION_ID,
				"Checking for current version started : " + ApplicationContext.getUpgradeServerURL());

		// Get Local manifest file
		try {
			if (getLocalManifest() != null) {
				setCurrentVersion((String) localManifest.getMainAttributes().get(Attributes.Name.MANIFEST_VERSION));
			}
		} catch (IOException exception) {
			LOGGER.error(LoggerConstants.LOG_REG_LOGIN, APPLICATION_NAME, APPLICATION_ID,
					exception.getMessage() + ExceptionUtils.getStackTrace(exception));

		}

		LOGGER.info(LoggerConstants.LOG_REG_UPDATE, APPLICATION_NAME, APPLICATION_ID,
				"Checking for current version completed");
		return currentVersion;
	}

	/**
	 * <p>
	 * Checks whteher the update is available or not
	 * </p>
	 * <p>
	 * If the Update is available:
	 * </p>
	 * <p>
	 * If the jars needs to be added/updated in the local
	 * </p>
	 * <ul>
	 * <li>Take the back-up of the current jars</li>
	 * <li>Download the jars from the server and add/update it in the local</li>
	 * </ul>
	 * <p>
	 * If the jars needs to be deleted in the local
	 * </p>
	 * <ul>
	 * <li>Take the back-up of the current jars</li>
	 * <li>Delete that particular jar from the local</li>
	 * </ul>
	 * <p>
	 * If there is any error occurs while updation then the restoration of the jars
	 * will happen by taking the back-up jars
	 * </p>
	 * 
	 * @throws Exception
	 *             - IOException
	 */
	public void update() throws Exception {

		LOGGER.info(LoggerConstants.LOG_REG_UPDATE, APPLICATION_NAME, APPLICATION_ID,
				"Updating latest version started");
		Path backUp = null;

		try {
			String local = getLocalManifest().getMainAttributes().getValue(Attributes.Name.MANIFEST_VERSION);
			// Get Server Manifest
			getServerManifest();

			// Back Current Application
			backUp = backUpSetup(getCurrentVersion());
			// replace local manifest with Server manifest
			serverManifest.write(new FileOutputStream(manifestFile));

			/*List<String> downloadJars = new LinkedList<>();
			List<String> deletableJars = new LinkedList<>();
			List<String> checkableJars = new LinkedList<>();*/

			/*Map<String, Attributes> localAttributes = localManifest.getEntries();
			Map<String, Attributes> serverAttributes = serverManifest.getEntries();

			// Compare local and server Manifest
			for (Entry<String, Attributes> jar : localAttributes.entrySet()) {
				checkableJars.add(jar.getKey());
				if (!serverAttributes.containsKey(jar.getKey())) {

					*//* unnecessary jar after update *//*
					deletableJars.add(jar.getKey());

				} else {
					Attributes localAttribute = jar.getValue();
					Attributes serverAttribute = serverAttributes.get(jar.getKey());
					if (!localAttribute.getValue(Attributes.Name.CONTENT_TYPE)
							.equals(serverAttribute.getValue(Attributes.Name.CONTENT_TYPE))) {

						*//* Jar to be downloaded *//*
						downloadJars.add(jar.getKey());

					}

					serverManifest.getEntries().remove(jar.getKey());

				}
			}*/

			/*for (Entry<String, Attributes> jar : serverAttributes.entrySet()) {
				downloadJars.add(jar.getKey());
			}

			deleteJars(deletableJars);

			// Un-Modified jars exist or not
			checkableJars.removeAll(deletableJars);
			checkableJars.removeAll(downloadJars);*/

			getServerManifest();

			// Download latest jars if not in local
			checkJars(getLatestVersion(), serverManifest.getEntries().keySet());
			//checkJars(getLatestVersion(), checkableJars);

			setLocalManifest(serverManifest);
			setServerManifest(null);
			setLatestVersion(null);

			// Update global param of software update flag as false
			globalParamService.update(RegistrationConstants.IS_SOFTWARE_UPDATE_AVAILABLE,
					RegistrationConstants.DISABLE);
			
			Timestamp time = Timestamp.valueOf(DateUtils.getUTCCurrentDateTime());			
			globalParamService.update(RegistrationConstants.LAST_SOFTWARE_UPDATE,
					String.valueOf(time));

			globalParamService.update(RegistrationConstants.SERVICES_VERSION_KEY, local);

		} catch (RuntimeException | IOException | ParserConfigurationException | SAXException exception) {
			LOGGER.error(LoggerConstants.LOG_REG_UPDATE, APPLICATION_NAME, APPLICATION_ID,
					exception.getMessage() + ExceptionUtils.getStackTrace(exception));
			// Rollback setup
			File backUpFolder = backUp.toFile();

			rollBackSetup(backUpFolder);

			throw exception;
		}
		LOGGER.info(LoggerConstants.LOG_REG_UPDATE, APPLICATION_NAME, APPLICATION_ID,
				"Updating latest version started");
	}

	private Path backUpSetup(String currentVersion) throws io.mosip.kernel.core.exception.IOException {
		LOGGER.info(LoggerConstants.LOG_REG_UPDATE, APPLICATION_NAME, APPLICATION_ID,
				"Backup of current version started");
		Timestamp timestamp = new Timestamp(System.currentTimeMillis());
		String date = timestamp.toString().replace(":", "-") + "Z";

		File backUpFolder = new File(backUpPath + SLASH + currentVersion + "_" + date);

		// bin backup folder
		File bin = new File(backUpFolder.getAbsolutePath() + SLASH + binFolder);
		bin.mkdirs();

		// lib backup folder
		File lib = new File(backUpFolder.getAbsolutePath() + SLASH + libFolder);
		lib.mkdirs();
		
		// db backup folder
		File db = new File(backUpFolder.getAbsolutePath() + SLASH + dbFolder);
		db.mkdirs();

		// manifest backup file
		File manifest = new File(backUpFolder.getAbsolutePath() + SLASH + manifestFile);

		FileUtils.copyDirectory(new File(binFolder), bin);
		FileUtils.copyDirectory(new File(libFolder), lib);
		FileUtils.copyDirectory(new File(dbFolder), db);
		FileUtils.copyFile(new File(manifestFile), manifest);

		for (File backUpFile : new File(backUpPath).listFiles()) {
			if (!backUpFile.getAbsolutePath().equals(backUpFolder.getAbsolutePath())) {
				FileUtils.deleteDirectory(backUpFile);
			}
		}
		LOGGER.info(LoggerConstants.LOG_REG_UPDATE, APPLICATION_NAME, APPLICATION_ID,
				"Backup of current version completed");

		return backUpFolder.toPath();

	}

	private void checkJars(String version, Collection<String> checkableJars)
			throws Exception {

		String connectionTimeout = ApplicationContext.getStringValueFromApplicationMap(CONNECTION_TIMEOUT);
		String readTimeout = ApplicationContext.getStringValueFromApplicationMap(READ_TIMEOUT);

		LOGGER.info(LoggerConstants.LOG_REG_UPDATE, APPLICATION_NAME, APPLICATION_ID, "Checking of jars started");
		for (String jarFile : checkableJars) {

			String folder = jarFile.contains(mosip) ? binFolder : libFolder;

			File jarInFolder = new File(folder + jarFile);

			if (!jarInFolder.exists() || !isCheckSumValid(jarInFolder, serverManifest)) {
				download(version, jarFile, connectionTimeout, readTimeout);
				LOGGER.info("Successfully downloaded the latest file : {}", jarFile);
			}

		}

		if (version.startsWith("1.2.0")) {
			String url =  getURL(serverRegClientURL) + version + SLASH + "run_upgrade.bat";
			org.apache.commons.io.FileUtils.copyURLToFile(new URL(url), new File("run_upgrade.bat"),50000, 0);
			LOGGER.info("Successfully downloaded the upgrade bat file");
		}
		
		LOGGER.info(LoggerConstants.LOG_REG_UPDATE, APPLICATION_NAME, APPLICATION_ID, "Checking of jars completed");
	}

	protected void download(String version, String fileName, String connectionTimeout, String readTimeout) throws Exception {
		String url = getURL(serverRegClientURL) + version + SLASH + libFolder + fileName;
		LOGGER.info("invoking url : {}", url);
		try {
			if(connectionTimeout == null || connectionTimeout.equals("null") || connectionTimeout.trim().isBlank()) { connectionTimeout = "50000"; }
			if(readTimeout == null || readTimeout.equals("null") || readTimeout.trim().isBlank()) { readTimeout = "0"; }

			URL fileUrl = new URL(url);
			org.apache.commons.io.FileUtils.copyURLToFile(fileUrl, new File((fileName.contains("mosip") ? binFolder : libFolder) + File.separator + fileName),
					Integer.parseInt(connectionTimeout), Integer.parseInt(readTimeout));
			return;

		} catch (IOException e) {
			LOGGER.error("Failed to download {}", url, e);
			throw e;
		}
	}

	/*private InputStream getInputStreamOfJar(String version, String jarName) throws IOException {
		return getInputStreamOf(getURL(serverRegClientURL) + version + SLASH + libFolder + jarName);

	}*/

	private void deleteJars(List<String> deletableJars) throws io.mosip.kernel.core.exception.IOException {

		LOGGER.info(LoggerConstants.LOG_REG_UPDATE, APPLICATION_NAME, APPLICATION_ID, "Deletion of jars started");
		for (String jarName : deletableJars) {
			File deleteFile = null;

			String deleteFolder = jarName.contains(mosip) ? binFolder : libFolder;

			deleteFile = new File(deleteFolder + jarName);

			if (deleteFile.exists()) {
				// Delete Jar
				FileUtils.forceDelete(deleteFile);

			}
		}
		LOGGER.info(LoggerConstants.LOG_REG_UPDATE, APPLICATION_NAME, APPLICATION_ID, "Deletion of jars completed");

	}

	private Manifest getLocalManifest() throws IOException {
		LOGGER.info(LoggerConstants.LOG_REG_UPDATE, APPLICATION_NAME, APPLICATION_ID,
				"Getting  of local manifest started");

		File localManifestFile = new File(manifestFile);

		if (localManifestFile.exists()) {

			// Set Local Manifest
			setLocalManifest(new Manifest(new FileInputStream(localManifestFile)));

		}
		LOGGER.info(LoggerConstants.LOG_REG_UPDATE, APPLICATION_NAME, APPLICATION_ID,
				"Getting  of local manifest completed");
		return localManifest;
	}

	private Manifest getServerManifest() throws IOException, ParserConfigurationException, SAXException {

		LOGGER.info(LoggerConstants.LOG_REG_UPDATE, APPLICATION_NAME, APPLICATION_ID,
				"Geting  of server manifest started");
		// Get latest Manifest from server
		setServerManifest(
				new Manifest(getInputStreamOf(getURL(serverRegClientURL) + getLatestVersion() + SLASH + manifestFile)));
		setLatestVersion(serverManifest.getMainAttributes().getValue(Attributes.Name.MANIFEST_VERSION));

		LOGGER.info(LoggerConstants.LOG_REG_UPDATE, APPLICATION_NAME, APPLICATION_ID,
				"Geting  of server manifest completed");
		return serverManifest;

	}

	private void setLocalManifest(Manifest localManifest) {
		this.localManifest = localManifest;
	}

	private void setServerManifest(Manifest serverManifest) {
		this.serverManifest = serverManifest;
	}

	private void setCurrentVersion(String currentVersion) {
		this.currentVersion = currentVersion;
	}

	private void setLatestVersion(String latestVersion) {
		this.latestVersion = latestVersion;
	}

	private boolean isCheckSumValid(File jarFile, Manifest manifest) {
		LOGGER.info(LoggerConstants.LOG_REG_UPDATE, APPLICATION_NAME, APPLICATION_ID,
				"Checking of checksum started for jar :" + jarFile.getName());
		String checkSum;
		try {
			checkSum = HMACUtils2.digestAsPlainText(Files.readAllBytes(jarFile.toPath()));

			// Get Check sum
			String manifestCheckSum = getCheckSum(jarFile.getName(), manifest);

			LOGGER.info(LoggerConstants.LOG_REG_UPDATE, APPLICATION_NAME, APPLICATION_ID,
					"Checking of checksum completed for jar :" + jarFile.getName());
			return checkSum.equals(manifestCheckSum);

		} catch (IOException | NoSuchAlgorithmException ioException) {
			LOGGER.error(LoggerConstants.LOG_REG_UPDATE, APPLICATION_NAME, APPLICATION_ID,
					ioException.getMessage() + ExceptionUtils.getStackTrace(ioException));
			return false;
		}

	}

	private boolean hasSpace(int bytes) {

		LOGGER.info(LoggerConstants.LOG_REG_UPDATE, APPLICATION_NAME, APPLICATION_ID, "Checking of space in machine");
		return bytes < new File("/").getFreeSpace();
	}

	private InputStream getInputStreamOf(String url) throws IOException {
		URLConnection connection = new URL(url).openConnection();

		connection.setConnectTimeout(
				Integer.valueOf(getGlobalConfigValueOf(RegistrationConstants.HTTP_API_WRITE_TIMEOUT)));

		connection.setReadTimeout(Integer.valueOf(getGlobalConfigValueOf(RegistrationConstants.HTTP_API_READ_TIMEOUT)));

		// Space Check
		if (hasSpace(connection.getContentLength())) {
			return connection.getInputStream();
		} else {
			throw new IOException("No Disk Space");
		}

	}

	public void setLatestVersionReleaseTimestamp(String latestVersionReleaseTimestamp) {
		this.latestVersionReleaseTimestamp = latestVersionReleaseTimestamp;
	}

	/**
	 * The latest version timestamp will be taken from the server meta-inf.xml file.
	 * This timestamp will the be parsed in this method.
	 * 
	 * @return timestamp
	 */
	public Timestamp getLatestVersionReleaseTimestamp() {

		Calendar calendar = Calendar.getInstance();

		String dateString = latestVersionReleaseTimestamp;

		int year = Integer.valueOf(dateString.charAt(0) + "" + dateString.charAt(1) + "" + dateString.charAt(2) + ""
				+ dateString.charAt(3));
		int month = Integer.valueOf(dateString.charAt(4) + "" + dateString.charAt(5));
		int date = Integer.valueOf(dateString.charAt(6) + "" + dateString.charAt(7));
		int hourOfDay = Integer.valueOf(dateString.charAt(8) + "" + dateString.charAt(9));
		int minute = Integer.valueOf(dateString.charAt(10) + "" + dateString.charAt(11));
		int second = Integer.valueOf(dateString.charAt(12) + "" + dateString.charAt(13));

		calendar.set(year, month - 1, date, hourOfDay, minute, second);

		return new Timestamp(calendar.getTime().getTime());
	}

	/**
	 * This method will check whether any updation needs to be done in the DB
	 * structure based on previous version and the list of version-mappings.
	 * <p>
	 * If there is any updates available:
	 * </p>
	 * <p>
	 * Take the back-up of the current DB
	 * </p>
	 * <p>
	 * Run the upgrade queries from the sql files by iterating through the 
	 * version-mappings available after previous version. 
	 * The SQL files are downloaded from the server and available in the 
	 * local
	 * </p>
	 * <p>
	 * If there is any error occurs during the update,then the rollback query will
	 * run from the sql file
	 * </p>
	 * 
	 * @param previousVersion
	 *            previous version
	 * @param versionMappings 
	 * @return response of sql execution
	 * @throws IOException
	 */
	public ResponseDTO executeSqlFile(String previousVersion, Map<String, VersionMappings> versionMappings) throws IOException {
		LOGGER.info("DB-Script files execution started from previous version : {} , To Current Version : {}", previousVersion, currentVersion);
		
		ResponseDTO responseDTO = new ResponseDTO();
		
		/*
		 * Here, we are removing the entries from version-mappings map, for which, the
		 * releaseOrder is less than or equal to the previous version, because, we need
		 * to execute the upgrade scripts only for the versions released after the
		 * previous version.
		 */
		if (versionMappings.containsKey(previousVersion)) {
			Integer previousVersionReleaseOrder = versionMappings.get(previousVersion).getReleaseOrder();
			versionMappings.entrySet().removeIf(versionMapping -> versionMapping.getValue().getReleaseOrder() <= previousVersionReleaseOrder);
		}
		
		for (Entry<String, VersionMappings> entry : versionMappings.entrySet()) {
			try {
				LOGGER.info("DB Script files execution started for the version: " + entry.getKey());
				
				executeSQL(entry.getValue().getDbVersion(), previousVersion);
				previousVersion = entry.getKey();
				//Backing up the DB with ongoing upgrade version name
				backUpSetup(entry.getKey());
				// Update global param with current version
				globalParamService.update(RegistrationConstants.SERVICES_VERSION_KEY, entry.getKey());
			} catch (RegBaseCheckedException | io.mosip.kernel.core.exception.IOException exception) {
				// Prepare Error Response
				setErrorResponse(responseDTO, RegistrationConstants.SQL_EXECUTION_FAILURE, null);
				// Replace with backup
				rollback(responseDTO, previousVersion);
				return responseDTO;
			}
		}
		
		setSuccessResponse(responseDTO, RegistrationConstants.SQL_EXECUTION_SUCCESS, null);

		LOGGER.info(LoggerConstants.LOG_REG_UPDATE, APPLICATION_NAME, APPLICATION_ID,
				"DB-Script files execution completed");

		return responseDTO;
	}

	private void executeSQL(String dbVersion, String previousVersion) throws RegBaseCheckedException {
		boolean isExecutionSuccess = false;
		boolean isRollBackSuccess = false;
		try {
			LOGGER.info(LoggerConstants.LOG_REG_UPDATE, APPLICATION_NAME, APPLICATION_ID,
					"Checking Started : " + dbVersion + SLASH + exectionSqlFile);

			execute(SQL + SLASH + dbVersion + SLASH + exectionSqlFile);
			isExecutionSuccess = true;

			LOGGER.info(LoggerConstants.LOG_REG_UPDATE, APPLICATION_NAME, APPLICATION_ID,
					"Checking completed : " + dbVersion + SLASH + exectionSqlFile);
		} catch (RuntimeException | IOException runtimeException) {		
			LOGGER.error(LoggerConstants.LOG_REG_UPDATE, APPLICATION_NAME, APPLICATION_ID,
					runtimeException.getMessage() + ExceptionUtils.getStackTrace(runtimeException));			
		}
		if (!isExecutionSuccess) {
			// ROLL BACK QUERIES
			try {
				LOGGER.info(LoggerConstants.LOG_REG_UPDATE, APPLICATION_NAME, APPLICATION_ID,
						"Checking started : " + dbVersion + SLASH + rollBackSqlFile);

				execute(SQL + SLASH + dbVersion + SLASH + rollBackSqlFile);
				isRollBackSuccess = true;

				LOGGER.info(LoggerConstants.LOG_REG_UPDATE, APPLICATION_NAME, APPLICATION_ID,
						"Checking completed : " + dbVersion + SLASH + rollBackSqlFile);
			} catch (RuntimeException | IOException exception) {
				LOGGER.error(LoggerConstants.LOG_REG_UPDATE, APPLICATION_NAME, APPLICATION_ID,
						exception.getMessage() + ExceptionUtils.getStackTrace(exception));
			}

			if (!isRollBackSuccess) {
				LOGGER.info(LoggerConstants.LOG_REG_UPDATE, APPLICATION_NAME, APPLICATION_ID,
						"Trying to rollback DB from the backup folder as rollback scripts failed for the version: " + dbVersion);
				
				dbRollBackSetup(previousVersion);
			}
			throw new RegBaseCheckedException();
		}
	}

	private void execute(String path) throws IOException {
		try (InputStream inputStream = SoftwareUpdateHandler.class.getClassLoader().getResourceAsStream(path)) {
			LOGGER.info(LoggerConstants.LOG_REG_UPDATE, APPLICATION_NAME, APPLICATION_ID,
					inputStream != null ? path + " found" : path + " Not Found");

			if (inputStream != null) {
				runSqlFile(inputStream);
			}
		}
	}

	private void runSqlFile(InputStream inputStream) throws IOException {
		LOGGER.info(LoggerConstants.LOG_REG_UPDATE, APPLICATION_NAME, APPLICATION_ID, "Execution started sql file");

		try (InputStreamReader inputStreamReader = new InputStreamReader(inputStream)) {
			try (BufferedReader bufferedReader = new BufferedReader(inputStreamReader)) {
				String str;
				StringBuilder sb = new StringBuilder();
				while ((str = bufferedReader.readLine()) != null) {
					sb.append(str + "\n ");
				}
				List<String> statments = java.util.Arrays.asList(sb.toString().split(";"));
				for (String stat : statments) {
					if (!stat.trim().equals("")) {
						LOGGER.info(LoggerConstants.LOG_REG_UPDATE, APPLICATION_NAME, APPLICATION_ID,
								"Executing Statment : " + stat);

						jdbcTemplate.execute(stat);
					}
				}
			}
		}

		LOGGER.info(LoggerConstants.LOG_REG_UPDATE, APPLICATION_NAME, APPLICATION_ID, "Execution completed sql file");
	}

	private void rollBackSetup(File backUpFolder) throws io.mosip.kernel.core.exception.IOException {
		LOGGER.info(LoggerConstants.LOG_REG_UPDATE, APPLICATION_NAME, APPLICATION_ID,
				"Replacing Backup of current version started");
		// TODO Working in Ecllipse but not in zip
		/*
		 * FileUtils.copyDirectory( FileUtils.getFile(backUpFolder.getAbsolutePath() +
		 * SLASH + FilenameUtils.getName(binFolder)),
		 * FileUtils.getFile(FilenameUtils.getName(binFolder)));
		 * FileUtils.copyDirectory( FileUtils.getFile(backUpFolder.getAbsolutePath() +
		 * SLASH + FilenameUtils.getName(libFolder)),
		 * FileUtils.getFile(FilenameUtils.getName(libFolder)));
		 * FileUtils.copyFile(FileUtils.getFile(backUpFolder.getAbsolutePath()+SLASH+
		 * FilenameUtils.getName(manifestFile)),
		 * FileUtils.getFile(FilenameUtils.getName(manifestFile)));
		 */

		FileUtils.copyDirectory(new File(backUpFolder.getAbsolutePath() + SLASH + binFolder), new File(binFolder));
		FileUtils.copyDirectory(new File(backUpFolder.getAbsolutePath() + SLASH + libFolder), new File(libFolder));

		FileUtils.copyFile(new File(backUpFolder.getAbsolutePath() + SLASH + manifestFile), new File(manifestFile));
		LOGGER.info(LoggerConstants.LOG_REG_UPDATE, APPLICATION_NAME, APPLICATION_ID,
				"Replacing Backup of current version completed");
	}
	
	private void dbRollBackSetup(String previousVersion) {
		LOGGER.info(LoggerConstants.LOG_REG_UPDATE, APPLICATION_NAME, APPLICATION_ID,
				"Replacing DB backup of current version started");
		
		File file = FileUtils.getFile(backUpPath);

		LOGGER.info(LoggerConstants.LOG_REG_UPDATE, APPLICATION_NAME, APPLICATION_ID,
				"Backup Path found : " + file.exists());
		
		if (file.exists()) {
			for (File backUpFolder : file.listFiles()) {
				if (backUpFolder.getName().contains(previousVersion)) {
					try {
						FileUtils.copyDirectory(new File(backUpFolder.getAbsolutePath() + SLASH + dbFolder), new File(dbFolder));
						
						LOGGER.info(LoggerConstants.LOG_REG_UPDATE, APPLICATION_NAME, APPLICATION_ID,
								"Replacing DB backup of current version completed");
					} catch (Exception exception) {
						LOGGER.error(LoggerConstants.LOG_REG_UPDATE, APPLICATION_NAME, APPLICATION_ID,
								"Exception in backing up the DB folder: " + exception.getMessage() + ExceptionUtils.getStackTrace(exception));
					}
					break;
				}
			}
		}
	}

	private void rollback(ResponseDTO responseDTO, String previousVersion) {
		File file = FileUtils.getFile(backUpPath);

		LOGGER.info(LoggerConstants.LOG_REG_UPDATE, APPLICATION_NAME, APPLICATION_ID,
				"Backup Path found : " + file.exists());

		boolean isBackUpCompleted = false;
		if (file.exists()) {
			for (File backUpFolder : file.listFiles()) {
				if (backUpFolder.getName().contains(previousVersion)) {
					try {
						rollBackSetup(backUpFolder);
						isBackUpCompleted = true;
						setErrorResponse(responseDTO, RegistrationConstants.BACKUP_PREVIOUS_SUCCESS, null);
					} catch (io.mosip.kernel.core.exception.IOException exception) {
						LOGGER.error(LoggerConstants.LOG_REG_UPDATE, APPLICATION_NAME, APPLICATION_ID,
								exception.getMessage() + ExceptionUtils.getStackTrace(exception));

						setErrorResponse(responseDTO, RegistrationConstants.BACKUP_PREVIOUS_FAILURE, null);
					}
					break;
				}
			}
		}
		if (!isBackUpCompleted) {
			setErrorResponse(responseDTO, RegistrationConstants.BACKUP_PREVIOUS_FAILURE, null);
		}
	}

	/**
	 * This method will return the checksum of the jars by reading it from the
	 * Manifest file.
	 * 
	 * @param jarName
	 *            jarName
	 * @param manifest
	 *            localManifestFile
	 * @return String - the checksum
	 */
	public String getCheckSum(String jarName, Manifest manifest) {

		// Get Local manifest
		manifest = manifest != null ? manifest : localManifest;

		String checksum = null;

		if (manifest == null) {

			try {
				manifest = getLocalManifest();

			} catch (IOException exception) {
				LOGGER.error(LoggerConstants.LOG_REG_UPDATE, APPLICATION_NAME, APPLICATION_ID,
						exception.getMessage() + ExceptionUtils.getStackTrace(exception));

			}
		}

		if (manifest != null) {
			checksum = (String) manifest.getEntries().get(jarName).get(Attributes.Name.CONTENT_TYPE);
		}

		// checksum (content-type)
		return checksum;
	}

	public Map<String, String> getJarChecksum() {
		if(CHECKSUM_MAP == null) {
			CHECKSUM_MAP = new HashMap<>();
			CHECKSUM_MAP.put(MOSIP_CLIENT, getCheckSum(MOSIP_CLIENT, null));
			CHECKSUM_MAP.put(MOSIP_SERVICES, getCheckSum(MOSIP_SERVICES, null));
		}
		return CHECKSUM_MAP;
	}

	private String getURL(String urlPostFix) {
		LOGGER.info(LoggerConstants.LOG_REG_UPDATE, APPLICATION_NAME, APPLICATION_ID,
				"Upgrade server : " + ApplicationContext.getUpgradeServerURL());
		return String.format(urlPostFix, ApplicationContext.getUpgradeServerURL());
	}

	private void addProperties(String version) {

		// LOGGER.info(LoggerConstants.LOG_REG_UPDATE, APPLICATION_NAME, APPLICATION_ID,
		// "Started updating version property in mosip-application.properties");
		//
		// try {
		// Properties properties = new Properties();
		// properties.load(new FileInputStream(propsFilePath));
		//
		// properties.setProperty("mosip.reg.version", version);
		//
		// // update mosip-Version in mosip-application.properties file
		// try (FileOutputStream outputStream = new FileOutputStream(propsFilePath)) {
		//
		// properties.store(outputStream, version);
		// }
		//
		// } catch (IOException ioException) {
		//
		// LOGGER.error(LoggerConstants.LOG_REG_UPDATE, APPLICATION_NAME,
		// APPLICATION_ID,
		// ioException.getMessage() + ExceptionUtils.getStackTrace(ioException));
		//
		// }
		//
		// LOGGER.info(LoggerConstants.LOG_REG_UPDATE, APPLICATION_NAME, APPLICATION_ID,
		// "Completed updating version property in mosip-application.properties");

	}

}
