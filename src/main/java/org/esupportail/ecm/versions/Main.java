package org.esupportail.ecm.versions;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.DocumentSecurityException;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.api.blobholder.BlobHolder;
import org.nuxeo.ecm.core.api.impl.blob.FileBlob;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.core.api.model.PropertyException;
import org.nuxeo.ecm.core.utils.BlobsExtractor;
import org.nuxeo.ecm.platform.mimetype.interfaces.MimetypeRegistry;
import org.nuxeo.ecm.platform.preview.helper.PreviewHelper;
import org.nuxeo.ecm.platform.ui.web.auth.NXAuthConstants;
import org.nuxeo.ecm.platform.web.common.vh.VirtualHostHelper;
import org.nuxeo.ecm.webengine.WebException;
import org.nuxeo.ecm.webengine.model.WebObject;
import org.nuxeo.ecm.webengine.model.exceptions.WebResourceNotFoundException;
import org.nuxeo.ecm.webengine.model.impl.ModuleRoot;
import org.nuxeo.runtime.api.Framework;

@Path("esupversions")
@WebObject(type = "esupversions")
public class Main extends ModuleRoot {

    private static final Log log = LogFactory.getLog(Main.class);

    protected java.io.File zipDirectory;

    protected boolean isRoot = true;

    protected String child;

    protected Object requestedObject;

    /**
     * Default view
     */
    @GET
    public Object doGet() {
        if (requestedObject == null)
            return getView("index");
        else
            return requestedObject;
    }

    protected void resolve(String versionUid) throws ClientException {
        // final DocumentRef docRef = new IdRef(versionUid);
        DocumentRef docRef = new IdRef(versionUid);
        CoreSession session = ctx.getCoreSession();

        DocumentModel doc = session.getLastDocumentVersion(docRef);
        if (doc == null) {
            doc = session.getDocument(docRef);
        }

        if (doc.isDownloadable()) {
            proceedDownloadable(versionUid, doc);
        }
        if (doc.hasSchema("note")) {
            procedNote(versionUid, doc);
        }
    }

    private void proceedDownloadable(String versionUid, DocumentModel doc)
            throws ClientException {
        if (child != null) {
            BlobHolder bh = doc.getAdapter(BlobHolder.class);
            for (Blob blob : bh.getBlobs()) {
                if (blob.getFilename().contains(child)) {
                    proceedBlob(versionUid, blob, blob.getFilename());
                    return;
                }
            }
        }

        Property propertyFile = doc.getProperty("file:content");
        if (propertyFile != null) {
            // Resolve filename when using a complex property ...
            Blob requestedBlob = (Blob) propertyFile.getValue();
            String requestedFilename = requestedBlob.getFilename();
            if (requestedFilename == null) {
                requestedFilename = resolveComplexFilename(
                        propertyFile.getParent(), requestedFilename);
            }

            proceedBlob(versionUid, requestedBlob, requestedFilename);
        }
    }

    protected String resolveComplexFilename(Property propertyFile,
            String requestedFilename) {
        if (propertyFile.isComplex()) { // special handling for file
            // and files schema
            try {
                requestedFilename = (String) propertyFile.getValue("filename");
                log.debug("resolve :: requestedFilename=" + requestedFilename);
            } catch (PropertyException e) {
                requestedFilename = "Unknown";
            }
        }
        return requestedFilename;
    }

    protected void proceedBlob(String versionUid, Blob requestedBlob,
            String requestedFilename) {
        if (requestedBlob == null) {
            throw new WebResourceNotFoundException("No attached file at "
                    + "file:content");
        }
        log.debug("resolve :: requestedFilename=" + requestedFilename);

        requestedObject = Response.ok(requestedBlob).header(
                "Content-Disposition", "inline; filename=" + requestedFilename).type(
                requestedBlob.getMimeType()).build();
        if (requestedFilename.endsWith(".zip")) {
            try {
                String tempdir = System.getProperty("java.io.tmpdir");
                log.debug("resolve :: tempdir=" + tempdir);
                File zipFile = new File(tempdir, "nuxeo-esup-webengine-"
                        + versionUid + ".zip");
                zipDirectory = new File(tempdir, "nuxeo-esup-webengine-"
                        + versionUid);
                if (!zipDirectory.exists()) {
                    InputStream inputStream = requestedBlob.getStream();
                    OutputStream out = new FileOutputStream(zipFile);
                    byte buf[] = new byte[1024];
                    int len;
                    while ((len = inputStream.read(buf)) > 0)
                        out.write(buf, 0, len);
                    out.close();
                    inputStream.close();
                    de.schlichtherle.io.File trueZipFile = new de.schlichtherle.io.File(
                            zipFile);
                    trueZipFile.copyAllTo(zipDirectory);
                }
                File[] files = zipDirectory.listFiles();
                Object requestedObjectTemp = null;
                // if one directory element
                if (files != null && files.length == 1
                        && files[0].isDirectory()) {
                    log.debug("resolve :: one directory element :: "
                            + files[0].getName());
                    requestedObjectTemp = getRequestedObjectFromFiles(
                            versionUid, files[0].getName(),
                            files[0].listFiles());
                }
                // some files or directories
                else {
                    log.debug("resolve :: files elements");
                    requestedObjectTemp = getRequestedObjectFromFiles(
                            versionUid, null, files);
                }
                if (requestedObjectTemp != null) {
                    requestedObject = requestedObjectTemp;
                }
            } catch (Exception ie) {
                log.error("problem unziping zip file from document version :"
                        + versionUid, ie);
            }
        }
    }

