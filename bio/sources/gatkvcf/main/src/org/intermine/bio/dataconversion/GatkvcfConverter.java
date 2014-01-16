package org.intermine.bio.dataconversion;

/*
 * Copyright (C) 2013 Phytozome
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.io.Reader;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Iterator;

import org.apache.log4j.Logger;
import org.apache.tools.ant.BuildException;

import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.xml.full.Item;
import org.intermine.xml.full.ReferenceList;
import org.intermine.util.StringUtil;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.bio.dataconversion.BioFileConverter;
import org.intermine.bio.util.OrganismData;
import org.intermine.bio.util.OrganismRepository;
import org.intermine.util.FormattedTextParser;

/**
 * 
 * @author 
 */
public class GatkvcfConverter extends BioFileConverter
{
  //
  private static final String DATASET_TITLE = "GATK VCF Data";
  private static final String DATA_SOURCE_NAME = "GATK Diversity";
  private static final Logger LOG = Logger.getLogger(GatkvcfConverter.class);

  // hashes of inserted things...
  // snpLocation is keyed by (chromosome:start position:reference).
  // Using a multimap was toooooo slow.
  // this is a map to intermineId (Integer)
  private HashMap<String,Integer> snpLocationIntMap = new HashMap<String,Integer>();
  // this is a map to uniqueIdentifier (String)
  // also using the chromosome:start:reference key
  private HashMap<String,String> snpLocationIDMap = new HashMap<String,String>();
  // snps that go to a snpLocation
  private HashMap<String,ArrayList<String> > snpLocationCollectionMap = new HashMap<String,ArrayList<String> >();
  // The chromosomes that we reference. chromosomes keyed by chromosome
  private HashMap<String,String> chrMap = new HashMap<String,String>();
  //  the snp keyed by snpLocation and nucleotide substitution
  private HashMap<String,ArrayList<String>> snpMap = new HashMap<String,ArrayList<String> >();
  // standard organism map.
  // key is taxon id
  private Map<Integer,String> organismMap = new HashMap<Integer,String>();
  // the organism we're working on now. Presumable this would be parsed from
  // the file somehow.
  private Integer currentOrganism;
  private String version;
  // the referenced consequences and consequence type types.
  private Map<String,String> consequenceMap = new HashMap<String,String>();
  private Map<String,String> consequenceTypeMap = new HashMap<String,String>();
  // referenced genes and transcripts.
  private Map<String,String> geneMap = new HashMap<String,String>();
  private Map<String,String> mRNAMap = new HashMap<String,String>();
  private Item organism = null;
  // we'll get this from the header. When parsing, we need to keep these in order
  private ArrayList<String> sampleList = new ArrayList<String>();
  private Pattern EFF_PATTERN;
  // we'll use this for printing log message when we go to a new chromosome
  private String lastChromosome = null;

  /**
   * Constructor
   * @param writer the ItemWriter used to handle the resultant items
   * @param model the Model
   */
  public GatkvcfConverter(ItemWriter writer, Model model) {
    super(writer, model, DATA_SOURCE_NAME, DATASET_TITLE);
    EFF_PATTERN = Pattern.compile("(\\w+)\\((.+)\\)");
  }

