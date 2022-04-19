/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.crosswalk;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.DSpaceObject;
import org.dspace.content.packager.PackageDisseminator;
import org.dspace.content.packager.PackageException;
import org.dspace.content.packager.PackageIngester;
import org.dspace.content.packager.PackageParameters;
import org.dspace.content.packager.RoleDisseminator;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.core.factory.CoreServiceFactory;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.dspace.workflow.WorkflowException;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.Namespace;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.XMLOutputter;

/**
 * Role Crosswalk
 * <p>
 * Translate between DSpace Group and EPeople definitions and a DSpace-specific
 * XML export format (generated by the RoleDisseminator).  This is primarily
 * used for AIPs, but may be used by other Packagers as necessary.
 * <p>
 * This crosswalk allows you to export DSpace Groups and EPeople to this XML
 * structured format.  It also allows you to import an XML file of this format
 * in order to restore DSpace Groups and EPeople defined within it.
 * <p>
 * This is just wrappers; the real work is done in RoleDisseminator and
 * RoleIngester.
 *
 * @author mwood
 * @author Tim Donohue
 * @see org.dspace.content.packager.RoleDisseminator
 * @see org.dspace.content.packager.RoleIngester
 * @see AbstractPackagerWrappingCrosswalk
 * @see IngestionCrosswalk
 * @see DisseminationCrosswalk
 */
