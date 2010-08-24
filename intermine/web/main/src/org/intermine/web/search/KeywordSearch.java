package org.intermine.web.search;

/*
 * Copyright (C) 2002-2010 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Vector;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.StopAnalyzer;
import org.apache.lucene.analysis.snowball.SnowballAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.IndexReader.FieldOption;
import org.apache.lucene.queryParser.MultiFieldQueryParser;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.queryParser.QueryParser.Operator;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermsFilter;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.Version;
import org.intermine.api.InterMineAPI;
import org.intermine.api.config.ClassKeyHelper;
import org.intermine.metadata.AttributeDescriptor;
import org.intermine.metadata.ClassDescriptor;
import org.intermine.metadata.FieldDescriptor;
import org.intermine.metadata.Model;
import org.intermine.model.InterMineObject;
import org.intermine.modelproduction.MetadataManager;
import org.intermine.modelproduction.MetadataManager.LargeObjectOutputStream;
import org.intermine.objectstore.ObjectStore;
import org.intermine.objectstore.intermine.ObjectStoreInterMineImpl;
import org.intermine.objectstore.query.BagConstraint;
import org.intermine.objectstore.query.ConstraintOp;
import org.intermine.objectstore.query.ConstraintSet;
import org.intermine.objectstore.query.ContainsConstraint;
import org.intermine.objectstore.query.Query;
import org.intermine.objectstore.query.QueryClass;
import org.intermine.objectstore.query.QueryCollectionReference;
import org.intermine.objectstore.query.QueryField;
import org.intermine.objectstore.query.QueryObjectReference;
import org.intermine.objectstore.query.Results;
import org.intermine.objectstore.query.ResultsRow;
import org.intermine.pathquery.PathException;
import org.intermine.sql.Database;
import org.intermine.util.DynamicUtil;
import org.intermine.util.ObjectPipe;
import org.intermine.util.TypeUtil;

import com.browseengine.bobo.api.BoboBrowser;
import com.browseengine.bobo.api.BoboIndexReader;
import com.browseengine.bobo.api.Browsable;
import com.browseengine.bobo.api.BrowseException;
import com.browseengine.bobo.api.BrowseRequest;
import com.browseengine.bobo.api.BrowseResult;
import com.browseengine.bobo.api.BrowseSelection;
import com.browseengine.bobo.api.FacetSpec;
import com.browseengine.bobo.api.FacetSpec.FacetSortSpec;
import com.browseengine.bobo.facets.FacetHandler;
import com.browseengine.bobo.facets.impl.MultiValueFacetHandler;
import com.browseengine.bobo.facets.impl.PathFacetHandler;
import com.browseengine.bobo.facets.impl.SimpleFacetHandler;

/**
 * container class to cache class attributes
 * @author nils
 */
class ClassAttributes
{
    String className;
    Set<AttributeDescriptor> attributes;

    /**
     * constructor
     * @param className
     *            name of the class
     * @param attributes
     *            set of attributes for the class
     */
    public ClassAttributes(String className, Set<AttributeDescriptor> attributes) {
        super();
        this.className = className;
        this.attributes = attributes;
    }

    /**
     * name of the class
     * @return name of the class
     */
    public String getClassName() {
        return className;
    }

    /**
     * attributes associated with the class
     * @return attributes associated with the class
     */
    public Set<AttributeDescriptor> getAttributes() {
        return attributes;
    }
}

/**
 * container for the lucene index to hold field list and directory
 * @author nils
 */
class LuceneIndexContainer implements Serializable
{
    private static final long serialVersionUID = 1L;
    private transient Directory directory;
    private String directoryType;
    private HashSet<String> fieldNames = new HashSet<String>();
    private HashMap<String, Float> fieldBoosts = new HashMap<String, Float>();

    /**
     * get lucene directory for this index
     * @return directory
     */
    public Directory getDirectory() {
        return directory;
    }

    /**
     * set lucene directory
     * @param directory
     *            directory
     */
    public void setDirectory(Directory directory) {
        this.directory = directory;
    }

    /**
     * get type of directory
     * @return 'FSDirectory' or 'RAMDirectory'
     */
    public String getDirectoryType() {
        return directoryType;
    }

    /**
     * set type of directory
     * @param directoryType
     *            class name of lucene directory
     */
    public void setDirectoryType(String directoryType) {
        this.directoryType = directoryType;
    }

    /**
     * get list of fields in the index
     * @return fields
     */
    public HashSet<String> getFieldNames() {
        return fieldNames;
    }

    /**
     * set list of fields in the index
     * @param fieldNames
     *            fields
     */
    public void setFieldNames(HashSet<String> fieldNames) {
        this.fieldNames = fieldNames;
    }

    /**
     * get list of boost associated with fields
     * @return boosts
     */
    public HashMap<String, Float> getFieldBoosts() {
        return fieldBoosts;
    }

    /**
     * set boost associated with fields
     * @param fieldBoosts
     *            boosts
     */
    public void setFieldBoosts(HashMap<String, Float> fieldBoosts) {
        this.fieldBoosts = fieldBoosts;
    }

    @Override
    public String toString() {
        return "INDEX [[" + directory + "" + ", fields = " + fieldNames + "" + ", boosts = "
                + fieldBoosts + "" + "]]";
    }
}

/**
 * container to hold results for a reference query and associated iterator
 * @author nils *
 */
class InterMineResultsContainer
{
    final Results results;
    final ListIterator<ResultsRow<InterMineObject>> iterator;

    /**
     * create container and set iterator
     * @param results
     *            result object from os.execute(query)
     */
    @SuppressWarnings("unchecked")
    public InterMineResultsContainer(Results results) {
        this.results = results;
        this.iterator = results.listIterator();
    }

    /**
     * the results
     * @return the results
     */
    public Results getResults() {
        return results;
    }

    /**
     * the iterator on the results
     * @return iterator
     */
    public ListIterator<ResultsRow<InterMineObject>> getIterator() {
        return iterator;
    }
}

/**
 * container class to hold the name and value of an attribute for an object to
 * be added as a field to the document
 * @author nils
 */
class ObjectValueContainer
{
    final String className;
    final String name;
    final String value;