    /**
     * @param versionUid
     * @param doc
     * @throws PropertyException
     * @throws ClientException
     */
    private void procedNote(String versionUid, DocumentModel doc)
            throws PropertyException, ClientException {
        String requestedFilename = null;
        String requestedContent = null;
        String requestedMimeType = null;
        // TODO trouver:
        Property property = null;
        // file name
        property = doc.getProperty("dublincore:title");
        if (property != null) {
            requestedFilename = property.getValue() + ".txt";
        }
        // content
        property = doc.getProperty("note:note");
        if (property != null) {
            requestedContent = (String) property.getValue();
        }
        // mime_type
        property = doc.getProperty("note:mime_type");
        if (property != null) {
            requestedMimeType = (String) property.getValue();
            if (requestedMimeType == null) {
                requestedMimeType = "text/plain";
            }
        }
        if (requestedFilename != null && requestedContent != null
                && requestedMimeType != null) {
            requestedObject = Response.ok(requestedContent).header(
                    "Content-Disposition",
                    "inline; filename=" + requestedFilename).type(
                    requestedMimeType).build();
        }
    }

    private Object getRequestedObjectFromFiles(String versionUid,
            String directoryName, File[] files) {
        boolean indexHtml = false;
        boolean indexHtm = false;
        for (File file : files) {
            String fileName = file.getName();
            // root index.html file
            if (fileName.equals("index.html")) {
                indexHtml = true;
            }
            // root index.htm file
            if (fileName.equals("index.htm")) {
                indexHtm = true;
            }
        }
        log.debug("getRequestedObjectFromFiles :: indexHtml=" + indexHtml);
        log.debug("getRequestedObjectFromFiles :: indexHtm=" + indexHtm);
        if (indexHtml)
            return getRequestedObjectFromFileName(versionUid, directoryName,
                    "index.html");
        else if (indexHtm)
            return getRequestedObjectFromFileName(versionUid, directoryName,
                    "index.htm");
        else
            return null;
    }

    private Object getRequestedObjectFromFileName(String versionUid,
            String directoryName, String fileName) {
        String ctxUrlPath = getPath();
        ctxUrlPath = ctxUrlPath.endsWith("/") ? ctxUrlPath : ctxUrlPath + "/";
        ctxUrlPath = ctxUrlPath + versionUid;
        // String ctxUrlPath = ctx.getUrlPath();
        log.debug("getRequestedObjectFromFileName :: ctxUrlPath=" + ctxUrlPath);
        String indexUrl = ctxUrlPath.endsWith("/") ? ctxUrlPath : ctxUrlPath
                + "/";
        indexUrl = directoryName != null ? indexUrl + directoryName + "/"
                : indexUrl;
        indexUrl = indexUrl + fileName;
        log.debug("getRequestedObjectFromFileName :: indexUrl=" + indexUrl);
        return redirect(indexUrl);
    }

    /**
     * To prevent using of "skin" folder inside a ZIP file
     *
     * @see org.nuxeo.ecm.webengine.model.impl.ModuleRoot#getSkinResource(java.lang.String)
     */
    @GET
    @Path("defaultWebengineSkin/{path:.*}")
    public Response getSkinResource(@PathParam("path")
    String path) {
        return super.getSkinResource(path);
    }

