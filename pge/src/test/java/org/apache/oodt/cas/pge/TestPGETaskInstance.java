/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.oodt.cas.pge;

//OODT static imports
import static org.apache.oodt.cas.pge.metadata.PgeTaskMetKeys.ACTION_IDS;
import static org.apache.oodt.cas.pge.metadata.PgeTaskMetKeys.ATTEMPT_INGEST_ALL;
import static org.apache.oodt.cas.pge.metadata.PgeTaskMetKeys.CONFIG_FILE_PATH;
import static org.apache.oodt.cas.pge.metadata.PgeTaskMetKeys.CRAWLER_CONFIG_FILE;
import static org.apache.oodt.cas.pge.metadata.PgeTaskMetKeys.CRAWLER_CRAWL_FOR_DIRS;
import static org.apache.oodt.cas.pge.metadata.PgeTaskMetKeys.CRAWLER_RECUR;
import static org.apache.oodt.cas.pge.metadata.PgeTaskMetKeys.DUMP_METADATA;
import static org.apache.oodt.cas.pge.metadata.PgeTaskMetKeys.INGEST_CLIENT_TRANSFER_SERVICE_FACTORY;
import static org.apache.oodt.cas.pge.metadata.PgeTaskMetKeys.INGEST_FILE_MANAGER_URL;
import static org.apache.oodt.cas.pge.metadata.PgeTaskMetKeys.MIME_EXTRACTOR_REPO;
import static org.apache.oodt.cas.pge.metadata.PgeTaskMetKeys.NAME;
import static org.apache.oodt.cas.pge.metadata.PgeTaskMetKeys.PGE_CONFIG_BUILDER;
import static org.apache.oodt.cas.pge.metadata.PgeTaskMetKeys.PROPERTY_ADDERS;
import static org.apache.oodt.cas.pge.metadata.PgeTaskMetKeys.REQUIRED_METADATA;
import static org.apache.oodt.cas.pge.metadata.PgeTaskMetKeys.WORKFLOW_MANAGER_URL;
import static org.apache.oodt.cas.pge.metadata.PgeTaskStatus.CRAWLING;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

//JDK imports
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.StringReader;
import java.util.Collections;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.net.URL;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;


//JUnit imports
import junit.framework.TestCase;

//Apache imports
import org.apache.commons.io.FileUtils;

//OODT imports
import org.apache.oodt.cas.crawl.AutoDetectProductCrawler;
import org.apache.oodt.cas.crawl.ProductCrawler;
import org.apache.oodt.cas.crawl.action.CrawlerAction;
import org.apache.oodt.cas.crawl.action.MoveFile;
import org.apache.oodt.cas.crawl.status.IngestStatus;
import org.apache.oodt.cas.metadata.Metadata;
import org.apache.oodt.cas.pge.config.DynamicConfigFile;
import org.apache.oodt.cas.pge.config.MockPgeConfigBuilder;
import org.apache.oodt.cas.pge.config.OutputDir;
import org.apache.oodt.cas.pge.config.PgeConfig;
import org.apache.oodt.cas.pge.metadata.PgeMetadata;
import org.apache.oodt.cas.pge.metadata.PgeTaskMetKeys;
import org.apache.oodt.cas.pge.metadata.PgeTaskStatus;
import org.apache.oodt.cas.pge.writers.MockDynamicConfigFileWriter;
import org.apache.oodt.cas.workflow.metadata.CoreMetKeys;
import org.apache.oodt.cas.workflow.structs.WorkflowTaskConfiguration;
import org.apache.oodt.cas.workflow.system.XmlRpcWorkflowManagerClient;

//Google imports
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;


/**
 * Test class for {@link PGETaskInstance}.
 *
 * @author bfoster (Brian Foster)
 */
public class TestPGETaskInstance extends TestCase {

   private final List<File> tmpDirs = Lists.newArrayList();

   @Override
   public void tearDown() throws Exception {
      for (File tmpDir : tmpDirs) {
         FileUtils.forceDelete(tmpDir);
      }
      tmpDirs.clear();
   }

