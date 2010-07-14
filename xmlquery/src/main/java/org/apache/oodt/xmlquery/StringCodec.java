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


package jpl.eda.xmlquery;

import java.io.*;
import java.util.zip.*;
//import jpl.oodt.util.*;
import jpl.eda.util.*;
import org.w3c.dom.*;

/** A result encoder/decoder for strings.
 *
 * This codec uses a string format for objects.
 *
 * @author Kelly
 */
class StringCodec implements Codec {
	public Node encode(Object object, Document doc) throws DOMException {
		Element value = doc.createElement("resultValue");
		value.setAttribute("xml:space", "preserve");
		value.appendChild(doc.createTextNode(object.toString()));
		return value;
	}

	public Object decode(Node node) {
		return XML.text(node);
	}

	public InputStream getInputStream(Object value) throws IOException {
		return new ByteArrayInputStream(((String) value).getBytes());
	}

	public long sizeOf(Object object) {
		return ((String) object).getBytes().length;
	}
}