    @Path(value = "{path}")
    public Object traverse(@PathParam("path")
    String path) throws Exception {
        CoreSession session = ctx.getCoreSession();
        String errorMessage = "";
        log.debug("traverse :: isRoot=" + isRoot);
        log.debug("traverse :: path=" + path);
        if (isRoot == true) {
            isRoot = false;
            String versionUid = path;
            log.debug("traverse :: versionUid=" + versionUid);
            try {
                resolve(versionUid);
                return this;
            } catch (DocumentSecurityException se) {
                NuxeoPrincipal user = (NuxeoPrincipal) session.getPrincipal();
                if (user.isAnonymous()) {
                    // inherited redirect method is not used here because it
                    // does not manage cookies
                    String localName = ctx.getRequest().getLocalName();
                    StringBuffer domain = new StringBuffer();
                    String[] localName_element = localName.split("\\.");
                    // start form i=1 to suppress first localName_element
                    for (int i = 1; i < localName_element.length; i++) {
                        domain.append(".").append(localName_element[i]);
                    }
                    String requestPage = this.getPath().replaceFirst("/nuxeo/",
                            "")
                            + this.getTrailingPath();
                    NewCookie cookieUrlToReach = new NewCookie(
                            NXAuthConstants.SSO_INITIAL_URL_REQUEST_KEY,
                            requestPage, "/", domain.toString(), 1,
                            NXAuthConstants.SSO_INITIAL_URL_REQUEST_KEY, 60,
                            false);

                    ResponseBuilder responseBuilder;
                    String url = ctx.getServerURL().append("/nuxeo/logout").toString();
                    try {
                        responseBuilder = Response.seeOther(new URI(url));
                    } catch (URISyntaxException e) {
                        throw WebException.wrap(e);
                    }
                    responseBuilder.cookie(cookieUrlToReach);
                    requestedObject = responseBuilder.build();
                    return this;
                } else
                    errorMessage = se.getMessage();
            } catch (ClientException ce) {
                log.error("Version document can't be viewed", ce);
                errorMessage = ce.getMessage();
            }
            requestedObject = getView("index").arg("versionUid", path).arg(
                    "error",
                    "Version document can't be accessed : " + errorMessage);
        } else {
            if (zipDirectory != null) {
                final String filePath = path;
                log.debug("traverse :: filePath=" + filePath);
                List<String> files = Arrays.asList(zipDirectory.list());
                log.debug("traverse :: files=" + files);
                if (files.contains(filePath)) {
                    log.debug("traverse :: files.contains == OK");
                    FilenameFilter filterNameIndex = new FilenameFilter() {
                        public boolean accept(java.io.File dir, String name) {
                            return name.equals(filePath);
                        }
                    };
                    java.io.File requestedFile = zipDirectory.listFiles(filterNameIndex)[0];
                    log.debug("traverse :: requestedFile=" + requestedFile);
                    Blob requestedBlob;
                    String requestedFilename;
                    if (requestedFile.isDirectory())
                        zipDirectory = requestedFile;
                    else {
                        requestedBlob = new FileBlob(requestedFile);
                        requestedFilename = filePath;
                        log.debug("traverse :: requestedFilename="
                                + requestedFilename);

                        ResponseBuilder responseBuilder = Response.ok(requestedBlob);
                        responseBuilder = responseBuilder.header(
                                "Content-Disposition",
                                "inline; filename=" + requestedFilename).type(
                                requestedBlob.getMimeType());

                        responseBuilder = responseBuilder.type(getMimetype(
                                requestedFilename, requestedBlob));
                        requestedObject = responseBuilder.build();

                    }
                }
            }
        }
        return this;
    }

    @GET
    @Path(value = "{uid}/file/{child}/preview")
    public Object redirecToPreview(@PathParam("uid")
       String uid, @PathParam("child")
       String child) throws Exception {

        try {
            IdRef idRef = new IdRef(uid);
            DocumentModel doc = ctx.getCoreSession().getLastDocumentVersion(idRef);
            if (doc == null) {
                doc = ctx.getCoreSession().getDocument(idRef);
            }

            for (Property pro : new BlobsExtractor().getBlobsProperties(doc)) {
                Blob bl = (Blob) pro.getValue();
                if (bl.getFilename().contains(child)) {
                    return redirect(VirtualHostHelper.getBaseURL(request) +
                            PreviewHelper.getPreviewURL(doc, previewXPath(pro)));
                }
            }
        } catch (ClientException e) {
            log.debug(e);
        }

        return Response.noContent().build();
    }

    protected String previewXPath(Property property) {
        String path = property.getPath();
        if (path.startsWith("/")) {
            path = path.substring(1, path.length());
        }
        path = path.replaceAll("item\\[(\\d+)\\]", "$1").replace("/", "-");
        return String.format("%s:%s", property.getSchema().getName(), path);
    }

    @Path(value = "{path}/file/{child}")
    public Object traverseWithChild(@PathParam("path")
    String path, @PathParam("child")
    String child) throws Exception {
        this.child = child;
        return traverse(path);
    }

    private String getMimetype(String fileName, Blob blog) throws Exception {
        // mimetype detection
        MimetypeRegistry mimeService = Framework.getService(MimetypeRegistry.class);
        String detectedMimeType = mimeService.getMimetypeFromFilenameAndBlobWithDefault(
                fileName, blog, null);

        // force Mime-Type for css and js files
        if (fileName.endsWith(".css")) {
            detectedMimeType = "text/css";
        } else if (fileName.endsWith(".js")) {
            detectedMimeType = "application/javascript";
        }

        if (detectedMimeType == null) {
            detectedMimeType = "application/octet-stream";
        }
        return detectedMimeType;
    }

}