   public void testLoadPropertyAdders() throws Exception {
      PGETaskInstance pgeTask = createTestInstance();
      ConfigFilePropertyAdder propAdder = pgeTask
            .loadPropertyAdder(MockConfigFilePropertyAdder.class
                  .getCanonicalName());
      assertNotNull(propAdder);
      assertTrue(propAdder instanceof MockConfigFilePropertyAdder);
   }

   public void testRunPropertyAdders() throws Exception {
      PGETaskInstance pgeTask = createTestInstance();
      pgeTask.pgeMetadata.replaceMetadata(PROPERTY_ADDERS,
            MockConfigFilePropertyAdder.class.getCanonicalName());
      pgeTask.pgeConfig.setPropertyAdderCustomArgs(new Object[] { "key",
            "value" });
      pgeTask.runPropertyAdders();
      assertEquals("value", pgeTask.pgeMetadata.getMetadata("key"));

      pgeTask.pgeMetadata.replaceMetadata(
            MockConfigFilePropertyAdder.RUN_COUNTER, "0");
      pgeTask.pgeMetadata.replaceMetadata(PROPERTY_ADDERS, Lists.newArrayList(
            MockConfigFilePropertyAdder.class.getCanonicalName(),
            MockConfigFilePropertyAdder.class.getCanonicalName()));
      pgeTask.runPropertyAdders();
      assertEquals("value", pgeTask.pgeMetadata.getMetadata("key"));
      assertEquals("2",
            pgeTask.pgeMetadata
                  .getMetadata(MockConfigFilePropertyAdder.RUN_COUNTER));

      pgeTask.pgeMetadata.replaceMetadata(
            MockConfigFilePropertyAdder.RUN_COUNTER, "0");
      System.setProperty(PgeTaskMetKeys.USE_LEGACY_PROPERTY, "true");
      pgeTask.pgeMetadata.replaceMetadata(PROPERTY_ADDERS.getName(),
            MockConfigFilePropertyAdder.class.getCanonicalName());
      pgeTask.runPropertyAdders();
      assertEquals("value", pgeTask.pgeMetadata.getMetadata("key"));
      assertEquals("1",
            pgeTask.pgeMetadata
                  .getMetadata(MockConfigFilePropertyAdder.RUN_COUNTER));
      System.getProperties().remove(PgeTaskMetKeys.USE_LEGACY_PROPERTY);
   }

   public void testCreatePgeMetadata() throws Exception {
      final String PGE_NAME = "PGE_Test";
      final String PGE_REQUIRED_METADATA = "Filename, FileLocation ";
      final String PROP_ADDERS = "some.prop.adder.classpath,some.other.classpath";
      PGETaskInstance pgeTask = createTestInstance();
      Metadata dynMet = new Metadata();
      WorkflowTaskConfiguration config = new WorkflowTaskConfiguration();
      config.addConfigProperty(NAME.getName(), PGE_NAME);
      config.addConfigProperty(REQUIRED_METADATA.getName(),
            PGE_REQUIRED_METADATA);
      config.addConfigProperty(PROPERTY_ADDERS.getName(), PROP_ADDERS);

      PgeMetadata pgeMet = pgeTask.createPgeMetadata(dynMet, config);
      assertEquals(1, pgeMet.getAllMetadata(NAME).size());
      assertEquals(PGE_NAME, pgeMet.getAllMetadata(NAME).get(0));
      assertEquals(2, pgeMet.getAllMetadata(REQUIRED_METADATA).size());
      assertTrue(pgeMet.getAllMetadata(REQUIRED_METADATA).contains("Filename"));
      assertTrue(pgeMet.getAllMetadata(REQUIRED_METADATA).contains(
            "FileLocation"));
      assertEquals(2, pgeMet.getAllMetadata(PROPERTY_ADDERS).size());
      assertTrue(pgeMet.getAllMetadata(PROPERTY_ADDERS).contains(
            "some.prop.adder.classpath"));
      assertTrue(pgeMet.getAllMetadata(PROPERTY_ADDERS).contains(
            "some.other.classpath"));

      // Verify still works when only one property adder is specified.
      pgeTask = createTestInstance();
      config = new WorkflowTaskConfiguration();
      config.addConfigProperty(PgeTaskMetKeys.PROPERTY_ADDERS.getName(),
            "one.prop.adder.only");

      pgeMet = pgeTask.createPgeMetadata(dynMet, config);
      assertEquals(1, pgeMet.getAllMetadata(PROPERTY_ADDERS).size());
      assertEquals("one.prop.adder.only", pgeMet
            .getAllMetadata(PROPERTY_ADDERS).get(0));
   }