  public void setOrganisms(String organisms) {
    String[] bits = StringUtil.split(organisms, " ");
    //for (int i = 0; i < bits.length; i++) {
    for (String organismIdString: bits) {
      OrganismData od = null;
      Integer taxonId;
      try {
        taxonId = Integer.valueOf(organismIdString);
        od = OrganismRepository.getOrganismRepository().getOrganismDataByTaxon(taxonId);
        // the last one we register becomes the 'current' one
        currentOrganism = taxonId;
      } catch (NumberFormatException e) {
        od = OrganismRepository.getOrganismRepository().getOrganismDataByAbbreviation(organismIdString);
        taxonId = 999;
      }
      if (od == null) {
        throw new RuntimeException("Can't find organism for: " + organismIdString);
      }
      if (!organismMap.containsKey(taxonId) ) {
        organism = createItem("Organism");
        organism.setAttribute("taxonId", taxonId.toString());
        try {
          store(organism);
        } catch(ObjectStoreException e) {
          throw new BuildException("Problem storing organism");
        }
        organismMap.put(taxonId,organism.getIdentifier());
      }
    }
    // TODO: extend this to multiple organism processing; Until then, this
    // only will process 1 organism. How will we link the file to the organism?
    if (organismMap.size() > 1 ) {
      throw new BuildException("This code only written for processing single organisms.");
    }
  }
  public void setVersion(String versionProp) {
    version = versionProp;
  }
  /**
   * 
   *
   * {@inheritDoc}
   */
  public void process(Reader reader) throws Exception {
    File theFile = getCurrentFile();
    LOG.info("Processing file " + theFile.getName() + "...");
    //TODO if we want to process multiple organisms, makes sure we set
    // the organism variable at this point.
    if( !theFile.getName().endsWith(".vcf") ) {
      LOG.info("Ignoring file " + theFile.getName() + ". Not a SnpEff-processed GATK vcf file.");
    } else {
      // we need to open and find the header line. Since this starts with a "#", the
      //  FormattedTextParser calls this a comment line and will not return it to us.
      // TODO: replace FormattedTextParser.
      BufferedReader in = new BufferedReader(new FileReader(theFile));
      String line;
      while ( (line = in.readLine()) != null) {
        if (line.startsWith("##")) continue;
        if (line.startsWith("#") ) {
          String[] fields = line.split("\\t");
          processHeader(fields);
          break;
        }
      }
      // make sure we processed the header at this point
      if (sampleList.size() == 0) {
        throw new BuildException("Cannot find sample names in vcf file.");
      }
      Iterator<?> tsvIter;
      try {
        tsvIter = FormattedTextParser.parseTabDelimitedReader(reader);
      } catch (Exception e) {
        throw new BuildException("Cannot parse file: " + getCurrentFile(),e);
      }
      int ctr = 0;
      while (tsvIter.hasNext() ) {
        ctr++;
        String[] fields = (String[]) tsvIter.next();
        if (!processData(fields)) {
          return;
        }
        if ((ctr%100000) == 0) {
          LOG.info("Processed " + ctr + " lines...");
        }
      }
      LOG.info("Processed " + ctr + " lines.");
    }
  }
  public void close() throws Exception
  {
    // now store all snp locations. We've held off in storing until all snpLocations were processed
    Set<String> snpLocs = (Set<String>)snpLocationIntMap.keySet();
    Iterator<String>snpLocIt = snpLocs.iterator();
    LOG.info("Storing SNP locations...");
    while (snpLocIt.hasNext() ) {
      String snpLocKey = snpLocIt.next();
      ReferenceList refList = new ReferenceList();
      refList.setName("snps");
      refList.setRefIds((ArrayList<String>)snpLocationCollectionMap.get(snpLocKey));
      try {
        store(refList,(Integer)snpLocationIntMap.get(snpLocKey));
      } catch (ObjectStoreException e) {
        throw new BuildException("Cannot store snplocation: " + e.getMessage());
      }
    }
  }

