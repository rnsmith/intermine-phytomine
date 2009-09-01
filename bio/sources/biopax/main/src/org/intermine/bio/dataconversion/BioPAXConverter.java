package org.intermine.bio.dataconversion;

/*
 * Copyright (C) 2002-2009 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.collections.keyvalue.MultiKey;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.biopax.paxtools.controller.PropertyEditor;
import org.biopax.paxtools.controller.Traverser;
import org.biopax.paxtools.controller.Visitor;
import org.biopax.paxtools.io.jena.JenaIOHandler;
import org.biopax.paxtools.io.simpleIO.SimpleEditorMap;
import org.biopax.paxtools.model.BioPAXElement;
import org.biopax.paxtools.model.BioPAXLevel;
import org.biopax.paxtools.model.Model;
import org.biopax.paxtools.model.level2.pathway;
import org.intermine.bio.util.OrganismData;
import org.intermine.bio.util.OrganismRepository;
import org.intermine.dataconversion.ItemWriter;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.xml.full.Item;
/**
 * 
 * @author
 */
public class BioPAXConverter extends BioFileConverter implements Visitor
{
    private static final Logger LOG = Logger.getLogger(BioPAXConverter.class);
    private static final String PROP_FILE = "biopax_config.properties";
    private static final String DATASET_TITLE = "BioPAX";
    private static final String DATA_SOURCE_NAME = "BioPAX data set";
    private static final String NAMESPACE = "Reactome";
    private static final String DEFAULT_SOURCE = "UniProt";
    private Map<String, String> pathways = new HashMap();
    private Map<String, Item> proteins = new HashMap();
    private Traverser traverser;
    private Set<BioPAXElement> visited = new HashSet();
    private int depth=0;
    private Item organism;
    private String pathwayRefId = null;
    private List<MultiKey> synonyms = new ArrayList(); 
    private Set<String> taxonIds = null;
    private Map<String, String> sources = new HashMap();
    private OrganismRepository or;
    private String source = null;
    int nopathwaycount = 0;
    