   @SuppressWarnings("unchecked") // FileUtils.readLines cast to List<String>
   public void testLogger() throws Exception {
      PGETaskInstance pgeTask1 = createTestInstance();
      PGETaskInstance pgeTask2 = createTestInstance();

      pgeTask1.logger.log(Level.INFO, "pge1 message1");
      pgeTask1.logger.log(Level.INFO, "pge1 message2");
      pgeTask2.logger.log(Level.SEVERE, "pge2 message1");
      pgeTask1.logger.log(Level.INFO, "pge1 message3");

      for (Handler handler : pgeTask1.logger.getHandlers()) {
         handler.flush();
      }
      for (Handler handler : pgeTask2.logger.getHandlers()) {
         handler.flush();
      }
      File logDir = new File(pgeTask1.pgeConfig.getExeDir() + "/logs");
      assertTrue(logDir.exists());
      List<String> messages = FileUtils.readLines(logDir.listFiles(
         new FileFilter() {
            @Override
            public boolean accept(File pathname) {
               return pathname.getName().endsWith(".log");
            }
         })[0], "UTF-8");
      assertEquals(Level.INFO.getLocalizedName() + ": pge1 message1", messages.get(1));
      assertEquals(Level.INFO.getLocalizedName() + ": pge1 message2", messages.get(3));
      assertEquals(Level.INFO.getLocalizedName() + ": pge1 message3", messages.get(5));
      logDir = new File(pgeTask2.pgeConfig.getExeDir() + "/logs");
      assertTrue(logDir.exists());
      messages = FileUtils.readLines(logDir.listFiles(
         new FileFilter() {
            @Override
            public boolean accept(File pathname) {
               return pathname.getName().endsWith(".log");
            }
         })[0], "UTF-8");
      assertEquals(Level.SEVERE.getLocalizedName() + ": pge2 message1", messages.get(1));
   }

   public void testUpdateStatus() throws Exception {
      final Map<String, String> args = Maps.newHashMap();
      PGETaskInstance pgeTask = createTestInstance();
      pgeTask.wm = new XmlRpcWorkflowManagerClient(null) {
         @Override
         public boolean updateWorkflowInstanceStatus(String instanceId,
               String status) {
            args.put("InstanceId", instanceId);
            args.put("Status", status);
            return true;
         }
      };
      String instanceId = "Test ID";
      String status = PgeTaskStatus.CRAWLING.getWorkflowStatusName();
      pgeTask.workflowInstId = instanceId;
      pgeTask.updateStatus(status);
      assertEquals(instanceId, args.get("InstanceId"));
      assertEquals(status, args.get("Status"));
   }

   public void testCreatePgeConfig() throws Exception {
      final String KEY = "TestKey";
      final String VALUE = "TestValue";
      File pgeConfigFile = new File(createTmpDir(), "pgeConfig.xml");
      FileUtils.writeLines(pgeConfigFile, "UTF-8",
            Lists.newArrayList(
               "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
               "<pgeConfig>",
               "   <customMetadata>",
               "      <metadata key=\"" + KEY + "\" val=\"" + VALUE
               		+ "\"/>",
               "   </customMetadata>",
               "</pgeConfig>"));
      PGETaskInstance pgeTask = createTestInstance();
      pgeTask.pgeMetadata.replaceMetadata(CONFIG_FILE_PATH,
            pgeConfigFile.getAbsolutePath());
      PgeConfig pgeConfig = pgeTask.createPgeConfig();
      assertNotNull(pgeConfig);
      assertEquals(VALUE, pgeTask.pgeMetadata.getMetadata(KEY));

      pgeTask = createTestInstance();
      pgeTask.pgeMetadata.replaceMetadata(PGE_CONFIG_BUILDER,
            MockPgeConfigBuilder.class.getCanonicalName());
      pgeConfig = pgeTask.createPgeConfig();
      assertEquals(MockPgeConfigBuilder.MOCK_EXE_DIR, pgeConfig.getExeDir());
   }