  private void processHeader(String[] header) throws BuildException
  {
    // here is what we expect for the first few columns. Complain if this
    // is not case;
    if (header.length < 9) {
      throw new BuildException("Unexpected length of header fields.");
    } else {
      // we haven't stripped off the # from CHROM
      if ( header[0] == null || !header[0].equals("#CHROM") ||
            header[1] == null || !header[1].equals("POS") ||
            header[2] == null || !header[2].equals("ID") ||
            header[3] == null || !header[3].equals("REF") ||
            header[4] == null || !header[4].equals("ALT") ||
            header[5] == null || !header[5].equals("QUAL") ||
            header[6] == null || !header[6].equals("FILTER") ||
            header[7] == null || !header[7].equals("INFO") ||
            header[8] == null || !header[8].equals("FORMAT") ) {
            throw new BuildException("Unexpected item in header fields.");
      }
      // we're going to be certain there are no duplicates.
      HashSet<String> sampleNameSet = new HashSet<String>();
      for(int i=9;i<header.length;i++) {
        if( sampleNameSet.contains(header[i]) ) {
          throw new BuildException("Duplicated sample in header name: "+ header[i]);
        }
        Item source = createItem("DiversitySample");
        source.setAttribute("name",header[i]);
        source.setReference("organism",organismMap.get(currentOrganism));
        try {
        store(source);
        } catch (ObjectStoreException e) {
          throw new BuildException("Cannot store source " + header[i]);
        }
        sampleList.add(source.getIdentifier());
      }
    }
  }
  private boolean processData(String[] fields) throws BuildException
  {
    if (fields.length < 10) {
      throw new BuildException("Unexpected number of columns in VCF file.");
    }
    String chr = fields[0];
    //TODO  remove for production
    //if (!chr.equals("Chr01")) return false;
    
    if (lastChromosome==null || !chr.equals(lastChromosome)) {
      lastChromosome = chr;
      LOG.info("Processing "+chr);
    }
    
    Integer pos = new Integer(fields[1]);
    //TODO remove for production
    //if (pos > 100000) return false;
    
    String name = fields[2];
    String ref = fields[3];
    String alt = fields[4];
    String quality = fields[5];
    String filter = fields[6];
    String info = fields[7];
    
    String snpLocKey = fields[0]+":"+fields[1]+":"+fields[3];
    // create this location if we haven't seen this before
    if (! snpLocationIntMap.containsKey(snpLocKey)) {
      if (!createSNPLocation(chr,pos,ref)) {
        throw new BuildException("Cannot create SNP location");
      }
    }
    String snpLocationID = (String)snpLocationIDMap.get(snpLocKey);
    Item snp = createItem("SNP");
    snp.setAttribute("alternate", alt);
    snp.setAttribute("quality", quality);
    snp.setAttribute("name",name);
    snp.setAttribute("filter", filter);
    snp.setReference("snpLocation", snpLocationID);
    //@SuppressWarnings("unchecked")
    ((ArrayList<String>)snpLocationCollectionMap.get(snpLocKey)).add(snp.getIdentifier());
    Integer nSamples = parseInfo(snp,info);
    if (nSamples != null && nSamples > 0) {
      snp.setAttribute("sampleCount", nSamples.toString());
    }

    try {
      store(snp);
    } catch (ObjectStoreException e) {
      throw new BuildException("Problem storing SNP: " + e);
    }
    
    // process the genotype field. First we have attribute:attribute:attribute... and value:value:value...
    // convert these to attribute=value;attribute=value;...
    String[] attBits = fields[8].split(":");
    
    // look through the different genotype scores for column 9 onward.
    for(int col=9;col<fields.length;col++) {
      if (fields[col].equals("./.")) {
        continue;
      } else {
        String[] valBits = fields[col].split(":");
        StringBuffer genotype = new StringBuffer();
        if ( valBits.length != attBits.length) LOG.warn("Genotype fields have unexpected length.");
        for(int i=0; i< attBits.length && i<valBits.length;i++) {
          if ( i > 0) genotype.append(";");
          genotype.append(attBits[i] + "=" + valBits[i]);
        }
        Item snpSource = createItem("SNPDiversitySample");
        snpSource.setAttribute("genotype", genotype.toString());
        snpSource.setReference("diversitySample", sampleList.get(col-9));
        snpSource.setReference("snp",snp.getIdentifier());
        try {
          store(snpSource);
        } catch (ObjectStoreException e) {
          throw new BuildException("Problem storing SNPDiversitySample: " + e);
        }
      }
    }
    
    return true;
  }
  /**
   * parseInfo
   * We're taking the INFO field of the VCF record, extracting the EFF tag and stuffing the remainder
   * into the info attribute
   * @param snp The snp record being processed
   * @param info The info string
   */
  private Integer parseInfo(Item snp, String info) {
    Integer nSamples = null;
    if (info == null) return null;
    // initialize the new string buffer to be same length as old.
    StringBuffer newInfo = new StringBuffer(info.length());
    String[] bits = info.split(";");
    for (String keyVal: bits) {
      String[] kV = keyVal.split("=",2);
      if (kV[0].equals("EFF")) {
        // deal with the SnpEff calls.
        // snpMap will be linked to all the consequences. If we've seen this
        // combination again (in a different file), we'll just link to these.
        // otherwise parse the fields.
        String snpMapKey = snp.getReference("snpLocation")+":"+snp.getAttribute("alternate");
        if (snpMap.containsKey(snpMapKey) ) {
          ArrayList<String> aList = snpMap.get(snpMapKey);
          for( String con : aList) {
            snp.addToCollection("consequences", con);
          }
        } else {
          // never seen this substitution at this location before; parse the EFF
          parseEff(snp,kV[1]);
        }
      } else if (kV[0].equals("set")) {
        // we're going to drop the set= tags. But we will use it to determine
        // the number of samples
        nSamples = kV[1].split("-").length;
      } else {
        // if not EFF tag, append to newInfo
        if (newInfo.length() > 0) newInfo.append(';');
        newInfo.append(keyVal);
      }
    }
    snp.setAttribute("info",newInfo.toString());
    return nSamples;
  }

