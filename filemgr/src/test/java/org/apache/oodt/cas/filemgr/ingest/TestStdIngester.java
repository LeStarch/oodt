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


package org.apache.oodt.cas.filemgr.ingest;

//JDK imports
import java.io.File;
import java.io.FileInputStream;
import java.net.URL;
import java.util.Properties;
//OODT imports
import org.apache.oodt.cas.filemgr.metadata.CoreMetKeys;
import org.apache.oodt.cas.filemgr.structs.Product;
import org.apache.oodt.cas.filemgr.system.XmlRpcFileManager;
import org.apache.oodt.cas.filemgr.system.XmlRpcFileManagerClient;
import org.apache.oodt.cas.metadata.Metadata;
import org.apache.oodt.cas.metadata.SerializableMetadata;

// Jnit imports
import junit.framework.TestCase;

/**
 * @author mattmann
 * @version $Revision$
 * 
 * <p>
 * Describe your class here
 * </p>.
 */
public class TestStdIngester extends TestCase {

    private static final int FM_PORT = 50010;

    private XmlRpcFileManager fm;

    private String luceneCatLoc;

    private StdIngester ingester;

    private static final String transferServiceFacClass = "org.apache.oodt.cas."
            + "filemgr.datatransfer.LocalDataTransferFactory";

    private Properties initialProperties = new Properties(
        System.getProperties());

    public TestStdIngester() {
        ingester = new StdIngester(transferServiceFacClass);
    }

    public void testIngest() {
        Metadata prodMet = null;

        try {
            URL ingestUrl = this.getClass().getResource("/ingest");
            URL refUrl = this.getClass().getResource("/ingest/test.txt");
            URL metUrl = this.getClass().getResource("/ingest/test.txt.met");

            prodMet = new SerializableMetadata(new FileInputStream(
                new File(metUrl.getFile())));

            // now add the right file location
            prodMet.addMetadata(CoreMetKeys.FILE_LOCATION, new File(
                ingestUrl.getFile()).getCanonicalPath());
            ingester.ingest(new URL("http://localhost:" + FM_PORT), new File(
                refUrl.getFile()), prodMet);
        } catch (Exception e) {
            fail(e.getMessage());
        }
        
        // now make sure that the file is ingested
        try {
            XmlRpcFileManagerClient fmClient = new XmlRpcFileManagerClient(new URL("http://localhost:"+FM_PORT));
            Product p = fmClient.getProductByName("test.txt");
            assertNotNull(p);
            assertEquals(Product.STATUS_RECEIVED, p.getTransferStatus());
            assertTrue(fmClient.hasProduct("test.txt"));
            fmClient = null;
        } catch (Exception e){
            fail(e.getMessage());
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see junit.framework.TestCase#setUp()
     */
    protected void setUp() throws Exception {
        startXmlRpcFileManager();
    }

    /*
     * (non-Javadoc)
     * 
     * @see junit.framework.TestCase#tearDown()
     */
    protected void tearDown() throws Exception {
        fm.shutdown();
        fm = null;

        // blow away lucene cat
        deleteAllFiles(luceneCatLoc);

        // blow away test file
        deleteAllFiles("/tmp/test.txt");

        // Reset the System properties to initial values.
        System.setProperties(initialProperties);
    }

    private void deleteAllFiles(String startDir) {
        File startDirFile = new File(startDir);
        File[] delFiles = startDirFile.listFiles();

        if (delFiles != null && delFiles.length > 0) {
            for (int i = 0; i < delFiles.length; i++) {
                delFiles[i].delete();
            }
        }

        startDirFile.delete();

    }

    private void startXmlRpcFileManager() {

        Properties properties = new Properties(System.getProperties());

        // first make sure to load properties for the file manager
        // and make sure to load logging properties as well

        // set the log levels
        URL loggingPropertiesUrl = this.getClass().getResource(
            "/test.logging.properties");
        properties.setProperty("java.util.logging.config.file", new File(
            loggingPropertiesUrl.getFile()).getAbsolutePath());

        // first load the example configuration
        try {
          URL filemgrPropertiesUrl = this.getClass().getResource(
              "/filemgr.properties");
          properties.load(
              new FileInputStream(new File(filemgrPropertiesUrl.getFile())));
        } catch (Exception e) {
            fail(e.getMessage());
        }

        // override the catalog to use: we'll use lucene
        try {
            URL ingestUrl = this.getClass().getResource("/ingest");
            luceneCatLoc = new File(ingestUrl.getFile()).getCanonicalPath()
                + "/cat";
        } catch (Exception e) {
            fail(e.getMessage());
        }

        properties.setProperty("filemgr.catalog.factory",
                "org.apache.oodt.cas.filemgr.catalog.LuceneCatalogFactory");
        properties.setProperty(
                "org.apache.oodt.cas.filemgr.catalog.lucene.idxPath",
                luceneCatLoc);

        // now override the repo mgr policy
        try {
            URL fmpolicyUrl = this.getClass().getResource("/ingest/fmpolicy");
            properties.setProperty(
                    "org.apache.oodt.cas.filemgr.repositorymgr.dirs",
                    "file://"
                        + new File(fmpolicyUrl.getFile()).getCanonicalPath());
        } catch (Exception e) {
            fail(e.getMessage());
        }

        // now override the val layer ones
        URL examplesCoreUrl = this.getClass().getResource("/examples/core");
        properties.setProperty("org.apache.oodt.cas.filemgr.validation.dirs",
                "file://"
                    + new File(examplesCoreUrl.getFile()).getAbsolutePath());

        // set up mime repo path
        URL mimeTypesUrl = this.getClass().getResource("/mime-types.xml");
        properties.setProperty(
                "org.apache.oodt.cas.filemgr.mime.type.repository", new File(
                     mimeTypesUrl.getFile()).getAbsolutePath());

        System.setProperties(properties);

        try {
            fm = new XmlRpcFileManager(FM_PORT);
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

}