    /**
     * constructor
     * @param className
     *            name of the class the attribute belongs to
     * @param name
     *            name of the field
     * @param value
     *            value of the field
     */
    public ObjectValueContainer(String className, String name, String value) {
        super();
        this.className = className;
        this.name = name;
        this.value = value;
    }

    /**
     * className
     * @return className
     */
    public String getClassName() {
        return className;
    }

    /**
     * name
     * @return name
     */
    public String getName() {
        return name;
    }

    /**
     * value
     * @return value
     */
    public String getValue() {
        return value;
    }

    /**
     * generate the name to be used as a field name in lucene
     * @return lowercase classname and field name
     */
    public String getLuceneName() {
        return (className + "_" + name).toLowerCase();
    }
}

/**
 * thread to fetch all intermineobjects (with exceptions) from database, create
 * a lucene document for them, add references (if applicable) and put the final
 * document in the indexing queue
 * @author nils
 */
class InterMineObjectFetcher extends Thread
{
    private static final Logger LOG = Logger.getLogger(InterMineObjectFetcher.class);

    final ObjectStore os;
    final Map<String, List<FieldDescriptor>> classKeys;
    final ObjectPipe<Document> indexingQueue;
    final Set<Class<? extends InterMineObject>> ignoredClasses;
    final Map<Class<? extends InterMineObject>, String[]> specialReferences;
    final Map<ClassDescriptor, Float> classBoost;
    final Vector<KeywordSearchFacetData> facets;

    final Map<Integer, Document> documents = new HashMap<Integer, Document>();
    final Set<String> fieldNames = new HashSet<String>();
    final Map<Class<?>, Vector<ClassAttributes>> decomposedClassesCache =
            new HashMap<Class<?>, Vector<ClassAttributes>>();

    Field idField = null;
    Field categoryField = null;

    /**
     * initialize the documentfetcher thread
     * @param os
     *            intermine objectstore
     * @param classKeys
     *            classKeys from InterMineAPI, map of classname to all key field
     *            descriptors
     * @param indexingQueue
     *            queue shared with indexer
     * @param ignoredClasses
     *            classes that should not be indexed (as specified in config +
     *            subclasses)
     * @param specialReferences
     *            map of classname to references to index in additional to
     *            normal attributes
     * @param classBoost
     *            apply per-class doc boost as specified here (all other classes
     *            get 1.0)
     * @param facets
     *            fields used for faceting - will be indexed untokenized in
     *            addition to the normal indexing
     */
    public InterMineObjectFetcher(ObjectStore os, Map<String, List<FieldDescriptor>> classKeys,
            ObjectPipe<Document> indexingQueue,
            Set<Class<? extends InterMineObject>> ignoredClasses,
            Map<Class<? extends InterMineObject>, String[]> specialReferences,
            Map<ClassDescriptor, Float> classBoost, Vector<KeywordSearchFacetData> facets) {
        super();

        this.os = os;
        this.classKeys = classKeys;
        this.indexingQueue = indexingQueue;
        this.ignoredClasses = ignoredClasses;
        this.specialReferences = specialReferences;
        this.classBoost = classBoost;
        this.facets = facets;
    }

    /**
     * get list of fields contained in the fetched documents
     * @return fields
     */
    public Set<String> getFieldNames() {
        return fieldNames;
    }