   public void testCreateWorkflowManagerClient() throws Exception {
      PGETaskInstance pgeTask = createTestInstance();
      pgeTask.pgeMetadata.replaceMetadata(WORKFLOW_MANAGER_URL,
            "http://localhost:8888");
      XmlRpcWorkflowManagerClient wmClient =
         pgeTask.createWorkflowManagerClient();
      assertNotNull(wmClient);
   }

   public void testGetWorkflowInstanceId() throws Exception {
      String workflowInstId = "12345";
      PGETaskInstance pgeTask = createTestInstance();
      pgeTask.pgeMetadata.replaceMetadata(CoreMetKeys.WORKFLOW_INST_ID,
            workflowInstId);
      assertEquals(workflowInstId, pgeTask.getWorkflowInstanceId());
   }

   public void testCreateExeDir() throws Exception {
      PGETaskInstance pgeTask = createTestInstance();
      File exeDir = new File(pgeTask.pgeConfig.getExeDir());
      FileUtils.deleteDirectory(exeDir);
      assertFalse(exeDir.exists());
      pgeTask.createExeDir();
      assertTrue(exeDir.exists());
   }

   public void testCreateOuputDirsIfRequested() throws Exception {
      PGETaskInstance pgeTask = createTestInstance();
      File outputDir1 = createTmpDir();
      FileUtils.forceDelete(outputDir1);
      File outputDir2 = createTmpDir();
      FileUtils.forceDelete(outputDir2);
      File outputDir3 = new File("/some/file/path");
      assertFalse(outputDir1.exists());
      assertFalse(outputDir2.exists());
      assertFalse(outputDir3.exists());
      pgeTask.pgeConfig.addOuputDirAndExpressions(new OutputDir(outputDir1
            .getAbsolutePath(), true));
      pgeTask.pgeConfig.addOuputDirAndExpressions(new OutputDir(outputDir2
            .getAbsolutePath(), true));
      pgeTask.pgeConfig.addOuputDirAndExpressions(new OutputDir(outputDir3
            .getAbsolutePath(), false));
      pgeTask.createOuputDirsIfRequested();
      assertTrue(outputDir1.exists());
      assertTrue(outputDir2.exists());
      assertFalse(outputDir3.exists());
   }

   public void testCreateDynamicConfigFile() throws Exception {
      File tmpDir = createTmpDir();
      FileUtils.forceDelete(tmpDir);
      assertFalse(tmpDir.exists());
      PGETaskInstance pgeTask = createTestInstance();
      File dynamicConfigFile = new File(tmpDir, "DynamicConfig.xml");
      assertFalse(dynamicConfigFile.exists());
      pgeTask.createDynamicConfigFile(new DynamicConfigFile(dynamicConfigFile.getAbsolutePath(),
            MockDynamicConfigFileWriter.class.getCanonicalName(),
            new Object[] {}));
      assertTrue(dynamicConfigFile.exists());
   }

   private static Document parseXmlFile(File file) throws Exception{
      DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
      Document dom = null;
      DocumentBuilder db = dbf.newDocumentBuilder();
      dom = db.parse(file);
      return dom;
   }

   public static Document parseXmlString(String xml) throws Exception  {
	 DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
	 DocumentBuilder builder = factory.newDocumentBuilder();
	 InputSource is = new InputSource(new StringReader(xml));
	 return builder.parse(is);
    }
    
