<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  contributor license agreements.  See the NOTICE file distributed with
  this work for additional information regarding copyright ownership.
  The ASF licenses this file to You under the Apache License, Version 2.0
  (the "License"); you may not use this file except in compliance with
  the License.  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
-->

<script type="text/javascript" src="js/jquery/jquery.js"></script>
<script type="text/javascript">
var uploadedFilesCounter = 0;

function makeNewFileUploadBox() {
	// Build the HTML
	var uploadBoxHTML = "<input type=\"file\" " +
		"id=\"uploaded_file" + (++uploadedFilesCounter) +"\" " +
		"name=\"uploaded_file" + (uploadedFilesCounter) +"\" " +
		"style=\"width:100%;\" />";
		
	// Add it to the form
	$("#additionalFileUploadBoxes").append(uploadBoxHTML);

	// prevent default action for the link
	return false;
}

</script>

<div class="wizardContent">
	<h4>Add Data : Choose a Metadata Extractor</h4>

	<div>
		<form action="uploadMetExtractorConfig" method="POST" enctype="multipart/form-data">
			<table style="border:solid 1px #ccc;width:470px;margin-bottom:15px;">
			  <tr><td style="width:190px;">Selected Dataset Collection: </td><td><%=session.getAttribute("dsCollection") %> <a class="actionlink" href="ingestData.jsp">change</a></td></tr>
			  <tr><td>Selected Dataset:</td><td><%=session.getAttribute("ds") %> <a class="actionlink" href="addData.jsp">change</a></td></tr>
			  <tr><td>Select Metadata Extractor:</td><td>
			  	<select name="metext" style="border:solid 1px #888;padding:1px;width:100%;">
			  		<option value="org.apache.oodt.cas.metadata.extractors.CopyAndRewriteExtractor">CopyAndRewriteExtractor</option>
			  	</select>
			  <tr>
			  	<td>Upload Configuration File(s): </td><td style="padding-right:9px;">
			  		<input type="file" id="uploaded_file0" name="uploaded_file0" style="width:100%;" />
			  		
			  		<div id="additionalFileUploadBoxes">
			  		
			  		</div>
			  		<a class="actionlink" href="#" id="moreFilesToUpload" onclick="return makeNewFileUploadBox()">I need to upload more files</a>
			  	</td>
			  </tr>
			  <tr><td colspan="2" style="text-align:right;font-size:90%;color:#333;">Choose a configuration file to upload, and click to continue...&nbsp;&nbsp; <input type="submit" id="submitButton" value="Continue..." style="padding:2px;"/></td></tr>
			</table>
		</form>
	</div>
</div>
