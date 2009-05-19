/*
 * (C) Copyright 2006-2008 Nuxeo SAS (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     bstefanescu
 *
 * $Id$
 */

package org.esupportail.ecm.versions;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Arrays;
import java.util.List;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.ecm.core.api.impl.blob.FileBlob;
import org.nuxeo.ecm.webengine.WebException;
import org.nuxeo.ecm.webengine.model.Resource;
import org.nuxeo.ecm.webengine.model.WebObject;
import org.nuxeo.ecm.webengine.model.exceptions.IllegalParameterException;
import org.nuxeo.ecm.webengine.model.impl.DefaultObject;


@WebObject(type = "WebSite")
@Produces({"text/html; charset=UTF-8"})
public class WebSiteObject extends DefaultObject {

    protected File siteDirectory;

    public WebSiteObject(File siteDirectory) {
    	this.siteDirectory = siteDirectory;
    }
    
    @GET
    public Object doGet() {
    	// return index
    	List<String> files = Arrays.asList(siteDirectory.list());
		  if(files.contains("index.html")) {
			  FilenameFilter filterNameIndex = new FilenameFilter() {
			        public boolean accept(java.io.File dir, String name) {
			            return name.equals("index.html");
			        }
			    };

			  Blob blob = new FileBlob(siteDirectory.listFiles(filterNameIndex)[0]);
			  String fileName = "index.html";
		  
        return Response.ok(blob)
          .header("Content-Disposition", "inline; filename=" + fileName)
          .type(blob.getMimeType())
          .build();
		  }
		  
		  return null;
    }


}
