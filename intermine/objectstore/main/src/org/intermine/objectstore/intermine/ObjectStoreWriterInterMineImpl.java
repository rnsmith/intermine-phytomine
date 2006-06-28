package org.intermine.objectstore.intermine;

/*
 * Copyright (C) 2002-2005 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.ref.WeakReference;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.intermine.metadata.AttributeDescriptor;
import org.intermine.metadata.ClassDescriptor;
import org.intermine.metadata.CollectionDescriptor;
import org.intermine.metadata.FieldDescriptor;
import org.intermine.model.InterMineObject;
import org.intermine.objectstore.ObjectStore;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.objectstore.ObjectStoreWriter;
import org.intermine.objectstore.proxy.Lazy;
import org.intermine.objectstore.query.Query;
import org.intermine.sql.writebatch.Batch;
import org.intermine.sql.writebatch.BatchWriter;
import org.intermine.sql.writebatch.BatchWriterPostgresCopyImpl;
import org.intermine.util.CacheMap;
import org.intermine.util.DatabaseUtil;
import org.intermine.util.DynamicUtil;
import org.intermine.util.ShutdownHook;
import org.intermine.util.Shutdownable;
import org.intermine.util.TypeUtil;

import org.apache.log4j.Logger;

/**
 * An SQL-backed implementation of the ObjectStoreWriter interface, backed by
 * ObjectStoreInterMineImpl.
 *
 * @author Matthew Wakeling
 * @author Andrew Varley
 */