   public void testDumpMetadataIfRequested() throws Exception {
      PGETaskInstance pgeTask = createTestInstance();
      File dumpMetFile = new File(pgeTask.getDumpMetadataPath());
      pgeTask.dumpMetadataIfRequested();
      assertFalse(dumpMetFile.exists());
      pgeTask.pgeMetadata.replaceMetadata(DUMP_METADATA, "true");
      pgeTask.dumpMetadataIfRequested();
      assertTrue(dumpMetFile.exists());
      @SuppressWarnings("unchecked")
      String expectedMetString =    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
    		+ "<cas:metadata xmlns:cas=\"http://oodt.jpl.nasa.gov/1.0/cas\">"
      		+ "<keyval type=\"vector\">"
    		+ "<key>PGETask%2FName</key>"
      		+ "<val>"
    		+ pgeTask.pgeMetadata.getMetadata(NAME)
    		+ "</val>"
    		+ "</keyval>"
    		+ "<keyval type=\"vector\">"
    		+ "<key>PGETask%2FDumpMetadata</key>"
    		+ "<val>"
    		+ pgeTask.pgeMetadata.getMetadata(DUMP_METADATA)
    		+ "</val>"
    		+ "</keyval>"
    		+ "</cas:metadata>";
       
       Document dumpMetDoc = parseXmlFile(dumpMetFile);
       Document expectedMetDoc = parseXmlString(expectedMetString);
       
       
       Element dumpMetRoot, expectedMetRoot;
       dumpMetRoot = dumpMetDoc.getDocumentElement();
       expectedMetRoot = expectedMetDoc.getDocumentElement();
       
       
       NodeList dumpKeyList = dumpMetRoot.getElementsByTagName("key");
       NodeList expectedKeyList = expectedMetRoot.getElementsByTagName("key");
       
       NodeList dumpValList = dumpMetRoot.getElementsByTagName("val");
       NodeList expectedValList = expectedMetRoot.getElementsByTagName("val");
       
       assertEquals(dumpKeyList.getLength(), expectedKeyList.getLength());
       assertEquals(dumpValList.getLength(), expectedValList.getLength());
       
       
       Map<String, String> dumpKeyValMap = new HashMap<String, String>();
       Map<String, String> expectedKeyValMap = new HashMap<String, String>();
       
       for (int i = 0; i < dumpKeyList.getLength(); i++) {
           
           Node k1 = dumpKeyList.item(i);
           Node k2 = expectedKeyList.item(i);
           
           Node v1 = dumpValList.item(i);
           Node v2 = expectedValList.item(i);
           
           dumpKeyValMap.put(k1.getFirstChild().getNodeValue(), v1.getFirstChild().getNodeValue());
           expectedKeyValMap.put(k2.getFirstChild().getNodeValue(), v2.getFirstChild().getNodeValue());
           
       }
       
       assertTrue(dumpKeyValMap.equals(expectedKeyValMap));
       
   }

   public void testCreateProductCrawler() throws Exception {
      PGETaskInstance pgeTask = createTestInstance();
      pgeTask.pgeMetadata.replaceMetadata(MIME_EXTRACTOR_REPO,
            "src/main/resources/examples/Crawler/mime-extractor-map.xml");
      pgeTask.pgeMetadata.replaceMetadata(
            INGEST_CLIENT_TRANSFER_SERVICE_FACTORY,
            "org.apache.oodt.cas.filemgr.datatransfer.LocalDataTransferFactory");
      pgeTask.pgeMetadata.replaceMetadata(INGEST_FILE_MANAGER_URL,
            "http://localhost:9000");
      pgeTask.pgeMetadata.replaceMetadata(CRAWLER_CONFIG_FILE,
            "src/main/resources/examples/Crawler/crawler-config.xml");
      pgeTask.pgeMetadata.replaceMetadata(ACTION_IDS,
            Lists.newArrayList("DeleteDataFile", "MoveMetadataFileToFailureDir"));
      pgeTask.pgeMetadata.replaceMetadata(REQUIRED_METADATA,
            Lists.newArrayList("Owners"));
      pgeTask.pgeMetadata.replaceMetadata(CRAWLER_CRAWL_FOR_DIRS,
            Boolean.toString(false));
      pgeTask.pgeMetadata.replaceMetadata(CRAWLER_RECUR,
            Boolean.toString(true));

      ProductCrawler pc = pgeTask.createProductCrawler();
      assertEquals(
            "org.apache.oodt.cas.filemgr.datatransfer.LocalDataTransferFactory",
            pc.getClientTransferer());
      assertEquals("http://localhost:9000", pc.getFilemgrUrl());
      assertEquals(
            Sets.newHashSet("DeleteDataFile", "MoveMetadataFileToFailureDir"),
            Sets.newHashSet(pc.getActionIds()));
      CrawlerAction action = (CrawlerAction) pc.getApplicationContext().getBean("DeleteDataFile");
      assertNotNull(action);
      MoveFile moveFileAction = (MoveFile) pc.getApplicationContext().getBean("MoveMetadataFileToFailureDir");
      Properties properties = new Properties();
      properties.load(new FileInputStream(new File(
            "src/main/resources/examples/Crawler/action-beans.properties")));
      assertEquals(properties.get("crawler.failure.dir"),
            moveFileAction.getToDir());
      assertTrue(pc.getRequiredMetadata().contains("Owners"));
      assertFalse(pc.isCrawlForDirs());
      assertFalse(pc.isNoRecur());
   }

