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
package org.apache.oodt.cas.curation.rest;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.cxf.jaxrs.ext.multipart.Attachment;
import org.apache.cxf.jaxrs.ext.multipart.MultipartBody;
import org.apache.oodt.cas.curation.configuration.Configuration;

/**
 * A RESTful backend allowing for the user to upload files
 * to the CAS-Curator.
 * 
 * @author starchmd
 */
@Path("upload")
public class UploadBackend {
  /**
   * Construct using configuration to determine upload area
   */
  public UploadBackend() {}

  @POST
  @Path("file")
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  /**
   * Handles the uploading of the data, by copying all data out of the input stream.
   * @param mbody - body of the multipart post
   * @return - OK response on success
   * @throws IOException - exception thrown on error
   */
  public Response upload(MultipartBody mbody) throws IOException {
    List<Attachment> attachments = mbody.getAllAttachments();
    for (Attachment attachment : attachments) {
      String filename = attachment.getContentDisposition().getParameter("filename");
      if (filename.endsWith(".zip")) {
        Map<String, InputStream> unzipdMap = unzipAttachment(filename, attachment.getDataHandler().getInputStream());
        Iterator<Entry<String, InputStream>> it = unzipdMap.entrySet().iterator();
        while (it.hasNext()) {
          Map.Entry<String, InputStream> pair = (Map.Entry<String, InputStream>) it.next();
          writeToStaging(pair.getKey(), pair.getValue());
        }
      } else {
        writeToStaging(filename, attachment.getDataHandler().getInputStream());
      }
    }
    return Response.ok().build();
  }

  private Map<String, InputStream> unzipAttachment(String filename, InputStream is) throws IOException {
    writeToStaging(filename, is); // first write the zipFile to staging
    String tmpZip = "/tmp/" + filename; // write file to /tmp for copy
    ZipFile zipFile = null;
    HashMap<String, InputStream> fileMap = new HashMap<String, InputStream>();
    try {
      FileUtils.copyInputStreamToFile(is, new File(tmpZip));
      zipFile = new ZipFile(tmpZip);

      final Enumeration<? extends ZipEntry> entries = zipFile.entries();

      while(entries.hasMoreElements()) {
        ZipEntry entry = (ZipEntry)entries.nextElement();

        if(entry.isDirectory()) {
          // Assume directories are stored parents first then children.
          System.out.println("Extracting directory: " + entry.getName());
          // This is not robust, just for demonstration purposes.
          (new File(Configuration.getWithReplacement(Configuration.UPLOAD_AREA_CONFIG),entry.getName())).mkdir();
          continue;
        }

        System.out.println("Extracting file: " + entry.getName());
        fileMap.put(entry.getName(), zipFile.getInputStream(entry));
      }

      zipFile.close();
    } catch (IOException ioe) {
      System.err.println("Unhandled exception:");
      ioe.printStackTrace();
    }
    return fileMap;
  }

  private void writeToStaging(String filename, InputStream in) throws IOException {
    try {
      OutputStream os = new FileOutputStream(new File(Configuration.getWithReplacement(Configuration.UPLOAD_AREA_CONFIG),filename));
      IOUtils.copy(in,os);
      in.close();
      os.close();
    } catch(IOException e) {
      throw new IOException("Failed uploading file:",e);
    }

  }
}