    /**
     * Constructor
     * @param writer the ItemWriter used to handle the resultant items
     * @param intermineModel the Model
     * @throws ObjectStoreException if organism can't be stored
     */
    public BioPAXConverter(ItemWriter writer, org.intermine.metadata.Model intermineModel) {
        super(writer, intermineModel, DATA_SOURCE_NAME, DATASET_TITLE);
        traverser = new Traverser(new SimpleEditorMap(BioPAXLevel.L2), this);
        readConfig();
        or = OrganismRepository.getOrganismRepository();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void process(@SuppressWarnings("unused") Reader reader) throws Exception {
        
        File file = getCurrentFile();
        String filename = file.getName();
        String[] bits = filename.split(" ");
        
        String genus = bits[0];
        String species = bits[1].split("\\.")[0];
        
        String organismName = genus + " " + species;
        OrganismData od = or.getOrganismDataByGenusSpecies(genus, species);
        if (od == null) {
            LOG.error("no data for " + organismName + ".  Please add to repository.");
        }
        int taxonId = od.getTaxonId();
        String taxonIdString = String.valueOf(taxonId);
        
        // only process the taxonids set in the project XML file
        if (!taxonIds.contains(taxonIdString)) {
            return;
        }
        
        organism = createItem("Organism");
        organism.setAttribute("name", organismName);
        organism.setAttribute("taxonId", taxonIdString);
        try {
            store(organism);
        } catch (ObjectStoreException e) {
            throw new ObjectStoreException(e);
        }
        
        source = sources.get(taxonIdString);
        if (source == null) {
            source = DEFAULT_SOURCE;
        }
        
        JenaIOHandler jenaIOHandler = new JenaIOHandler(null, BioPAXLevel.L2);
        Model model = jenaIOHandler.convertFromOWL(new FileInputStream(file));
        Set<pathway> pathwaySet = model.getObjects(pathway.class);
        for (pathway pathwayObj : pathwaySet) {
            // System.out.println("PATHWAY: "+ pathway.getNAME());
            visited= new HashSet();
            traverser.traverse(pathwayObj, model);
        }
    }

    /**
     * Sets the list of taxonIds that should be imported if using split input files.
     *
     * @param taxonIds a space-separated list of taxonIds
     */
    public void setBioPAXOrganisms(String taxonIds) {
        this.taxonIds = new HashSet<String>(Arrays.asList(StringUtils.split(taxonIds, " ")));
        LOG.info("Setting list of organisms to " + this.taxonIds);
    }
    
    private void readConfig() {
        Properties props = new Properties();
        try {
            props.load(getClass().getClassLoader().getResourceAsStream(PROP_FILE));
        } catch (IOException e) {
            throw new RuntimeException("Problem loading properties '" + PROP_FILE + "'", e);
        }
        for (Map.Entry<Object, Object> entry: props.entrySet()) {
            String key = (String) entry.getKey();
            String value = ((String) entry.getValue()).trim();
            sources.put(key, value);
        }
    }
    
    
    private String getPathway(org.biopax.paxtools.model.level2.pathway pathway) 
    throws ObjectStoreException {
        String rdfId = pathway.getRDFId();
        String refId = pathways.get(rdfId);
        if (refId == null) {
            Item item = createItem("Pathway");
            item.setAttribute("name", pathway.getNAME());
            Set<org.biopax.paxtools.model.level2.xref> xrefs = pathway.getXREF();
            for (org.biopax.paxtools.model.level2.xref xref : xrefs) {
                String xrefId = xref.getRDFId();
                if (xrefId.contains(NAMESPACE)) {
                    String identifier = StringUtils.substringAfter(xrefId, NAMESPACE);
                    item.setAttribute("identifier", identifier);
                }
            }
            try {
                store(item);
            } catch (ObjectStoreException e) {
                throw new ObjectStoreException(e);
            }
            refId = item.getIdentifier();
            pathways.put(rdfId, refId);
        }
        return refId;
    }
    
    private void getProtein(String identifier, String pathway, String identifierSource) {
        String acc = StringUtils.substringAfter(identifier, identifierSource + "_");
        String accession = acc.replace("_", "-");
        
        Item item = proteins.get(accession);
        if (item == null) {
            item = createItem("Protein");
            item.setAttribute("primaryAccession", accession);
            item.setReference("organism", organism);
            proteins.put(accession, item);
            try {
                setSynonym(item.getIdentifier(), accession);
            } catch (ObjectStoreException e) {
                return;
            }
        }
        item.addToCollection("pathways", pathway);
        return;
    }
    
    /**
     * Adds the BioPAX element into the model and traverses the element for its dependent elements.
     *
     * @param bpe    the BioPAX element to be added into the model
     * @param model  model into which the element will be added
     * @param editor editor that is going to be used for traversing functionallity
     * @see org.biopax.paxtools.controller.Traverser
     */
    public void visit(BioPAXElement bpe, Model model, @SuppressWarnings("unused") 
                      PropertyEditor editor) {
        if (bpe != null) {
            if (bpe instanceof org.biopax.paxtools.model.level2.entity) {
                org.biopax.paxtools.model.level2.entity entity = (org.biopax.paxtools.model.level2.entity) bpe;
                String className = entity.getModelInterface().getSimpleName();
                if (className.equalsIgnoreCase("PATHWAY")) {
                    try {
                        pathwayRefId = getPathway((org.biopax.paxtools.model.level2.pathway) entity);
                    } catch  (ObjectStoreException e) {
                        return;
                    }
                }
                if (className.equalsIgnoreCase("protein") && StringUtils.isNotEmpty(pathwayRefId)) {
                    String accession = entity.getRDFId();
                    if (accession.contains(source)) {
                        getProtein(accession, pathwayRefId, source);                        
                    } else if (accession.contains(DEFAULT_SOURCE)) {
                        getProtein(accession, pathwayRefId, DEFAULT_SOURCE);
                    } else {
                        Set<org.biopax.paxtools.model.level2.xref> xrefs = entity.getXREF();
                        for (org.biopax.paxtools.model.level2.xref xref : xrefs) {
                            accession = xref.getRDFId();
                            if (accession.contains(source)) {
                                getProtein(accession, pathwayRefId, source);
                            } else if (accession.contains(DEFAULT_SOURCE)) {
                                getProtein(accession, pathwayRefId, DEFAULT_SOURCE);
                            }
                        }
                    }

                }
            }
            if(!visited.contains(bpe)) {
                visited.add(bpe);
                depth++;
                traverser.traverse(bpe, model);
                depth--;
            }
        }
    }
    
    private void setSynonym(String subjectId, String value)
    throws ObjectStoreException {
        MultiKey key = new MultiKey(subjectId, value);
        if (synonyms.contains(key)) {
            Item syn = createItem("Synonym");
            syn.setReference("subject", subjectId);
            syn.setAttribute("value", value);
            synonyms.add(key);
            try {
                store(syn);
            } catch (ObjectStoreException e) {
                throw new ObjectStoreException(e);
            }
        }
    }
    
    public void close() 
    throws ObjectStoreException{
        for (Item protein : proteins.values()) {
            try {
                store(protein);
            } catch (ObjectStoreException e) {
                throw new ObjectStoreException(e);
            }
        }
    }
    
}