public class RoleCrosswalk
    extends AbstractPackagerWrappingCrosswalk
    implements IngestionCrosswalk, DisseminationCrosswalk {
    // Plugin Name of DSPACE-ROLES packager to use for ingest/dissemination
    // (Whatever plugin is defined with this name in 'dspace.cfg' will be used by this Crosswalk)
    private static final String ROLE_PACKAGER_PLUGIN = "DSPACE-ROLES";

    // ---- Dissemination Methods -----------

    /**
     * Get XML namespaces of the elements this crosswalk may return.
     * Returns the XML namespaces (as JDOM objects) of the root element.
     *
     * @return array of namespaces, which may be empty.
     */
    @Override
    public Namespace[] getNamespaces() {
        Namespace result[] = new Namespace[1];
        result[0] = RoleDisseminator.DSROLES_NS;
        return result;
    }


    /**
     * Get the XML Schema location(s) of the target metadata format.
     * Returns the string value of the <code>xsi:schemaLocation</code>
     * attribute that should be applied to the generated XML.
     * <p>
     * It may return the empty string if no schema is known, but crosswalk
     * authors are strongly encouraged to implement this call so their output
     * XML can be validated correctly.
     *
     * @return SchemaLocation string, including URI namespace, followed by
     * whitespace and URI of XML schema document, or empty string if unknown.
     */
    @Override
    public String getSchemaLocation() {
        return "";
    }

    /**
     * Predicate: Can this disseminator crosswalk the given object.
     *
     * @param dso dspace object, e.g. an <code>Item</code>.
     * @return true when disseminator is capable of producing metadata.
     */
    @Override
    public boolean canDisseminate(DSpaceObject dso) {
        //We can only disseminate SITE, COMMUNITY or COLLECTION objects,
        //as Groups are only associated with those objects.
        return (dso.getType() == Constants.SITE ||
            dso.getType() == Constants.COMMUNITY ||
            dso.getType() == Constants.COLLECTION);
    }

    /**
     * Predicate: Does this disseminator prefer to return a list of Elements,
     * rather than a single root Element?
     *
     * @return true when disseminator prefers you call disseminateList().
     */
    @Override
    public boolean preferList() {
        //We prefer disseminators call 'disseminateElement()' instead of 'disseminateList()'
        return false;
    }

    /**
     * Execute crosswalk, returning List of XML elements.
     * Returns a <code>List</code> of JDOM <code>Element</code> objects representing
     * the XML produced by the crosswalk.  This is typically called when
     * a list of fields is desired, e.g. for embedding in a METS document
     * <code>xmlData</code> field.
     * <p>
     * When there are no results, an
     * empty list is returned, but never <code>null</code>.
     *
     * @param context context
     * @param dso     the  DSpace Object whose metadata to export.
     * @return results of crosswalk as list of XML elements.
     * @throws CrosswalkInternalException  (<code>CrosswalkException</code>) failure of the crosswalk itself.
     * @throws CrosswalkObjectNotSupported (<code>CrosswalkException</code>) Cannot crosswalk this kind of DSpace
     *                                     object.
     * @throws IOException                 I/O failure in services this calls
     * @throws SQLException                Database failure in services this calls
     * @throws AuthorizeException          current user not authorized for this operation.
     */
    @Override
    public List<Element> disseminateList(Context context, DSpaceObject dso)
        throws CrosswalkException, IOException, SQLException,
        AuthorizeException {
        Element dim = disseminateElement(context, dso);
        return dim.getChildren();
    }

    /**
     * Execute crosswalk, returning one XML root element as
     * a JDOM <code>Element</code> object.
     * This is typically the root element of a document.
     * <p>
     *
     * @param context context
     * @param dso     the  DSpace Object whose metadata to export.
     * @return root Element of the target metadata, never <code>null</code>
     * @throws CrosswalkInternalException  (<code>CrosswalkException</code>) failure of the crosswalk itself.
     * @throws CrosswalkObjectNotSupported (<code>CrosswalkException</code>) Cannot crosswalk this kind of DSpace
     *                                     object.
     * @throws IOException                 I/O failure in services this calls
     * @throws SQLException                Database failure in services this calls
     * @throws AuthorizeException          current user not authorized for this operation.
     */
    @Override
    public Element disseminateElement(Context context, DSpaceObject dso)
        throws CrosswalkException, IOException, SQLException,
        AuthorizeException {
        try {
            PackageDisseminator dip = (PackageDisseminator)
                CoreServiceFactory.getInstance().getPluginService()
                                  .getNamedPlugin(PackageDisseminator.class, ROLE_PACKAGER_PLUGIN);
            if (dip == null) {
                throw new CrosswalkInternalException(
                    "Cannot find a PackageDisseminator plugin named " + ROLE_PACKAGER_PLUGIN);
            }

            // Create a temporary file to disseminate into
            ConfigurationService configurationService
                    = DSpaceServicesFactory.getInstance().getConfigurationService();
            String tempDirectory = (configurationService.hasProperty("upload.temp.dir"))
                    ? configurationService.getProperty("upload.temp.dir")
                    : System.getProperty("java.io.tmpdir");
            File tempFile = File
                .createTempFile("RoleCrosswalkDisseminate" + dso.hashCode(), null, new File(tempDirectory));
            tempFile.deleteOnExit();

            // Initialize our packaging parameters
            PackageParameters pparams;
            if (this.getPackagingParameters() != null) {
                pparams = this.getPackagingParameters();
            } else {
                pparams = new PackageParameters();
            }

            //actually disseminate to our temp file.
            dip.disseminate(context, dso, pparams, tempFile);

            // if we ended up with a Zero-length output file,
            // this means dissemination was successful but had no results
            if (tempFile.exists() && tempFile.length() == 0) {
                return null;
            }

            try {
                //Try to parse our XML results (which were disseminated by the Packager)
                SAXBuilder builder = new SAXBuilder();
                Document xmlDocument = builder.build(tempFile);
                //If XML parsed successfully, return root element of doc
                if (xmlDocument != null && xmlDocument.hasRootElement()) {
                    return xmlDocument.getRootElement();
                } else {
                    return null;
                }
            } catch (JDOMException je) {
                throw new MetadataValidationException(
                    "Error parsing Roles XML (see wrapped error message for more details) ", je);
            }
        } catch (PackageException pe) {
            throw new CrosswalkInternalException(
                "Failed to export Roles via packager (see wrapped error message for more details) ", pe);
        }
    }

    // ---- Ingestion Methods -----------


    /**
     * Ingest a List of XML elements
     *
     * @param context                     context
     * @param dso                         DSpaceObject
     * @param metadata                    list of metadata
     * @param createMissingMetadataFields whether to create missing fields
     * @throws CrosswalkException if crosswalk error
     * @throws IOException        if IO error
     * @throws SQLException       if database error
     * @throws AuthorizeException if authorization error
     */
    @Override
    public void ingest(Context context, DSpaceObject dso, List<Element> metadata, boolean createMissingMetadataFields)
        throws CrosswalkException, IOException, SQLException, AuthorizeException {
        if (!metadata.isEmpty()) {
            ingest(context, dso, ((Element) metadata.get(0)).getParentElement(), createMissingMetadataFields);
        }
    }


    /**
     * Ingest a whole XML document, starting at specified root.
     * <P>
     * This essentially just wraps a call to the configured Role PackageIngester.
     *
     * @param context                     context
     * @param dso                         DSpaceObject
     * @param root                        root element
     * @param createMissingMetadataFields whether to create missing fields
     * @throws CrosswalkException if crosswalk error
     * @throws IOException        if IO error
     * @throws SQLException       if database error
     * @throws AuthorizeException if authorization error
     */
    @Override
    public void ingest(Context context, DSpaceObject dso, Element root, boolean createMissingMetadataFields)
        throws CrosswalkException, IOException, SQLException, AuthorizeException {
        if (dso.getType() != Constants.SITE &&
            dso.getType() != Constants.COMMUNITY &&
            dso.getType() != Constants.COLLECTION) {
            throw new CrosswalkObjectNotSupported("Role crosswalk only valid for Site, Community or Collection");
        }

        //locate our "DSPACE-ROLES" PackageIngester plugin
        PackageIngester sip = (PackageIngester)
            CoreServiceFactory.getInstance().getPluginService()
                              .getNamedPlugin(PackageIngester.class, ROLE_PACKAGER_PLUGIN);
        if (sip == null) {
            throw new CrosswalkInternalException("Cannot find a PackageIngester plugin named " + ROLE_PACKAGER_PLUGIN);
        }

        // Initialize our packaging parameters
        PackageParameters pparams;
        if (this.getPackagingParameters() != null) {
            pparams = this.getPackagingParameters();
        } else {
            pparams = new PackageParameters();
        }

        // Initialize our license info
        String license = null;
        if (this.getIngestionLicense() != null) {
            license = this.getIngestionLicense();
        }

        // Create a temporary file to ingest from
        ConfigurationService configurationService
                = DSpaceServicesFactory.getInstance().getConfigurationService();
        String tempDirectory = (configurationService.hasProperty("upload.temp.dir"))
                ? configurationService.getProperty("upload.temp.dir")
                : System.getProperty("java.io.tmpdir");
        File tempFile = File.createTempFile("RoleCrosswalkIngest" + dso.hashCode(), null, new File(tempDirectory));
        tempFile.deleteOnExit();
        FileOutputStream fileOutStream = null;
        try {
            fileOutStream = new FileOutputStream(tempFile);
            XMLOutputter writer = new XMLOutputter();
            writer.output(root, fileOutStream);
        } finally {
            if (fileOutStream != null) {
                fileOutStream.close();
            }
        }

        //Actually call the ingester
        try {
            sip.ingest(context, dso, tempFile, pparams, license);
        } catch (PackageException | WorkflowException e) {
            throw new CrosswalkInternalException(e);
        }
    }

}