  private void parseEff(Item snp, String eff) {
    if (eff == null) return;
    // save the results for linking again later
    ArrayList<String> aList = new ArrayList<String>();
    snpMap.put(snp.getReference("snpLocation")+":"+snp.getAttribute("alternate"),aList);
    for (String bit : eff.split(",") ) {
      Matcher match = EFF_PATTERN.matcher(bit);
      if (match.matches() ) {
        String cType = match.group(1);
        String effect = match.group(2);
        String[] fields = effect.split("\\|");
        if ( fields.length < 9) return;
        if (!consequenceTypeMap.containsKey(cType) ) {
          Item conTypeItem = createItem("ConsequenceType");
          conTypeItem.setAttribute("type", cType);
          try {
            store(conTypeItem);
          } catch (ObjectStoreException e) {
            throw new BuildException("Cannot store consequencetype: " + e);
          }
          consequenceTypeMap.put(cType,conTypeItem.getIdentifier());
        }

        // has this consequence been seen before?
        // first, construct the key to the map
        StringBuffer conKey = new StringBuffer(cType);
        conKey.append(":");
        if (fields[3] != null) conKey.append(fields[3]);
        conKey.append(":");
        if (fields[5] != null) conKey.append(fields[5]);
        conKey.append(":");
        if (fields[8] != null) conKey.append(fields[8]);
        
        if (!consequenceMap.containsKey(conKey.toString()) ) {
          Item con = createItem("Consequence");
          con.setReference("type",consequenceTypeMap.get(cType));
          if (fields[3] != null && fields[3].length() > 0) {
            con.setAttribute("substitution", fields[3]);
          }
          if (fields[5] != null && fields[5].length() > 0 ) {
            String geneName = getGene(fields[5]);
            if (geneName != null) con.setReference("gene", geneName);
          }
          if (fields[8] != null && fields[8].length() > 0 ) {
            String transName = getMRNA(fields[8]);
            if (transName != null) con.setReference("transcript",transName);
          }
          try {
            store(con);
          } catch (ObjectStoreException e) {
            throw new BuildException("Cannot store consequencetype: " + e);
          }
          consequenceMap.put(conKey.toString(),con.getIdentifier());
        }
        String conID = consequenceMap.get(conKey.toString());
        aList.add(conID);
        snp.addToCollection("consequences", conID);

 
      }
    }
  }

  private boolean createSNPLocation(String chromosome,Integer position,String reference) {

    if(reference.length() < 1) {
      return false;
    };
    // make sure the chromosome is registered
    if (! chrMap.containsKey(chromosome) ) {
      Item chrItem = createItem("Chromosome");
      chrItem.setAttribute("primaryIdentifier",chromosome);
      chrItem.setReference("organism",organism);
      try {
        store(chrItem);
      } catch (ObjectStoreException e) {
        throw new BuildException("Cannot store chromosome item: " + e);
      }
      chrMap.put(chromosome,chrItem.getIdentifier());
    }
    // make and store the feature
    Item snpLocation = createItem("SNPLocation");
    snpLocation.setReference("organism",organism);
    snpLocation.setAttribute("reference",reference);
    snpLocation.setAttribute("start",position.toString());
    snpLocation.setAttribute("end",(new Integer(position + reference.length()-1)).toString());
    snpLocation.setReference("locatedOn",chrMap.get(chromosome));

    //MultiKey snpLocKey = new MultiKey(chromosome,position,position+reference.length()-1);
    String snpLocKey = chromosome+":"+position.toString()+":"+reference;
    Integer intermineID = null;
    try {
      intermineID = store(snpLocation);
    } catch (ObjectStoreException e) {
      throw new BuildException("Cannot store SNP Location Feature: " + e);
    }
    snpLocationIDMap.put(snpLocKey, snpLocation.getIdentifier());
    snpLocationIntMap.put(snpLocKey, intermineID);
    snpLocationCollectionMap.put(snpLocKey,new ArrayList<String>());
    return true;
  }
  
  private String getGene(String gene_name) {
    if (!geneMap.containsKey(gene_name)) {
      Item gene = createItem("Gene");
      gene.setAttribute("primaryIdentifier", gene_name);
      gene.setReference("organism", organismMap.get(currentOrganism));
      try {
        store(gene);
      } catch (ObjectStoreException e) {
        throw new BuildException("Cannot store gene object " +e.getMessage());
      }
      geneMap.put(gene_name,gene.getIdentifier());
    }
    return geneMap.get(gene_name);
  }
  private String getMRNA(String mrna_name) {
    if (!mRNAMap.containsKey(mrna_name)) {
      Item mRNA = createItem("MRNA");
      mRNA.setAttribute("primaryIdentifier", mrna_name);
      mRNA.setReference("organism", organismMap.get(currentOrganism));
      try {
        store(mRNA);
      } catch (ObjectStoreException e) {
        throw new BuildException("Cannot store mRNA object " +e.getMessage());
      }
      mRNAMap.put(mrna_name,mRNA.getIdentifier());
    }
    return mRNAMap.get(mrna_name);
  }
}