    /**
     * fetch objects from database, create documents and add them to the queue
     */
    @SuppressWarnings("unchecked")
    public void run() {
        try {
            long time = System.currentTimeMillis();
            long objectParseTime = 0;
            LOG.info("Fetching all InterMineObjects...");

            HashSet<Class<? extends InterMineObject>> seenClasses =
                    new HashSet<Class<? extends InterMineObject>>();
            HashMap<String, InterMineResultsContainer> referenceResults =
                    new HashMap<String, InterMineResultsContainer>();

            try {
                //query all objects except the ones we are ignoring
                Query q = new Query();
                QueryClass qc = new QueryClass(InterMineObject.class);
                q.addFrom(qc);
                q.addToSelect(qc);

                QueryField qf = new QueryField(qc, "class");
                q.setConstraint(new BagConstraint(qf, ConstraintOp.NOT_IN, ignoredClasses));

                LOG.info("QUERY: " + q.toString());

                Results results = os.execute(q, 0, true, false, true);

                ListIterator<ResultsRow<InterMineObject>> it = results.listIterator();
                int i = 0;
                int size = results.size();
                LOG.info("Query returned " + size + " results");

                //iterate over objects
                while (it.hasNext()) {
                    ResultsRow<InterMineObject> row = it.next();

                    if (i % 10000 == 1) {
                        LOG.info("IMOFetcher: fetched " + i + " of " + size + " in "
                                + (System.currentTimeMillis() - time) + "ms total, "
                                + (objectParseTime) + "ms spent on parsing");
                    }

                    for (InterMineObject object : row) {
                        long time2 = System.currentTimeMillis();

                        Set<Class> objectClasses = DynamicUtil.decomposeClass(object.getClass());
                        Class objectTopClass = objectClasses.iterator().next();
                        ClassDescriptor classDescriptor =
                                os.getModel().getClassDescriptorByName(objectTopClass.getName());

                        // create base doc for object
                        Document doc = createDocument(object, classDescriptor);

                        HashSet<String> references = new HashSet<String>();
                        HashMap<String, KeywordSearchFacetData> referenceFacetFields =
                                new HashMap<String, KeywordSearchFacetData>();

                        // find all references associated with this object or
                        // its superclasses
                        for (Entry<Class<? extends InterMineObject>, String[]> specialClass
                                : specialReferences
                                .entrySet()) {
                            for (Class<?> objectClass : objectClasses) {
                                if (specialClass.getKey().isAssignableFrom(objectClass)) {
                                    for (String reference : specialClass.getValue()) {
                                        String fullReference =
                                                classDescriptor.getUnqualifiedName() + "."
                                                        + reference;
                                        references.add(fullReference);

                                        //check if this reference returns a field we are
                                        //faceting by. if so, add it to referenceFacetFields
                                        for (KeywordSearchFacetData facet : facets) {
                                            for (String field : facet.getFields()) {
                                                if (field.startsWith(reference + ".")
                                                        && !field.substring(reference.length() + 1)
                                                                .contains(".")) {
                                                    referenceFacetFields.put(fullReference, facet);
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // if we have not seen an object of this class before, query references
                        if (!seenClasses.contains(object.getClass())) {
                            LOG.info("Getting references for new class: " + object.getClass());

                            // query all references that we need
                            for (String reference : references) {
                                // LOG.info("Querying reference " + reference);

                                Query queryReference = getPathQuery(reference);

                                // do not count this towards objectParseTime
                                objectParseTime += (System.currentTimeMillis() - time2);

                                Results resultsc = os.execute(queryReference, 0, true, false, true);
                                ((ObjectStoreInterMineImpl) os).goFaster(queryReference);
                                referenceResults.put(reference, new InterMineResultsContainer(
                                        resultsc));
                                LOG.info("Querying reference " + reference + " done -- "
                                        + resultsc.size() + " results");

                                // start counting objectParseTime again
                                time2 = System.currentTimeMillis();
                            }

                            seenClasses.add(object.getClass());
                        }

                        // find all references and add them
                        for (String reference : references) {
                            InterMineResultsContainer resultsContainer =
                                    referenceResults.get(reference);
                            //step through the reference results (ordered) while ref.id = obj.id
                            while (resultsContainer.getIterator().hasNext()) {
                                ResultsRow next = resultsContainer.getIterator().next();

                                //reference is not for the current object?
                                if (!next.get(0).equals(object.getId())) {
                                    // go back one step
                                    if (resultsContainer.getIterator().hasPrevious()) {
                                        resultsContainer.getIterator().previous();
                                    }

                                    break;
                                }

                                // add reference to doc
                                addObjectToDocument((InterMineObject) next.get(1), null, doc);

                                //check if this reference contains an attribute we need for a facet
                                KeywordSearchFacetData referenceFacet =
                                        referenceFacetFields.get(reference);
                                if (referenceFacet != null) {
                                    //handle PATH facets FIXME: UNTESTED!
                                    if (referenceFacet.getType() == KeywordSearchFacetType.PATH) {
                                        String virtualPathField =
                                                "path_" + referenceFacet.getName().toLowerCase();
                                        for (String field : referenceFacet.getFields()) {
                                            if (field.startsWith(reference + ".")) {
                                                String facetAttribute =
                                                        field.substring(field.lastIndexOf('.') + 1);
                                                Object facetValue =
                                                        TypeUtil.getFieldValue(
                                                                (InterMineObject) next.get(1),
                                                                facetAttribute);

                                                if (facetValue instanceof String
                                                        && !StringUtils
                                                                .isBlank((String) facetValue)) {
                                                    Field f = doc.getField(virtualPathField);

                                                    if (f != null) {
                                                        f.setValue(f.stringValue() + "/"
                                                                + facetValue);
                                                    } else {
                                                        doc.add(new Field(virtualPathField,
                                                                (String) facetValue,
                                                                Field.Store.NO,
                                                                Field.Index.NOT_ANALYZED_NO_NORMS));
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        //SINGLE/MULTI facet
                                        //add attribute to document a second time, but unstemmed
                                        //and with the field name corresponding to the facet name
                                        String facetAttribute =
                                                referenceFacet.getField()
                                                        .substring(
                                                                referenceFacet.getField()
                                                                        .lastIndexOf('.') + 1);
                                        Object facetValue =
                                                TypeUtil.getFieldValue((InterMineObject) next
                                                        .get(1), facetAttribute);

                                        if (facetValue instanceof String
                                                && !StringUtils.isBlank((String) facetValue)) {
                                            doc.add(new Field(referenceFacet.getField(),
                                                    (String) facetValue, Field.Store.NO,
                                                    Field.Index.NOT_ANALYZED_NO_NORMS));
                                        }
                                    }
                                }
                            }
                        }

                        // finally add doc to queue
                        indexingQueue.put(doc);

                        objectParseTime += (System.currentTimeMillis() - time2);
                    }

                    i++;
                }
            } finally {
                for (InterMineResultsContainer resultsContainer : referenceResults.values()) {
                    ((ObjectStoreInterMineImpl) os).releaseGoFaster(resultsContainer.getResults()
                            .getQuery());
                }
            }
        } catch (Exception e) {
            LOG.warn(null, e);
        }

        //notify main thread that we're done
        indexingQueue.finish();
    }

    private Document createDocument(InterMineObject object, ClassDescriptor classDescriptor) {
        Document doc = new Document();

        Float boost = classBoost.get(classDescriptor);
        if (boost != null) {
            doc.setBoost(boost);
        }

        // id has to be stored so we can fetch the actual objects for the
        // results
        doc.add(new Field("id", object.getId().toString(), Field.Store.YES,
                Field.Index.NOT_ANALYZED_NO_NORMS));

        // special case for faceting
        doc.add(new Field("Category", classDescriptor.getUnqualifiedName(), Field.Store.NO,
                Field.Index.NOT_ANALYZED_NO_NORMS));

        addToDocument(doc, "classname", classDescriptor.getUnqualifiedName(), 1F, false);

        addObjectToDocument(object, classDescriptor, doc);

        return doc;
    }

    private void addObjectToDocument(InterMineObject object, ClassDescriptor classDescriptor,
            Document doc) {
        Collection<String> keyFields;

        // if we know the class, get a list of key fields
        if (classDescriptor != null) {
            keyFields =
                    ClassKeyHelper
                            .getKeyFieldNames(classKeys, classDescriptor.getUnqualifiedName());
        } else {
            keyFields = Collections.emptyList();
        }

        Set<ObjectValueContainer> attributes = getAttributeMapForObject(os.getModel(), object);
        for (ObjectValueContainer attribute : attributes) {
            addToDocument(doc, attribute.getLuceneName(), attribute.getValue(), 1F, false);

            // index all key fields as raw data with a higher boost, favors
            // "exact matches"
            if (keyFields.contains(attribute.getName())) {
                addToDocument(doc, attribute.getLuceneName(), attribute.getValue(), 2F, true);
            }
        }
    }

    private Set<ObjectValueContainer> getAttributeMapForObject(Model model, Object obj) {
        Set<ObjectValueContainer> values = new HashSet<ObjectValueContainer>();
        Vector<ClassAttributes> decomposedClassAttributes =
                getClassAttributes(model, obj.getClass());

        for (ClassAttributes classAttributes : decomposedClassAttributes) {
            for (AttributeDescriptor att : classAttributes.getAttributes()) {
                try {
                    // only index strings
                    if ("java.lang.String".equals(att.getType())
                            || "java.lang.Integer".equals(att.getType())) {
                        Object value = TypeUtil.getFieldValue(obj, att.getName());

                        // ignore null values
                        if (value != null) {
                            String string = String.valueOf(value);

                            if (!string.startsWith("http://")) {
                                values.add(new ObjectValueContainer(classAttributes.getClassName(),
                                        att.getName(), string));
                            }
                        }
                    }
                } catch (IllegalAccessException e) {
                    LOG.warn("Error introspecting an object: " + obj, e);
                }
            }
        }

        return values;
    }

    private Field addToDocument(Document doc, String fieldName, String value, float boost,
            boolean raw) {
        if (!StringUtils.isBlank(fieldName) && !StringUtils.isBlank(value)) {
            Field f;

            if (!raw) {
                f = new Field(fieldName, value, Field.Store.NO, Field.Index.ANALYZED);
            } else {
                f =
                        new Field(fieldName + "_raw", value.toLowerCase(), Field.Store.NO,
                                Field.Index.NOT_ANALYZED);
            }

            f.setBoost(boost);

            doc.add(f);
            fieldNames.add(f.name());

            return f;
        }

        return null;
    }

    // simple caching of attributes
    private Vector<ClassAttributes> getClassAttributes(Model model, Class<?> baseClass) {
        Vector<ClassAttributes> attributes = decomposedClassesCache.get(baseClass);

        if (attributes == null) {
            LOG.info("decomposedClassesCache: No entry for " + baseClass + ", adding...");
            attributes = new Vector<ClassAttributes>();

            for (Class<?> cls : DynamicUtil.decomposeClass(baseClass)) {
                ClassDescriptor cld = model.getClassDescriptorByName(cls.getName());
                attributes.add(new ClassAttributes(cld.getUnqualifiedName(), cld
                        .getAllAttributeDescriptors()));
            }

            decomposedClassesCache.put(baseClass, attributes);
        }

        return attributes;
    }

    private Query getPathQuery(String pathString) throws PathException {
        Query q = new Query();
        ConstraintSet constraints = new ConstraintSet(ConstraintOp.AND);

        org.intermine.pathquery.Path path =
                new org.intermine.pathquery.Path(os.getModel(), pathString);
        List<ClassDescriptor> classDescriptors = path.getElementClassDescriptors();
        List<String> fieldNames = path.getElements();

        ClassDescriptor parentClassDescriptor = null;
        QueryClass parentQueryClass = null;

        for (int i = 0; i < classDescriptors.size(); i++) {
            ClassDescriptor classDescriptor = classDescriptors.get(i);

            Class<?> classInCollection = classDescriptor.getType();

            QueryClass queryClass = new QueryClass(classInCollection);
            q.addFrom(queryClass);

            if (i == 0) {
                // first class
                QueryField topId = new QueryField(queryClass, "id");
                q.addToSelect(topId);
                q.addToOrderBy(topId); // important for optimization in run()
            } else {
                String fieldName = fieldNames.get(i - 1);

                if (parentClassDescriptor.getReferenceDescriptorByName(fieldName, true) != null) {
                    LOG.info(parentClassDescriptor.getType().getSimpleName() + " -> " + fieldName
                            + " (OBJECT)");
                    QueryObjectReference objectReference =
                            new QueryObjectReference(parentQueryClass, fieldName);
                    ContainsConstraint cc =
                            new ContainsConstraint(objectReference, ConstraintOp.CONTAINS,
                                    queryClass);
                    constraints.addConstraint(cc);
                } else if (parentClassDescriptor.getCollectionDescriptorByName(fieldName, true)
                        != null) {
                    LOG.info(parentClassDescriptor.getType().getSimpleName() + " -> " + fieldName
                            + " (COLLECTION)");
                    QueryCollectionReference collectionReference =
                            new QueryCollectionReference(parentQueryClass, fieldName);
                    ContainsConstraint cc =
                            new ContainsConstraint(collectionReference, ConstraintOp.CONTAINS,
                                    queryClass);
                    constraints.addConstraint(cc);
                } else {
                    LOG.warn("Unknown field '" + parentClassDescriptor.getUnqualifiedName()
                            + "'::'" + fieldName + "' in path '" + pathString + "'!");
                }
            }

            parentClassDescriptor = classDescriptor;
            parentQueryClass = queryClass;
        }

        q.setConstraint(constraints);
        q.addToSelect(parentQueryClass); // select last class

        return q;
    }
}

/**
 * allows for full-text searches over all metadata using the apache lucene
 * engine
 * @author nils
 */
public class KeywordSearch
{
    private static final String LUCENE_INDEX_DIR = "keyword_search_index";

    /**
     * maximum number of hits returned
     */
    public static final int MAX_HITS = 500;

    /**
     * maximum number of items to be displayed on a page
     */
    public static final int PER_PAGE = 100;

    private static final Logger LOG = Logger.getLogger(KeywordSearch.class);

    private static IndexReader reader = null;
    private static BoboIndexReader boboIndexReader = null;
    private static ObjectPipe<Document> indexingQueue = new ObjectPipe<Document>(50000);
    private static LuceneIndexContainer index = null;

    private static Properties properties = null;
    private static Map<Class<? extends InterMineObject>, String[]> specialReferences;
    private static Set<Class<? extends InterMineObject>> ignoredClasses;
    private static Map<ClassDescriptor, Float> classBoost;
    private static Vector<KeywordSearchFacetData> facets;
    private static boolean debugOutput;

    @SuppressWarnings("unchecked")
    private static synchronized void parseProperties(ObjectStore os) {
        if (properties != null) {
            return;
        }

        specialReferences = new HashMap<Class<? extends InterMineObject>, String[]>();
        ignoredClasses = new HashSet<Class<? extends InterMineObject>>();
        classBoost = new HashMap<ClassDescriptor, Float>();
        facets = new Vector<KeywordSearchFacetData>();
        debugOutput = false;

        // load config file to figure out special classes
        String configFileName = "keyword_search.properties";
        ClassLoader classLoader = KeywordSearch.class.getClassLoader();
        InputStream configStream = classLoader.getResourceAsStream(configFileName);
        if (configStream != null) {
            properties = new Properties();
            try {
                properties.load(configStream);

                for (Map.Entry<Object, Object> entry : properties.entrySet()) {
                    String key = (String) entry.getKey();
                    String value = (String) entry.getValue();

                    if ("index.ignore".equals(key) && !StringUtils.isBlank(value)) {
                        String[] ignoreClassNames = value.split("\\s+");

                        for (String className : ignoreClassNames) {
                            ClassDescriptor cld = os.getModel().getClassDescriptorByName(className);

                            if (cld == null) {
                                LOG.error("Unknown class in config file: " + className);
                            } else {
                                addCldToIgnored(ignoredClasses, cld);
                            }
                        }
                    } else if (key.startsWith("index.references.")) {
                        String classToIndex = key.substring("index.references.".length());
                        ClassDescriptor cld = os.getModel().getClassDescriptorByName(classToIndex);
                        if (cld != null) {
                            Class<? extends InterMineObject> cls =
                                    (Class<? extends InterMineObject>) cld.getType();

                            // special fields (references to follow) come as
                            // a
                            // space-separated list
                            String[] specialFields;
                            if (!StringUtils.isBlank(value)) {
                                specialFields = value.split("\\s+");
                            } else {
                                specialFields = null;
                            }

                            specialReferences.put(cls, specialFields);
                        } else {
                            LOG.error("keyword_search.properties: classDescriptor for '"
                                    + classToIndex + "' not found!");
                        }
                    } else if (key.startsWith("index.facet.single.")) {
                        String facetName = key.substring("index.facet.single.".length());
                        String facetField = value;
                        facets.add(new KeywordSearchFacetData(facetField, facetName,
                                KeywordSearchFacetType.SINGLE));
                    } else if (key.startsWith("index.facet.multi.")) {
                        String facetName = key.substring("index.facet.multi.".length());
                        String facetField = value;
                        facets.add(new KeywordSearchFacetData(facetField, facetName,
                                KeywordSearchFacetType.MULTI));
                    } else if (key.startsWith("index.facet.path.")) {
                        String facetName = key.substring("index.facet.path.".length());
                        String[] facetFields = value.split(" ");
                        facets.add(new KeywordSearchFacetData(facetFields, facetName,
                                KeywordSearchFacetType.PATH));
                    } else if (key.startsWith("index.boost.")) {
                        String classToBoost = key.substring("index.boost.".length());
                        ClassDescriptor cld = os.getModel().getClassDescriptorByName(classToBoost);
                        if (cld != null) {
                            classBoost.put(cld, Float.valueOf(value));
                        } else {
                            LOG.error("keyword_search.properties: classDescriptor for '"
                                    + classToBoost + "' not found!");
                        }
                    } else if ("search.debug".equals(key) && !StringUtils.isBlank(value)) {
                        debugOutput =
                                "1".equals(value) || "true".equals(value.toLowerCase())
                                        || "on".equals(value.toLowerCase());
                    }
                }
            } catch (IOException e) {
                LOG.error("keyword_search.properties: errow while loading file '" + configFileName
                        + "'", e);
            }
        } else {
            LOG.error("keyword_search.properties: file '" + configFileName + "' not found!");
        }

        LOG.info("Indexing - Ignored classes:");
        for (Class<? extends InterMineObject> class1 : ignoredClasses) {
            LOG.info("- " + class1.getSimpleName());
        }

        LOG.info("Indexing - Special References:");
        for (Entry<Class<? extends InterMineObject>, String[]> specialReference : specialReferences
                .entrySet()) {
            LOG.info("- " + specialReference.getKey() + " = "
                    + Arrays.toString(specialReference.getValue()));
        }

        LOG.info("Indexing - Facets:");
        for (KeywordSearchFacetData facet : facets) {
            LOG.info("- field = " + facet.getField() + ", name = " + facet.getName() + ", type = "
                    + facet.getType().toString());
        }

        LOG.info("Search - Debug mode: " + debugOutput);
    }

    /**
     * loads or creates the lucene index
     * @param im
     *            API for accessing object store
     * @param path path to store the fsdirectory in
     */
    public static synchronized void initKeywordSearch(InterMineAPI im, String path) {
        try {

            if (index == null) {
                // try to load index from database first
                loadIndexFromDatabase(im.getObjectStore(), path);

                if (index == null) {
                    throw new RuntimeException("lucene index missing!");
                }
            }

            if (properties == null) {
                parseProperties(im.getObjectStore());
            }

            if (reader == null) {
                reader = IndexReader.open(index.getDirectory(), true);
            }

            if (boboIndexReader == null) {
                // prepare faceting
                HashSet<FacetHandler<?>> facetHandlers = new HashSet<FacetHandler<?>>();
                facetHandlers.add(new SimpleFacetHandler("Category"));
                for (KeywordSearchFacetData facet : facets) {
                    if (facet.getType().equals(KeywordSearchFacetType.MULTI)) {
                        facetHandlers.add(new MultiValueFacetHandler(facet.getField()));
                    } else if (facet.getType().equals(KeywordSearchFacetType.PATH)) {
                        facetHandlers.add(new PathFacetHandler("path_"
                                + facet.getName().toLowerCase()));
                    } else {
                        facetHandlers.add(new SimpleFacetHandler(facet.getField()));
                    }
                }

                boboIndexReader = BoboIndexReader.getInstance(reader, facetHandlers);

                LOG.info("Fields:"
                        + Arrays.toString(boboIndexReader.getFieldNames(FieldOption.ALL)
                                .toArray()));
                LOG.info("Indexed fields:"
                        + Arrays.toString(boboIndexReader.getFieldNames(FieldOption.INDEXED)
                                .toArray()));
            }
        } catch (CorruptIndexException e) {
            LOG.error(e);
        } catch (IOException e) {
            LOG.error(e);
        }
    }

    private static void writeObjectToDB(ObjectStore os, String key, Object object)
        throws IOException, SQLException {
        LOG.info("Saving stream to database...");
        Database db = ((ObjectStoreInterMineImpl) os).getDatabase();
        LargeObjectOutputStream streamOut = MetadataManager.storeLargeBinary(db, key);

        GZIPOutputStream gzipStream = new GZIPOutputStream(new BufferedOutputStream(streamOut));
        ObjectOutputStream objectStream = new ObjectOutputStream(gzipStream);

        LOG.info("GZipping and serializing object...");
        objectStream.writeObject(object);

        objectStream.flush();
        gzipStream.finish();
        gzipStream.flush();

        streamOut.close();
    }

    /**
     * writes index and associated directory to the database using the
     * metadatamanager
     * @param os
     *            intermine objectstore
     * @param classKeys
     *            map of classname to key field descriptors (from InterMineAPI)
     */
    public static void saveIndexToDatabase(ObjectStore os,
            Map<String, List<FieldDescriptor>> classKeys) {
        if (index == null) {
            createIndex(os, classKeys);
        }

        try {
            LOG.info("Saving search index information to database...");
            writeObjectToDB(os, MetadataManager.SEARCH_INDEX, index);
            LOG.info("Successfully saved search index information to database.");

            // if we have a FSDirectory we need to zip and save that separately
            if ("FSDirectory".equals(index.getDirectoryType())) {
                final int bufferSize = 2048;

                try {
                    LOG.info("Zipping up FSDirectory...");

                    Database db = ((ObjectStoreInterMineImpl) os).getDatabase();
                    LargeObjectOutputStream streamOut =
                            MetadataManager.storeLargeBinary(db,
                                    MetadataManager.SEARCH_INDEX_DIRECTORY);

                    ZipOutputStream zipOut =
                            new ZipOutputStream(new BufferedOutputStream(streamOut));

                    byte data[] = new byte[bufferSize];

                    // get a list of files from current directory
                    File fsDirectory = ((FSDirectory) index.getDirectory()).getFile();
                    String files[] = fsDirectory.list();

                    for (int i = 0; i < files.length; i++) {
                        File file =
                                new File(fsDirectory.getAbsolutePath() + File.separator + files[i]);
                        LOG.info("Zipping file: " + file.getName() + " (" + file.length() / 1024
                                / 1024 + " MB)");

                        FileInputStream fi = new FileInputStream(file);
                        BufferedInputStream fileInput = new BufferedInputStream(fi, bufferSize);

                        try {
                            ZipEntry entry = new ZipEntry(files[i]);
                            zipOut.putNextEntry(entry);

                            int count;
                            while ((count = fileInput.read(data, 0, bufferSize)) != -1) {
                                zipOut.write(data, 0, count);
                            }
                        } finally {
                            fileInput.close();
                        }
                    }

                    zipOut.close();
                } catch (IOException e) {
                    LOG.error(null, e);
                }
            } else if ("RAMDirectory".equals(index.getDirectoryType())) {
                LOG.info("Saving RAM directory to database...");
                writeObjectToDB(os, MetadataManager.SEARCH_INDEX_DIRECTORY, index.getDirectory());
                LOG.info("Successfully saved RAM directory to database.");
            }
        } catch (IOException e) {
            LOG.error(null, e);
        } catch (SQLException e) {
            LOG.error(null, e);
        }
    }

    /**
     * perform a keyword search over all document metadata fields with lucene
     * @param searchString
     *            string to search for
     * @return map of document IDs with their respective scores
     */
    @Deprecated
    public static Map<Integer, Float> runLuceneSearch(String searchString) {
        LinkedHashMap<Integer, Float> matches = new LinkedHashMap<Integer, Float>();

        String queryString = parseQueryString(searchString);

        long time = System.currentTimeMillis();

        try {
            IndexSearcher searcher = new IndexSearcher(reader);

            Analyzer analyzer =
                    new SnowballAnalyzer(Version.LUCENE_30, "English",
                            StopAnalyzer.ENGLISH_STOP_WORDS_SET);

            org.apache.lucene.search.Query query;

            // pass entire list of field names to the multi-field parser
            // => search through all fields
            String[] fieldNamesArray = new String[index.getFieldNames().size()];
            index.getFieldNames().toArray(fieldNamesArray);
            QueryParser queryParser =
                    new MultiFieldQueryParser(Version.LUCENE_30, fieldNamesArray, analyzer, index
                            .getFieldBoosts());
            query = queryParser.parse(queryString);

            // required to expand search terms
            query = query.rewrite(reader);
            LOG.debug("Actual query: " + query);

            TopDocs topDocs = searcher.search(query, 500);
            // Filter filter = new TermsFilter();
            // searcher.search(query, filter, collector);

            LOG.info("Found " + topDocs.totalHits + " document(s) that matched query '"
                    + queryString + "'");

            for (int i = 0; (i < MAX_HITS && i < topDocs.totalHits); i++) {
                Document doc = searcher.doc(topDocs.scoreDocs[i].doc);
                Integer id = Integer.valueOf(doc.get("id"));

                matches.put(id, new Float(topDocs.scoreDocs[i].score));
            }
        } catch (ParseException e) {
            // just return an empty list
            LOG.info("Exception caught, returning no results", e);
        } catch (IOException e) {
            // just return an empty list
            LOG.info("Exception caught, returning no results", e);
        }

        LOG.info("Lucene search finished in " + (System.currentTimeMillis() - time) + " ms");

        return matches;
    }

    /**
     * perform a keyword search using bobo-browse for faceting and pagination
     * @param searchString
     *            string to search for
     * @param offset
     *            display offset
     * @param facetValues
     *            map of 'facet field name' to 'value to restrict field to'
     *            (optional)
     * @param ids ids to research the search to (for search in list)
     * @return bobo browse result or null if failed
     */
    public static BrowseResult runBrowseSearch(String searchString, int offset,
            Map<String, String> facetValues, List<Integer> ids) {
        BrowseResult result = null;
        long time = System.currentTimeMillis();

        String queryString = parseQueryString(searchString);

        try {
            Analyzer analyzer =
                    new SnowballAnalyzer(Version.LUCENE_30, "English",
                            StopAnalyzer.ENGLISH_STOP_WORDS_SET);

            org.apache.lucene.search.Query query;

            // pass entire list of field names to the multi-field parser
            // => search through all fields
            String[] fieldNamesArray = new String[index.getFieldNames().size()];
            index.getFieldNames().toArray(fieldNamesArray);
            QueryParser queryParser =
                    new MultiFieldQueryParser(Version.LUCENE_30, fieldNamesArray, analyzer);
            queryParser.setDefaultOperator(Operator.AND);
            query = queryParser.parse(queryString);

            // required to expand search terms
            query = query.rewrite(reader);

            if (debugOutput) {
                LOG.info("Rewritten query: " + query);
            }

            // initialize request
            BrowseRequest browseRequest = new BrowseRequest();
            if (debugOutput) {
                browseRequest.setShowExplanation(true);
            }
            browseRequest.setQuery(query);
            browseRequest.setFetchStoredFields(true);

            if (ids != null && !ids.isEmpty()) {
                TermsFilter idFilter = new TermsFilter(); //we may want fieldcachetermsfilter

                for (int id : ids) {
                    idFilter.addTerm(new Term("id", Integer.toString(id)));
                }

                browseRequest.setFilter(idFilter);
            }

            // pagination
            browseRequest.setOffset(offset);
            browseRequest.setCount(PER_PAGE);

            // add faceting selections
            for (Entry<String, String> facetValue : facetValues.entrySet()) {
                if (facetValue != null) {
                    BrowseSelection browseSelection = new BrowseSelection(facetValue.getKey());
                    browseSelection.addValue(facetValue.getValue());
                    browseRequest.addSelection(browseSelection);
                }
            }

            // order faceting results by hits
            FacetSpec orderByHitsSpec = new FacetSpec();
            orderByHitsSpec.setOrderBy(FacetSortSpec.OrderHitsDesc);
            browseRequest.setFacetSpec("Category", orderByHitsSpec);
            for (KeywordSearchFacetData facet : facets) {
                browseRequest.setFacetSpec(facet.getField(), orderByHitsSpec);
            }

            LOG.debug("Prepared browserequest in " + (System.currentTimeMillis() - time) + " ms");
            time = System.currentTimeMillis();

            // execute query and return result
            Browsable browser = new BoboBrowser(boboIndexReader);
            result = browser.browse(browseRequest);

            if (debugOutput) {
                for (int i = 0; i < result.getHits().length && i < 5; i++) {
                    Explanation expl = result.getHits()[i].getExplanation();
                    if (expl != null) {
                        LOG.info(result.getHits()[i].getStoredFields().getFieldable("id")
                                + " - score explanation: " + expl.toString());
                    }
                }
            }
        } catch (ParseException e) {
            // just return an empty list
            LOG.info("Exception caught, returning no results", e);
        } catch (IOException e) {
            // just return an empty list
            LOG.info("Exception caught, returning no results", e);
        } catch (BrowseException e) {
            // just return an empty list
            LOG.info("Exception caught, returning no results", e);
        }

        LOG.debug("Bobo browse finished in " + (System.currentTimeMillis() - time) + " ms");

        return result;
    }

    private static String parseQueryString(String queryString) {
        queryString = queryString.replaceAll("\\b(\\s+)\\+(\\s+)\\b", "$1AND$2");
        queryString = queryString.replaceAll("(^|\\s+)'(\\b[^']+ [^']+\\b)'(\\s+|$)", "$1\"$2\"$3");

        return queryString;
    }

    private static void loadIndexFromDatabase(ObjectStore os, String path) {
        long time = System.currentTimeMillis();
        LOG.info("Attempting to restore search index from database...");
        if (os instanceof ObjectStoreInterMineImpl) {
            Database db = ((ObjectStoreInterMineImpl) os).getDatabase();
            try {
                InputStream is = MetadataManager.readLargeBinary(db, MetadataManager.SEARCH_INDEX);

                if (is != null) {
                    GZIPInputStream gzipInput = new GZIPInputStream(is);
                    ObjectInputStream objectInput = new ObjectInputStream(gzipInput);

                    try {
                        Object object = objectInput.readObject();

                        if (object instanceof LuceneIndexContainer) {
                            index = (LuceneIndexContainer) object;

                            LOG.info("Successfully restored search index information"
                                    + " from database in " + (System.currentTimeMillis() - time)
                                    + " ms");
                            LOG.info("Index: " + index);
                        } else {
                            LOG.warn("Object from DB has wrong class:"
                                    + object.getClass().getName());
                        }
                    } finally {
                        objectInput.close();
                        gzipInput.close();
                    }
                } else {
                    LOG.warn("IS is null");
                }

                if (index != null) {
                    time = System.currentTimeMillis();
                    LOG.info("Attempting to restore search directory from database...");
                    is =
                            MetadataManager.readLargeBinary(db,
                                    MetadataManager.SEARCH_INDEX_DIRECTORY);

                    if (is != null) {
                        if ("FSDirectory".equals(index.getDirectoryType())) {
                            final int bufferSize = 2048;
                            File directoryPath = new File(path + File.separator + LUCENE_INDEX_DIR);
                            LOG.info("Directory path: " + directoryPath);

                            // make sure we start with a new index
                            if (directoryPath.exists()) {
                                String files[] = directoryPath.list();
                                for (int i = 0; i < files.length; i++) {
                                    LOG.info("Deleting old file: " + files[i]);
                                    new File(directoryPath.getAbsolutePath() + File.separator
                                            + files[i]).delete();
                                }
                            } else {
                                directoryPath.mkdir();
                            }

                            ZipInputStream zis = new ZipInputStream(is);
                            ZipEntry entry;
                            while ((entry = zis.getNextEntry()) != null) {
                                LOG.info("Extracting: " + entry.getName() + " (" + entry.getSize()
                                        + " MB)");

                                FileOutputStream fos =
                                        new FileOutputStream(directoryPath.getAbsolutePath()
                                                + File.separator + entry.getName());
                                BufferedOutputStream bos =
                                        new BufferedOutputStream(fos, bufferSize);

                                int count;
                                byte data[] = new byte[bufferSize];

                                while ((count = zis.read(data, 0, bufferSize)) != -1) {
                                    bos.write(data, 0, count);
                                }

                                bos.flush();
                                bos.close();
                            }

                            FSDirectory directory = FSDirectory.open(directoryPath);
                            index.setDirectory(directory);

                            LOG.info("Successfully restored FS directory from database in "
                                    + (System.currentTimeMillis() - time) + " ms");
                            time = System.currentTimeMillis();
                        } else if ("RAMDirectory".equals(index.getDirectoryType())) {
                            GZIPInputStream gzipInput = new GZIPInputStream(is);
                            ObjectInputStream objectInput = new ObjectInputStream(gzipInput);

                            try {
                                Object object = objectInput.readObject();

                                if (object instanceof FSDirectory) {
                                    RAMDirectory directory = (RAMDirectory) object;
                                    index.setDirectory(directory);

                                    time = System.currentTimeMillis() - time;
                                    LOG.info("Successfully restored RAM directory"
                                            + " from database in " + time + " ms");
                                }
                            } finally {
                                objectInput.close();
                                gzipInput.close();
                            }
                        } else {
                            LOG.warn("Unknown directory type specified: "
                                    + index.getDirectoryType());
                        }

                        LOG.info("Directory: " + index.getDirectory());
                    } else {
                        LOG.warn("index is null!");
                    }
                }
            } catch (ClassNotFoundException e) {
                LOG.error("Could not load search index", e);
            } catch (SQLException e) {
                LOG.error("Could not load search index", e);
            } catch (IOException e) {
                LOG.error("Could not load search index", e);
            }
        } else {
            LOG.error("ObjectStore is of wrong type!");
        }
    }

    private static void createIndex(ObjectStore os, Map<String, List<FieldDescriptor>> classKeys) {
        long time = System.currentTimeMillis();
        LOG.info("Indexing metadata...");

        parseProperties(os);

        LOG.info("Starting fetcher thread...");
        InterMineObjectFetcher fetchThread =
                new InterMineObjectFetcher(os, classKeys, indexingQueue, ignoredClasses,
                        specialReferences, classBoost, facets);
        fetchThread.start();

        // index the docs queued by the fetchers
        LOG.info("Preparing indexer...");
        index = new LuceneIndexContainer();
        try {
            File directoryPath = new File(LUCENE_INDEX_DIR);
            index.setDirectory(FSDirectory.open(directoryPath));
            index.setDirectoryType("FSDirectory");

            // make sure we start with a new index
            if (directoryPath.exists()) {
                String files[] = directoryPath.list();
                for (int i = 0; i < files.length; i++) {
                    LOG.info("Deleting old file: " + files[i]);
                    new File(directoryPath.getAbsolutePath() + File.separator + files[i]).delete();
                }
            }
        } catch (IOException e) {
            LOG.error("Could not create index directory, using RAM!", e);
            index.setDirectory(new RAMDirectory());
            index.setDirectoryType("RAMDirectory");
        }

        IndexWriter writer;
        try {
            SnowballAnalyzer snowballAnalyzer =
                    new SnowballAnalyzer(Version.LUCENE_30, "English",
                            StopAnalyzer.ENGLISH_STOP_WORDS_SET);
            writer =
                    new IndexWriter(index.getDirectory(), snowballAnalyzer, true,
                            IndexWriter.MaxFieldLength.UNLIMITED);

            int indexed = 0;

            // loop and index while we still have fetchers running
            LOG.info("Starting to index...");
            while (indexingQueue.hasNext()) {
                Document doc = indexingQueue.next();

                // nothing in the queue?
                if (doc != null) {
                    try {
                        writer.addDocument(doc);
                        indexed++;
                    } catch (IOException e) {
                        LOG.error("Failed to submit #" + doc.getFieldable("id") + " to the index",
                                e);
                    }

                    if (indexed % 10000 == 1) {
                        LOG.info("docs indexed=" + indexed + "; thread state="
                                + fetchThread.getState() + "; docs/ms=" + indexed * 1.0F
                                / (System.currentTimeMillis() - time) + "; memory="
                                + Runtime.getRuntime().freeMemory() / 1024 + "k/"
                                + Runtime.getRuntime().maxMemory() / 1024 + "k" + "; time="
                                + (System.currentTimeMillis() - time) + "ms");
                    }
                }
            }

            index.getFieldNames().addAll(fetchThread.getFieldNames());

            LOG.info("Indexing done, optimizing index files...");

            try {
                writer.optimize();
                writer.close();
            } catch (IOException e) {
                LOG.error("IOException while optimizing and closing IndexWriter", e);
            }

            time = System.currentTimeMillis() - time;
            int seconds = (int) Math.floor(time / 1000);
            LOG.info("Indexing of "
                    + indexed
                    + " documents finished in "
                    + String.format("%02d:%02d.%03d", (int) Math.floor(seconds / 60), seconds % 60,
                            time % 1000) + " minutes");
        } catch (IOException err) {
            throw new RuntimeException("Failed to create lucene IndexWriter", err);
        }
    }

    /**
     * recurse into class descriptor and add all subclasses to ignoredClasses
     * @param ignoredClasses
     *            set of classes
     * @param cld
     *            super class descriptor
     */
    @SuppressWarnings("unchecked")
    private static void addCldToIgnored(Set<Class<? extends InterMineObject>> ignoredClasses,
            ClassDescriptor cld) {
        if (cld == null) {
            LOG.error("cld is null!");
        } else if (InterMineObject.class.isAssignableFrom(cld.getType())) {
            ignoredClasses.add((Class<? extends InterMineObject>) cld.getType());

            for (ClassDescriptor subCld : cld.getSubDescriptors()) {
                addCldToIgnored(ignoredClasses, subCld);
            }
        } else {
            LOG.error("cld " + cld + " is not IMO!");
        }
    }

    /**
     * get list of facet fields and names
     * @return map of internal fieldname -> displayed name
     */
    public static Vector<KeywordSearchFacetData> getFacets() {
        return facets;
    }
}