   public void testRunIngestCrawler() throws Exception {
      // Case: UpdateStatus Success, VerifyIngest Success,
      PGETaskInstance pgeTask = createTestInstance();
      pgeTask.pgeConfig.addOuputDirAndExpressions(new OutputDir("/tmp/dir1", true));
      pgeTask.pgeConfig.addOuputDirAndExpressions(new OutputDir("/tmp/dir2", true));
      pgeTask.pgeMetadata.replaceMetadata(ATTEMPT_INGEST_ALL, Boolean.toString(true));
      pgeTask.workflowInstId = "WorkflowInstanceId";

      pgeTask.wm = createMock(XmlRpcWorkflowManagerClient.class);
      expect(pgeTask.wm.updateWorkflowInstanceStatus(pgeTask.workflowInstId,
            CRAWLING.getWorkflowStatusName())).andReturn(true);
      replay(pgeTask.wm);

      AutoDetectProductCrawler pc = createMock(AutoDetectProductCrawler.class);
      pc.crawl(new File("/tmp/dir1"));
      pc.crawl(new File("/tmp/dir2"));
      expect(pc.getIngestStatus()).andReturn(Collections.<IngestStatus>emptyList());
      replay(pc);

      pgeTask.runIngestCrawler(pc);

      verify(pgeTask.wm);
      verify(pc);

      // Case: UpdateStatus Fail
      pgeTask.wm = createMock(XmlRpcWorkflowManagerClient.class);
      expect(pgeTask.wm.updateWorkflowInstanceStatus(pgeTask.workflowInstId,
            CRAWLING.getWorkflowStatusName())).andReturn(false);
      replay(pgeTask.wm);

      pc = createMock(AutoDetectProductCrawler.class);
      replay(pc);

      try {
         pgeTask.runIngestCrawler(pc);
         fail("Should have thrown");
      } catch (Exception e) { /* expect throw */ }

      verify(pgeTask.wm);
      verify(pc);

      // Case: UpdateStatus Success, VerifyIngest Fail
      pgeTask.wm = createMock(XmlRpcWorkflowManagerClient.class);
      expect(pgeTask.wm.updateWorkflowInstanceStatus(pgeTask.workflowInstId,
            CRAWLING.getWorkflowStatusName())).andReturn(true);
      replay(pgeTask.wm);

      pc = createMock(AutoDetectProductCrawler.class);
      pc.crawl(new File("/tmp/dir1"));
      pc.crawl(new File("/tmp/dir2"));
      IngestStatus failedIngestStatus = new IngestStatus() {
         @Override
         public String getMessage() {
            return "Ingest Failure";
         }
         @Override
         public File getProduct() {
            return new File("/tmp/dir1");
         }
         @Override
         public Result getResult() {
            return Result.FAILURE;
         }
      };
      expect(pc.getIngestStatus()).andReturn(
            Lists.newArrayList(failedIngestStatus));
      replay(pc);

      try {
         pgeTask.runIngestCrawler(pc);
         fail("Should have thrown");
      } catch (Exception e) { /* expect throw */ }

      verify(pgeTask.wm);
      verify(pc);
   }

