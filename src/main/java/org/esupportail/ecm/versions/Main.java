package org.esupportail.ecm.versions;

import javax.ws.rs.*;
import javax.ws.rs.core.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.core.api.model.PropertyException;
import org.nuxeo.ecm.core.rest.*;
import org.nuxeo.ecm.webengine.model.*;
import org.nuxeo.ecm.webengine.model.impl.*;
import org.nuxeo.ecm.webengine.model.exceptions.*;



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
		CoreSession session = ctx.getCoreSession();		
		DocumentModelList proxies = session.getProxies(docRef, null);
		
		DocumentModel doc = null;
		// if we can read a proxy, we just simply take the first one (all have the same content [versions])
		if(proxies.size() > 0)
			doc = proxies.get(0);
		// if no proxy give us the access, we try the version doc itself (in workspace)
		else
			doc = session.getDocument(docRef);

		
		DocumentObject webDoc = DocumentFactory.newDocumentRoot(ctx, doc.getRef());
		
		if(doc.isDownloadable()) {
			Property propertyFile = doc.getProperty("file:content");
			if(propertyFile != null) 
				return this.downloadFile(propertyFile);
		}
		// this returns directly to the view skin/views/Document/index.ftl !
		return 	webDoc;
		
		
	} catch(ClientException ce) {
		log.error("Version document can't be viewed", ce);
		return getView("index").arg("versionUid", versionUid).arg("error", "Version document can't be accessed : " + ce.getMessage());
	}
	
  }
  
  /**
 * @see org.nuxeo.ecm.core.rest.FileService
 */
protected Object downloadFile(Property propertyFile) throws  PropertyException {

          Blob blob = (Blob) propertyFile.getValue();
          if (blob == null) {
              throw new WebResourceNotFoundException("No attached file at " + "file:content");
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



