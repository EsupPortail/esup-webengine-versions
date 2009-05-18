package org.esupportail.ecm.versions;

import java.io.*;
import javax.ws.rs.*;
import javax.ws.rs.core.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.core.api.model.PropertyException;
import org.nuxeo.ecm.core.rest.*;
import org.nuxeo.ecm.webengine.model.*;
import org.nuxeo.ecm.webengine.model.impl.*;
import org.nuxeo.ecm.webengine.model.exceptions.*;
import org.nuxeo.ecm.webengine.*;


import sun.util.logging.resources.logging;

@WebObject(type="esupversions")
@Produces("text/html; charset=UTF-8")
public class Main extends ModuleRoot {

  private static final Log log = LogFactory.getLog(Main.class);
	
  /**
   * Default view
   */
  @GET
  public Object doGet() {
    return getView("index");
  }

  @Path("uid/{versionUid}")
  @GET
  public Object getRepository(@PathParam("versionUid") String versionUid) {
	
	//final DocumentRef docRef = new IdRef(versionUid);
	 DocumentRef docRef = new IdRef(versionUid);
	
	try {
		// this 2 lines allows only to verify that we can access to this document 
		CoreSession session = ctx.getCoreSession();
		DocumentModel doc = session.getDocument(docRef);
		
		DocumentObject webDoc = DocumentFactory.newDocumentRoot(ctx, docRef);
		
		if(doc.isDownloadable()) {
			String xpath = "file:content";
			Property propertyFile = doc.getProperty(xpath);
			
			if(propertyFile != null) {

	            Blob blob = (Blob) propertyFile.getValue();
	            if (blob == null) {
	                throw new WebResourceNotFoundException("No attached file at " + xpath);
	            }
	            String fileName = blob.getFilename();
	            if (fileName == null) {
	            	propertyFile = propertyFile.getParent();
	                if (propertyFile.isComplex()) { // special handling for file and files schema
	                    try {
	                        fileName = (String) propertyFile.getValue("filename");
	                    } catch (PropertyException e) {
	                        fileName = "Unknown";
	                    }
	                }
	            }
	            return Response.ok(blob)
	                    .header("Content-Disposition", "inline; filename=" + fileName)
	                    .type(blob.getMimeType())
	                    .build();
			}
		}
		// this returns directly to the view skin/views/Document/index.ftl !
		return 	webDoc;
		
		
	} catch(ClientException ce) {
		log.error("Version document can't be viewed", ce);
		return getView("index").arg("versionUid", versionUid).arg("error", "Version document can't be accessed : " + ce.getMessage());
	}
	
  }
  
}