   public void testVerifyIngests() throws Exception {
      PGETaskInstance pgeTask = createTestInstance();

      // Test case failure.
      AutoDetectProductCrawler pc = createMock(AutoDetectProductCrawler.class);
      IngestStatus failedIngestStatus = new IngestStatus() {
         @Override
         public String getMessage() {
            return "Ingest Failure";
         }
         @Override
         public File getProduct() {
            return new File("/tmp/dir1");
         }
         @Override
         public Result getResult() {
            return Result.FAILURE;
         }
      };
      expect(pc.getIngestStatus()).andReturn(
            Lists.newArrayList(failedIngestStatus));
      replay(pc);

      try {
         pgeTask.verifyIngests(pc);
         fail("Should have thrown");
      } catch (Exception e) { /* expect throw */ }

      verify(pc);

      // Test case warn failure of precondition, but success overall.
      pc = createMock(AutoDetectProductCrawler.class);
      IngestStatus precondsFailIngestStatus = new IngestStatus() {
         @Override
         public String getMessage() {
            return "Preconditions failed";
         }
         @Override
         public File getProduct() {
            return new File("/tmp/dir1");
         }
         @Override
         public Result getResult() {
            return Result.PRECONDS_FAILED;
         }
      };
      expect(pc.getIngestStatus()).andReturn(
            Lists.newArrayList(precondsFailIngestStatus));
      replay(pc);

      pgeTask.logger = createMock(Logger.class);
      pgeTask.logger.info("Verifying ingests successful...");
      pgeTask.logger.warning(
            "Product was not ingested [file='/tmp/dir1',result='PRECONDS_FAILED',msg='Preconditions failed']");
      pgeTask.logger.info("Ingests were successful");
      replay(pgeTask.logger);

      pgeTask.verifyIngests(pc);

      verify(pc);
      verify(pgeTask.logger);

      // Test case success.
      pc = createMock(AutoDetectProductCrawler.class);
      IngestStatus successIngestStatus = new IngestStatus() {
         @Override
         public String getMessage() {
            return "Ingest Success";
         }
         @Override
         public File getProduct() {
            return new File("/tmp/dir1");
         }
         @Override
         public Result getResult() {
            return Result.SUCCESS;
         }
      };
      expect(pc.getIngestStatus()).andReturn(
            Lists.newArrayList(successIngestStatus));
      replay(pc);

      pgeTask.logger = createMock(Logger.class);
      pgeTask.logger.info("Verifying ingests successful...");
      pgeTask.logger.info("Ingests were successful");
      replay(pgeTask.logger);

      pgeTask.verifyIngests(pc);

      verify(pc);
      verify(pgeTask.logger);
   }

   private PGETaskInstance createTestInstance() throws Exception {
      return createTestInstance(UUID.randomUUID().toString());
   }

   private PGETaskInstance createTestInstance(String workflowInstId)
         throws Exception {
      PGETaskInstance pgeTask = new PGETaskInstance();
      pgeTask.workflowInstId = workflowInstId;
      pgeTask.pgeMetadata = new PgeMetadata();
      pgeTask.pgeMetadata.replaceMetadata(NAME, "TestPGE");
      pgeTask.pgeConfig = new PgeConfig();
      File exeDir = new File(createTmpDir(), workflowInstId);
      pgeTask.pgeConfig.setExeDir(exeDir.getAbsolutePath());
      pgeTask.logger = pgeTask.createLogger();
      return pgeTask;
   }

   private File createTmpDir() throws Exception {
      File tmpFile = File.createTempFile("bogus", "bogus");
      File tmpDir = new File(tmpFile.getParentFile(), UUID.randomUUID().toString());
      tmpFile.delete();
      tmpDir.mkdirs();
      tmpDirs.add(tmpDir);
      return tmpDir;
   }
}
