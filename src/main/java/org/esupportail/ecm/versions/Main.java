package org.esupportail.ecm.versions;

import java.io.*;
import javax.ws.rs.*;
import javax.ws.rs.core.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.IdRef;
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

  @GET
  @Path("uid/{versionUid}")
  public Object getVersion(@PathParam("versionUid") String versionUid) {
	
	Template returnedView = getView("resolve").arg("versionUid", versionUid);
	  
	CoreSession session = ctx.getCoreSession();
	final DocumentRef docRef = new IdRef(versionUid);
	
	try {
		DocumentModel doc = session.getDocument(docRef);
		returnedView.arg("version", doc);
		
		return 	DocumentFactory.newDocumentRoot(ctx, docRef);
	} catch(ClientException ce) {
		log.error("Version document can't be viewed", ce);
		returnedView.arg("error", "Version document can't be viewed : " + ce.getMessage());
	}
	
	return returnedView;
  }

  
}