public class ObjectStoreWriterInterMineImpl extends ObjectStoreInterMineImpl
    implements ObjectStoreWriter, Shutdownable
{
    private static final Logger LOG = Logger.getLogger(ObjectStoreWriterInterMineImpl.class);
    protected Connection conn = null;
    protected boolean connInUse = false;
    protected ObjectStoreInterMineImpl os;
    protected int sequenceBase = 0;
    protected int sequenceOffset = SEQUENCE_MULTIPLE;
    protected Batch batch;
    protected String createSituation;
    protected String closeSituation;
    protected Map recentSequences;
    protected Map tableToInfo;
    protected Map tableToColNameArray;
    protected Map tableToCollections;
    protected String connectionTakenBy = null;

    protected static final int SEQUENCE_MULTIPLE = 1000000;
    /**
     * Constructor for this ObjectStoreWriter. This ObjectStoreWriter is bound to a single SQL
     * Connection, grabbed from the provided ObjectStore.
     *
     * @param os an ObjectStoreInterMineImpl
     * @throws ObjectStoreException if a problem occurs
     */
    public ObjectStoreWriterInterMineImpl(ObjectStore os) throws ObjectStoreException {
        super(null, ((ObjectStoreInterMineImpl) os).getSchema());
        this.os = (ObjectStoreInterMineImpl) os;
        db = this.os.db;
        everOptimise = false;
        try {
            conn = this.os.getConnection();
        } catch (SQLException e) {
            throw new ObjectStoreException("Could not obtain connection to database "
                                           + db.getURL() + "(user=" + db.getUser()
                                           + ")", e);
        }
        this.os.writers.add(this);
        ShutdownHook.registerObject(new WeakReference(this));
        Exception e = new Exception();
        e.fillInStackTrace();
        StringWriter message = new StringWriter();
        PrintWriter pw = new PrintWriter(message);
        e.printStackTrace(pw);
        pw.close();
        createSituation = message.toString();
        int index = createSituation.indexOf("at junit.framework.TestCase.runBare");
        createSituation = (index < 0 ? createSituation : createSituation.substring(0, index));
        recentSequences = Collections.synchronizedMap(new CacheMap(getClass().getName()
                    + " with sequence = " + sequenceNumber + ", model = \"" + model.getName()
                    + "\" recentSequences cache"));
        batch = new Batch(new BatchWriterPostgresCopyImpl());
        tableToInfo = new HashMap();
        tableToColNameArray = new HashMap();
        tableToCollections = new HashMap();
    }

    /**
     * Returns the log used by this objctstore.
     *
     * @return the log
     */
    public Writer getLog() {
        return os.getLog();
    }

    /**
     * Not implemented.
     *
     * @param log ignored
     */
    public void setLog(Writer log) {
        throw new UnsupportedOperationException("Cannot change the log on a writer");
    }

    /**
     * Not implemented.
     *
     * @param tableName ignored
     */
    public void setLogTableName(String tableName) {
        throw new UnsupportedOperationException("Cannot change the log table name on a writer");
    }

    /**
     * @see ObjectStoreInterMineImpl#getLogEverything()
     */
    public boolean getLogEverything() {
        return os.getLogEverything();
    }

    /**
     * @see ObjectStoreInterMineImpl#dbLog(long, long, long, long, long, Query, String)
     */
    protected void dbLog(long optimise, long estimated, long execute, long permitted, long convert,
            Query q, String sql) {
        os.dbLog(optimise, estimated, execute, permitted, convert, q, sql);
    }

    /**
     * Allows the changing of the BatchWriter that this ObjectStoreWriter uses.
     *
     * @param batchWriter the new BatchWriter - use BatchWriterSimpleImpl for writers likely to see
     * small batches, and optimised (eg BatchWriterPostgresCopyImpl) implementations for bulk-write
     * writers.
     * @throws ObjectStoreException if something goes wrong
     */
    public void setBatchWriter(BatchWriter batchWriter) throws ObjectStoreException {
        Connection c = null;
        try {
            c = getConnection(); // Must get connection - it is our concurrency control.
            batch.setBatchWriter(batchWriter);
        } catch (SQLException e) {
            throw new ObjectStoreException("Could not get connection to database", e);
        } finally {
            releaseConnection(c);
        }
    }

    /**
     * Set the cutoff value used to decide if a bag should be put in a table.
     *
     * @param minBagTableSize don't use a table to represent bags if the bag is smaller than this
     * value
     */
    public void setMinBagTableSize(int minBagTableSize) {
        os.setMinBagTableSize(minBagTableSize);
    }

    /**
     * Returns the cutoff value used to decide if a bag should be put in a table.
     *
     * @return an int
     */
    public int getMinBagTableSize() {
        return os.getMinBagTableSize();
    }

    /**
     * @see ObjectStoreInterMineImpl#getConnection()
     */
    public synchronized Connection getConnection() throws SQLException {
        int loops = 0;
        while (connInUse || (conn == null)) {
            if (conn == null) {
                throw new SQLException("This ObjectStoreWriter is closed");
            }
            /*Exception trace = new Exception();
            trace.fillInStackTrace();
            LOG.debug("Connection in use - entering wait", trace);*/
            if (loops > 100) {
                LOG.error("Waited for connection for 100 seconds - probably a deadlock"
                        + " - throwing exception");
                //LOG.error("The connection was taken out by stack trace: " + connectionTakenBy);
                throw new SQLException("This ObjectStoreWriter appears to be dead due to"
                        + " deadlock");
            } else if (loops > 1) {
                LOG.info("Waited for connection for " + loops + " seconds - perhaps there's"
                        + " a deadlock");
            } else {
                LOG.debug("Connection in use - entering wait");
            }
            try {
                wait(1000L);
            } catch (InterruptedException e) {
                // ignore
            }
            LOG.debug("Notified or timed out");
            loops++;
        }
        connInUse = true;
        
        //Exception trace = new Exception();
        //trace.fillInStackTrace();
        //StringWriter message = new StringWriter();
        //PrintWriter pw = new PrintWriter(message);
        //trace.printStackTrace(pw);
        //pw.println("In Thread " + Thread.currentThread().getName());
        //pw.flush();
        //connectionTakenBy = message.toString();
        //LOG.debug("getConnection returning connection");
        return conn;
    }

    /**
     * @see ObjectStoreInterMineImpl#releaseConnection(Connection)
     */
    public synchronized void releaseConnection(Connection c) {
        if ((conn == null) && (c != null)) {
            // In this situation, the writer has been closed while this operation was still
            // happening. We should return the Connection back to the ObjectStore.
            try {
                if (isInTransactionWithConnection(c)) {
                    abortTransactionWithConnection(c);
                    LOG.error("ObjectStoreWriterInterMineImpl closed in unfinished transaction"
                            + " - transaction aborted");
                }
            } catch (Exception e) {
                LOG.error("Exception caught when destroying transaction while closing"
                        + " ObjectStoreWriter", e);
            }
            try {
                batch.close(c);
            } catch (Exception e) {
                LOG.error("Exception caught when closing Batch while closing ObjectStoreWriter", e);
            }
            try {
                os.releaseConnection(c);
            } catch (Exception e) {
                // ignore
            }
        } else if (c == conn) {
            connInUse = false;
            //LOG.debug("Released connection - notifying");
            notify();
        } else if (c != null) {
            Exception trace = new Exception();
            trace.fillInStackTrace();
            LOG.warn("Attempt made to release the wrong connection", trace);
        }
    }

    /**
     * Overrides Object.finalize - release the connection back to the objectstore.
     */
    protected synchronized void finalize() {
        if (conn != null) {
            LOG.error("Garbage collecting open ObjectStoreWriterInterMineImpl with sequence = "
                    + sequenceNumber + " and Database " + os.getDatabase().getURL()
                    + ", createSituation: " + createSituation);
            try {
                close();
            } catch (ObjectStoreException e) {
                LOG.error("Exception while garbage-collecting ObjectStoreWriterInterMineImpl: "
                        + e);
            }
        }
    }

    /**
     * @see ObjectStoreWriter#close()
     */
    public synchronized void close() throws ObjectStoreException {
        if (conn == null) {
            // This writer is already closed
            throw new ObjectStoreException("This ObjectStoreWriter is already closed in situation: "
                    + closeSituation + ", present stack trace:");
        }
        Exception est = new Exception();
        est.fillInStackTrace();
        StringWriter message = new StringWriter();
        PrintWriter pw = new PrintWriter(message);
        est.printStackTrace(pw);
        pw.close();
        closeSituation = message.toString();
        int index = closeSituation.indexOf("at junit.framework.TestCase.runBare");
        closeSituation = (index < 0 ? closeSituation : closeSituation.substring(0, index));
        if (connInUse) {
            conn = null;
            throw new ObjectStoreException("Closed ObjectStoreWriter while it is being used. Note"
                    + " this writer will be automatically closed when the current operation"
                    + " finishes");
        } else {
            try {
                if (isInTransactionWithConnection(conn)) {
                    abortTransactionWithConnection(conn);
                    LOG.error("ObjectStoreWriterInterMineImpl closed in unfinished transaction"
                            + " - transaction aborted");
                }
            } catch (Exception e) {
                LOG.error("Exception caught when destroying transaction while closing"
                        + " ObjectStoreWriter", e);
            }
            try {
                batch.close(conn);
            } catch (Exception e) {
                LOG.error("Exception caught when closing Batch while closing ObjectStoreWriter", e);
            }
            try {
                os.releaseConnection(conn);
            } catch (Exception e) {
                // ignore
            }
            conn = null;
            connInUse = true;
            notifyAll();
        }
    }

    /**
     * @see ObjectStoreWriter#getObjectStore()
     */
    public ObjectStore getObjectStore() {
        return os;
    }

    /**
     * @see ObjectStoreWriter#store(InterMineObject)
     */
    public void store(InterMineObject o) throws ObjectStoreException {
        Connection c = null;
        try {
            c = getConnection();
            storeWithConnection(c, o);
        } catch (SQLException e) {
            throw new ObjectStoreException("Could not get connection to database", e);
        } finally {
            releaseConnection(c);
        }
    }

    /**
     * Performs store with a given Connection.
     *
     * @param c the Connection
     * @param o the object to store
     * @throws ObjectStoreException sometimes
     */
    protected void storeWithConnection(Connection c,
                                       InterMineObject o) throws ObjectStoreException {
        boolean wasInTransaction = isInTransactionWithConnection(c);
        if (!wasInTransaction) {
            beginTransactionWithConnection(c);
        }

        try {
            boolean doDeletes = populateIds(c, o);
            String xml = null;
            String objectClass = null;
            Set classDescriptors = model.getClassDescriptorsForClass(o.getClass());

            Iterator cldIter = classDescriptors.iterator();
            while (cldIter.hasNext()) {
                ClassDescriptor cld = (ClassDescriptor) cldIter.next();
                ClassDescriptor tableMaster = schema.getTableMaster(cld);
                String tableName = DatabaseUtil.getTableName(tableMaster);
                if (!schema.getMissingTables().contains(tableName.toLowerCase())) {
                    if (doDeletes) {
                        batch.deleteRow(c, tableName, "id", o.getId());
                    }
                }
            }
            int tablesWritten = 0;
            Iterator validFieldEntryIter = TypeUtil.getFieldInfos(o.getClass()).entrySet()
                .iterator();
            Set validFieldNames = new HashSet();
            while (validFieldEntryIter.hasNext()) {
                Map.Entry entry = (Map.Entry) validFieldEntryIter.next();
                String fieldName = (String) entry.getKey();
                TypeUtil.FieldInfo fieldInfo = (TypeUtil.FieldInfo) entry.getValue();
                if (!Collection.class.isAssignableFrom(fieldInfo.getType())) {
                    validFieldNames.add(fieldName);
                }
            }
            cldIter = classDescriptors.iterator();
            while (cldIter.hasNext()) {
                ClassDescriptor cld = (ClassDescriptor) cldIter.next();
                ClassDescriptor tableMaster = schema.getTableMaster(cld);
                TableInfo tableInfo = getTableInfo(tableMaster);
                Set collections = (Set) tableToCollections.get(cld.getName());
                if (collections == null) {
                    LOG.info("Generating cached metadata for ClassDescriptor " + cld.getName());
                    collections = new HashSet();
                    Iterator fieldIter = cld.getAllFieldDescriptors().iterator();
                    while (fieldIter.hasNext()) {
                        FieldDescriptor field = (FieldDescriptor) fieldIter.next();
                        if (field instanceof CollectionDescriptor) {
                            collections.add(field);
                        }
                    }
                    tableToCollections.put(cld.getName(), collections);
                }

                if (!schema.getMissingTables().contains(tableInfo.tableName.toLowerCase())) {
                    tablesWritten++;
                    if (schema.isFlatMode() && (!schema.isTruncated(schema.getTableMaster(cld)))
                            && (!(cld.getType().equals(o.getClass())))) {
                        Set decomposed = DynamicUtil.decomposeClass(o.getClass());
                        if (!((decomposed.size() == 1) && cld.getType().equals(decomposed.iterator()
                                        .next()))) {
                            throw new ObjectStoreException("Non-flat model heirarchy used in flat "
                                    + "mode. Cannot store object with classes = " + decomposed);
                        }
                    }
                    Object values[] = new Object[tableInfo.colNames.length];
                    Set fieldNamesWritten = new HashSet();
                    for (int colNo = 0; colNo < tableInfo.colNames.length; colNo++) {
                        Object value = null;
                        if ("class".equals(tableInfo.colNames[colNo])) {
                            value = cld.getName();
                        } else if ("objectClass".equals(tableInfo.colNames[colNo])) {
                            if (objectClass == null) {
                                Iterator objectClassIter = DynamicUtil.decomposeClass(o.getClass())
                                    .iterator();
                                StringBuffer sb = new StringBuffer();
                                boolean needComma = false;
                                while (objectClassIter.hasNext()) {
                                    if (needComma) {
                                        sb.append(" ");
                                    }
                                    needComma = true;
                                    sb.append(((Class) objectClassIter.next()).getName());
                                }
                                objectClass = sb.toString();
                            }
                            value = objectClass;
                        } else if ("OBJECT".equals(tableInfo.colNames[colNo])) {
                            if (xml == null) {
                                xml = NotXmlRenderer.render(o);
                            }
                            value = xml;
                        } else if (validFieldNames.contains(tableInfo.fieldNames[colNo])) {
                            value = TypeUtil.getFieldProxy(o, tableInfo.fieldNames[colNo]);
                            if (value instanceof Date) {
                                value = new Long(((Date) value).getTime());
                            }
                            if ((value instanceof InterMineObject)
                                    && (colNo >= tableInfo.referencesFrom)) {
                                value = ((InterMineObject) value).getId();
                            } else if ((value instanceof InterMineObject)
                                    || (colNo >= tableInfo.referencesFrom)) {
                                value = null;
                            }
                            fieldNamesWritten.add(tableInfo.fieldNames[colNo]);
                        } else {
                            FieldDescriptor fieldDescriptor = tableInfo.fields[colNo];
                            if (fieldDescriptor instanceof AttributeDescriptor) {
                                String fieldType = ((AttributeDescriptor) fieldDescriptor)
                                    .getType();
                                if ("boolean".equals(fieldType)) {
                                    value = Boolean.FALSE;
                                } else if ("short".equals(fieldType)) {
                                    value = new Short((short) 0);
                                } else if ("int".equals(fieldType)) {
                                    value = new Integer(0);
                                } else if ("long".equals(fieldType)) {
                                    value = new Long(0L);
                                } else if ("float".equals(fieldType)) {
                                    value = new Float(0.0F);
                                } else if ("double".equals(fieldType)) {
                                    value = new Double(0.0);
                                }
                            }
                        }
                        values[colNo] = value;
                    }
                    if (schema.isFlatMode()) {
                        Iterator validFieldNameIter = validFieldNames.iterator();
                        while (validFieldNameIter.hasNext()) {
                            String validFieldName = (String) validFieldNameIter.next();
                            if (!fieldNamesWritten.contains(validFieldName)) {
                                Set decomposed = DynamicUtil.decomposeClass(o.getClass());
                                throw new ObjectStoreException("Cannot store object " + decomposed
                                        + " - no column for field " + validFieldName + " in table "
                                        + tableInfo.tableName);
                            }
                        }
                    }
                    batch.addRow(c, tableInfo.tableName, o.getId(), tableInfo.colNames, values);
                }

                Iterator collectionIter = collections.iterator();
                while (collectionIter.hasNext()) {
                    CollectionDescriptor collection = (CollectionDescriptor) collectionIter.next();
                    Collection coll = (Collection) TypeUtil.getFieldValue(o, collection.getName());
                    boolean needToStoreCollection = true;

                    if (coll instanceof Lazy) {
                        ObjectStore testOS = ((Lazy) coll).getObjectStore();
                        if (testOS instanceof ObjectStoreWriter) {
                            testOS = ((ObjectStoreWriter) testOS).getObjectStore();
                        }
                        if (testOS.equals(getObjectStore())) {
                            needToStoreCollection = false;
                        }
                    }

                    if (needToStoreCollection) {
                        // Collection - if it's many to many, then write indirection table.
                        if (collection.relationType() == FieldDescriptor.M_N_RELATION) {
                            String indirectTableName =
                                DatabaseUtil.getIndirectionTableName(collection);
                            String inwardColumnName =
                                DatabaseUtil.getInwardIndirectionColumnName(collection);
                            String outwardColumnName =
                                DatabaseUtil.getOutwardIndirectionColumnName(collection);
                            boolean swap = (inwardColumnName.compareTo(outwardColumnName) > 0);
                            String indirColNames[] = (String []) tableToColNameArray
                                .get(indirectTableName);
                            if (indirColNames == null) {
                                indirColNames = new String[2];
                                indirColNames[0] = (swap ? inwardColumnName : outwardColumnName);
                                indirColNames[1] = (swap ? outwardColumnName : inwardColumnName);
                                tableToColNameArray.put(indirectTableName, indirColNames);
                            }
                            Iterator collIter = coll.iterator();
                            while (collIter.hasNext()) {
                                InterMineObject inCollection = (InterMineObject)
                                    collIter.next();
                                batch.addRow(c, indirectTableName, indirColNames[0],
                                             indirColNames[1],
                                             (swap ? o.getId() : inCollection.getId()).intValue(),
                                             (swap ? inCollection.getId() : o.getId()).intValue());
                            }
                        }
                    }
                }
            }
            if (tablesWritten < 1) {
                throw new ObjectStoreException("Object " + DynamicUtil.decomposeClass(o.getClass())
                        + " does not map onto any database table.");
            }
            invalidateObjectById(o.getId());
        } catch (SQLException e) {
            throw new ObjectStoreException("Error while storing", e);
        } catch (IllegalAccessException e) {
            throw new ObjectStoreException("Illegal access to value while storing", e);
        }

        if (!wasInTransaction) {
            commitTransactionWithConnection(c);
        }
    }

    /**
     * Produces metadata for a given table, caching it to save time.
     *
     * @param tableMaster the ClassDescriptor describing the table
     * @return a TableInfo object
     * @throws ObjectStoreException if something goes wrong
     */
    protected TableInfo getTableInfo(ClassDescriptor tableMaster)
        throws ObjectStoreException {
        String tableName = DatabaseUtil.getTableName(tableMaster);
        TableInfo retval = (TableInfo) tableToInfo.get(tableName);
        if (retval == null) {
            retval = new TableInfo();
            retval.tableName = tableName;
            LOG.info("Generating cached metadata for table " + tableName);
            DatabaseSchema.Fields allColumns = schema.getTableFields(tableMaster);
            int colCount = allColumns.getAttributes().size() + allColumns.getReferences().size();
            boolean isTruncated = schema.isTruncated(tableMaster);
            boolean hasObject = "InterMineObject".equals(tableName) || (!(schema.isMissingNotXml()
                        || schema.isFlatMode()));
            if (isTruncated) {
                if (schema.isFlatMode()) {
                    colCount++;
                }
                colCount++;
            }
            if (hasObject) {
                colCount++;
            }
            retval.colNames = new String[colCount];
            retval.fieldNames = new String[colCount];
            retval.fields = new FieldDescriptor[colCount];
            int colNo = 0;
            if (hasObject) {
                retval.colNames[colNo] = "OBJECT";
                colNo++;
            }
            if (isTruncated) {
                if (schema.isFlatMode()) {
                    retval.colNames[colNo] = "objectClass";
                    colNo++;
                }
                retval.colNames[colNo] = "class";
                colNo++;
            }
            Iterator fieldIter = allColumns.getAttributes().iterator();
            while (fieldIter.hasNext()) {
                FieldDescriptor field = (FieldDescriptor) fieldIter.next();
                retval.colNames[colNo] = DatabaseUtil.getColumnName(field);
                retval.fieldNames[colNo] = field.getName();
                retval.fields[colNo] = field;
                colNo++;
            }
            retval.referencesFrom = colNo;
            fieldIter = allColumns.getReferences().iterator();
            while (fieldIter.hasNext()) {
                FieldDescriptor field = (FieldDescriptor) fieldIter.next();
                retval.colNames[colNo] = DatabaseUtil.getColumnName(field);
                retval.fieldNames[colNo] = field.getName();
                retval.fields[colNo] = field;
                colNo++;
            }
            tableToInfo.put(tableName, retval);
        }
        return retval;
    }

    /**
     * Populates the object o with IDs.
     *
     * @param c a Connection with which to fetch more IDs
     * @param o the InterMineObject
     * @return true if the object will need to be deleted from the DB before a store
     * @throws SQLException if the database cannot produce a new ID
     * @throws IllegalAccessException if the ID field cannot be set
     */
    protected boolean populateIds(Connection c, InterMineObject o) throws SQLException,
              IllegalAccessException {
        boolean doDeletes = true;
        // Make sure this object has an ID
        if (o.getId() == null) {
            o.setId(getSerialWithConnection(c));
            doDeletes = false;
        } else {
            doDeletes = !recentSequences.containsKey(o.getId());
        }
        recentSequences.remove(o.getId());

        // Make sure all objects pointed to have IDs
        Map fieldInfos = TypeUtil.getFieldInfos(o.getClass());
        Iterator fieldIter = fieldInfos.entrySet().iterator();
        while (fieldIter.hasNext()) {
            Map.Entry fieldEntry = (Map.Entry) fieldIter.next();
            TypeUtil.FieldInfo fieldInfo = (TypeUtil.FieldInfo) fieldEntry.getValue();
            if (InterMineObject.class.isAssignableFrom(fieldInfo.getType())) {
                InterMineObject obj = (InterMineObject) TypeUtil.getFieldProxy(o,
                        fieldInfo.getName());
                if ((obj != null) && (obj.getId() == null)) {
                    obj.setId(getSerialWithConnection(c));
                }
            } else if (Collection.class.isAssignableFrom(fieldInfo.getType())) {
                Collection coll = (Collection) TypeUtil.getFieldValue(o, fieldInfo.getName());
                if (!(coll instanceof Lazy)) {
                    Iterator collIter = coll.iterator();
                    while (collIter.hasNext()) {
                        InterMineObject obj = (InterMineObject) collIter.next();
                        if (obj.getId() == null) {
                            obj.setId(getSerialWithConnection(c));
                        }
                    }
                }
            }
        }
        return doDeletes;
    }

    /**
     * Gets an ID number which is unique in the database.
     *
     * @return an Integer
     * @throws ObjectStoreException if a problem occurs
     */
    public Integer getSerial() throws ObjectStoreException {
        try {
            Connection c = null;
            try {
                c = getConnection();
                return getSerialWithConnection(c);
            } finally {
                releaseConnection(c);
            }
        } catch (SQLException e) {
            throw new ObjectStoreException("Error generating serial number", e);
        }
    }

    /**
     * Gets an ID number which is unique in the database, given a Connection.
     *
     * @param c the Connection
     * @return an Integer
     * @throws SQLException if a problem occurs
     */
    protected Integer getSerialWithConnection(Connection c) throws SQLException {
        if (sequenceOffset >= SEQUENCE_MULTIPLE) {
            long start = System.currentTimeMillis();
            sequenceOffset = 0;
            Statement s = c.createStatement();
            ResultSet r = s.executeQuery("SELECT nextval('serial');");
            //System//.out.println(getModel().getName()
            //        + ": Executed SQL: SELECT nextval('serial');");
            if (!r.next()) {
                throw new SQLException("No result while attempting to get a unique id");
            }
            long nextSequence = r.getLong(1);
            sequenceBase = (int) (nextSequence * SEQUENCE_MULTIPLE);
            long end = System.currentTimeMillis();
            LOG.info("Got new set of serial numbers - took " + (end - start) + " ms");
        }
        Integer retval = new Integer(sequenceBase + (sequenceOffset++));
        recentSequences.put(retval, retval);
        return retval;
    }

    /**
     * @see ObjectStoreWriter#delete(InterMineObject)
     */
    public void delete(InterMineObject o) throws ObjectStoreException {
        Connection c = null;
        try {
            c = getConnection();
            deleteWithConnection(c, o);
        } catch (SQLException e) {
            throw new ObjectStoreException("Could not get connection to database", e);
        } finally {
            releaseConnection(c);
        }
    }

    /**
     * Performs a delete, with a connection.
     *
     * @param c the Connection
     * @param o the object to delete
     * @throws ObjectStoreException sometimes
     */
    protected void deleteWithConnection(Connection c,
            InterMineObject o) throws ObjectStoreException {
        boolean wasInTransaction = isInTransactionWithConnection(c);
        if (!wasInTransaction) {
            beginTransactionWithConnection(c);
        }

        try {
            // Make sure this object has an ID
            if (o.getId() == null) {
                throw new IllegalArgumentException("Attempt to delete an object without an ID: "
                        + o.toString());
            }

            Set classDescriptors = model.getClassDescriptorsForClass(o.getClass());

            Iterator cldIter = classDescriptors.iterator();
            while (cldIter.hasNext()) {
                ClassDescriptor cld = (ClassDescriptor) cldIter.next();
                ClassDescriptor tableMaster = schema.getTableMaster(cld);
                String tableName = DatabaseUtil.getTableName(tableMaster);
                if (!schema.getMissingTables().contains(tableName.toLowerCase())) {
                    batch.deleteRow(c, tableName, "id", o.getId());
                }
            }
            invalidateObjectById(o.getId());
        } catch (SQLException e) {
            throw new ObjectStoreException("Error while deleting", e);
        }

        if (!wasInTransaction) {
            commitTransactionWithConnection(c);
        }
    }

    /**
     * @see ObjectStoreWriter#isInTransaction()
     */
    public boolean isInTransaction() throws ObjectStoreException {
        Connection c = null;
        try {
            c = getConnection();
            return isInTransactionWithConnection(c);
        } catch (SQLException e) {
            throw new ObjectStoreException("Could not get connection to database", e);
        } finally {
            releaseConnection(c);
        }
    }

    /**
     * Finds if we are in a transaction.
     *
     * @param c the Connection
     * @return true or false
     * @throws ObjectStoreException sometimes
     */
    protected boolean isInTransactionWithConnection(Connection c) throws ObjectStoreException {
        try {
            return !c.getAutoCommit();
        } catch (SQLException e) {
            throw new ObjectStoreException("Error finding transaction status", e);
        }
    }

    /**
     * @see ObjectStoreWriter#beginTransaction()
     */
    public void beginTransaction() throws ObjectStoreException {
        Connection c = null;
        try {
            c = getConnection();
            beginTransactionWithConnection(c);
        } catch (SQLException e) {
            throw new ObjectStoreException("Could not get connection to database", e);
        } finally {
            releaseConnection(c);
        }
    }

    /**
     * Begins a transaction.
     *
     * @param c the Connection
     * @throws ObjectStoreException if we are already in a transaction
     */
    protected void beginTransactionWithConnection(Connection c) throws ObjectStoreException {
        try {
            if (!c.getAutoCommit()) {
                throw new ObjectStoreException("beginTransaction called, but already in"
                        + " transaction");
            }
            c.setAutoCommit(false);
        } catch (SQLException e) {
            throw new ObjectStoreException("Error beginning transaction", e);
        }
    }

    /**
     * @see ObjectStoreWriter#commitTransaction()
     */
    public void commitTransaction() throws ObjectStoreException {
        Connection c = null;
        try {
            c = getConnection();
            commitTransactionWithConnection(c);
        } catch (SQLException e) {
            throw new ObjectStoreException("Could not get connection to database", e);
        } finally {
            releaseConnection(c);
        }
    }

    /**
     * Commits a transaction.
     *
     * @param c the Connection
     * @throws ObjectStoreException if we are not in a transaction
     */
    protected void commitTransactionWithConnection(Connection c) throws ObjectStoreException {
        try {
            batch.flush(c);
            if (c.getAutoCommit()) {
                throw new ObjectStoreException("commitTransaction called, but not in transaction");
            }
            c.commit();
            c.setAutoCommit(true);
            os.flushObjectById();
        } catch (SQLException e) {
            throw new ObjectStoreException("Error committing transaction", e);
        }
    }

    /**
     * @see ObjectStoreWriter#abortTransaction()
     */
    public void abortTransaction() throws ObjectStoreException {
        Connection c = null;
        try {
            c = getConnection();
            abortTransactionWithConnection(c);
        } catch (SQLException e) {
            throw new ObjectStoreException("Could not get connection to database", e);
        } finally {
            releaseConnection(c);
        }
    }

    /**
     * Aborts a transaction.
     *
     * @param c the Connection
     * @throws ObjectStoreException if we are not in a transaction
     */
    public void abortTransactionWithConnection(Connection c) throws ObjectStoreException {
        try {
            batch.clear();
            if (c.getAutoCommit()) {
                throw new ObjectStoreException("abortTransaction called, but not in transaction");
            }
            c.rollback();
            c.setAutoCommit(true);
            os.flushObjectById();
        } catch (SQLException e) {
            throw new ObjectStoreException("Error aborting transaction", e);
        }
    }

    /**
     * @see ObjectStoreInterMineImpl#execute(Query, int, int, boolean, boolean, int)
     *
     * This method is overridden in order to flush batches properly before the read.
     */
    public List execute(Query q, int start, int limit, boolean optimise, boolean explain,
            int sequence) throws ObjectStoreException {
        Connection c = null;
        try {
            c = getConnection();
            Set readTables = SqlGenerator.findTableNames(q, getSchema());
            batch.flush(c, readTables);
            return executeWithConnection(c, q, start, limit, optimise, explain, sequence);
        } catch (SQLException e) {
            throw new ObjectStoreException("Could not get connection to database", e);
        } finally {
            releaseConnection(c);
        }
    }

    /**
     * @see ObjectStoreInterMineImpl#generateSql(Connection, Query, int, int)
     *
     * Delegates to the parent ObjectStore
     */
    protected String generateSql(Connection c, Query q, int start, int limit)
        throws ObjectStoreException {
        return os.generateSql(c, q, start, limit);
    }

    /**
     * @see ObjectStoreInterMineImpl#count(Query, int)
     *
     * This method is overridden in order to flush batches properly before the read.
     */
    public int count(Query q, int sequence) throws ObjectStoreException {
        Connection c = null;
        try {
            c = getConnection();
            Set readTables = SqlGenerator.findTableNames(q, getSchema());
            batch.flush(c, readTables);
            return countWithConnection(c, q, sequence);
        } catch (SQLException e) {
            throw new ObjectStoreException("Could not get connection to database", e);
        } finally {
            releaseConnection(c);
        }
    }

    /**
     * @see ObjectStoreInterMineImpl#internalGetObjectById(Integer, Class)
     *
     * This method is overridden in order to flush matches properly before the read.
     */
    protected InterMineObject internalGetObjectById(Integer id,
            Class clazz) throws ObjectStoreException {
        if (schema.isFlatMode()) {
            return super.internalGetObjectById(id, clazz);
        }
        Connection c = null;
        try {
            c = getConnection();
            String readTable = SqlGenerator.tableNameForId(clazz, getSchema());
            batch.flush(c, Collections.singleton(readTable));
            return internalGetObjectByIdWithConnection(c, id, clazz);
        } catch (SQLException e) {
            throw new ObjectStoreException("Could not get connection to database", e);
        } finally {
            releaseConnection(c);
        }
    }

    /**
     * Called by the StatsShutdownHook on shutdown
     */
    public synchronized void shutdown() {
        if (conn != null) {
            LOG.error("Shutting down open ObjectStoreWriterInterMineImpl with sequence = "
                    + sequenceNumber + " and Database " + os.getDatabase().getURL()
                    + ", createSituation = " + createSituation);
            try {
                close();
            } catch (ObjectStoreException e) {
                LOG.error("Exception caught while shutting down ObjectStoreWriterInterMineImpl: "
                        + e);
            }
        }
    }

    /**
     * @see ObjectStore#isMultiConnection()
     */
    public boolean isMultiConnection() {
        return false;
    }

    private static class TableInfo
    {
        String tableName;
        String colNames[];
        String fieldNames[];
        FieldDescriptor fields[];
        int referencesFrom;
    }
}
