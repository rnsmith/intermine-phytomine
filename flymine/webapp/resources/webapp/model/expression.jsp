<!-- microarray.jsp -->
<%@ taglib tagdir="/WEB-INF/tags" prefix="im" %>

<table width="100%">
  <tr>
    <td valign="top" rowspan="2">
      <div class="heading2">
        Current data
      </div>

      <div class="body">

        <h4>
          <a href="javascript:toggleDiv('hiddenDiv1');">
            <img id='hiddenDiv1Toggle' src="images/disclosed.gif"/>
            <i>D. melanogaster</i>  - In situ hybridisation data from BDGP  ...
          </a>
        </h4>

        <div id="hiddenDiv1" class="dataSetDescription">
          <dl>
            <dt>
              The <a href="http://www.fruitfly.org/cgi-bin/ex/insitu.pl/" target="_new">BDGP in situ project</a> determines patterns of gene expression during embryogenesis for <i>Drosophila</i> genes represented in non-redundant sets of <i>Drosophila</i> ESTs DGC1 and DGC2. Gene expression patterns are annotated with controlled vocabulary for developmental anatomy of <i>Drosophila</i> embryogenesis (ImaGO).

              The overall findings of the work are summarized in Tomancak et al (2007) Genome Biology 8:R145. (<a href="http://www.ncbi.nlm.nih.gov/entrez/query.fcgi?cmd=Retrieve&db=PubMed&dopt=Abstract&list_uids=17645804" target="_new">PubMed: 17645804</a>) - Global analysis of patterns of gene expression during Drosophila embryogenesis.
            </dt>
          </dl>
        </div>

        <h4>
          <a href="javascript:toggleDiv('hiddenDiv2');">
            <img id='hiddenDiv2Toggle' src="images/disclosed.gif"/>
            <i>D. melanogaster</i>  - In situ hybridisation data from Fly-FISH ...
          </a>
        </h4>

        <div id="hiddenDiv2" class="dataSetDescription">
          <dl>
            <dt>
              The <a href="http://fly-fish.ccbr.utoronto.ca/" target="_new">Fly-FISH</a> data documents the expression patterns of <i>Drosophila</i> mRNAs at the subcellular level during early embryogenesis. The overall findings and implications of the work performed thus far is summarized in Lecuyer et al (2007) Cell 131:174-187 (<a href="http://www.ncbi.nlm.nih.gov/entrez/query.fcgi?cmd=Retrieve&db=PubMed&dopt=Abstract&list_uids=17923096" target="_new">PubMed: 17923096</a>) - Global analysis of mRNA localization reveals a prominent role in organizing cellular architecture and function.
            </dt>
          </dl>
        </div>

        <h4>
          <a href="javascript:toggleDiv('hiddenDiv3');">
            <img id='hiddenDiv3Toggle' src="images/disclosed.gif"/>
            <i>D. melanogaster</i>  - Microarray-based gene expression data from FlyAtlas ...
          </a>
        </h4>

        <div id="hiddenDiv3" class="dataSetDescription">
          <dl>
            <dt>An affymetrix microarray-based atlas of gene expression in the adult <i>Drosophila</i> fly from <a href="http://www.flyatlas.org/" target="_new">FlyAtlas</a>.</dt>

            <dd>
              This dataset was generated by Venkat Chintapalli, Jing Wang & Julian
              Dow at the University of Glasgow with funding from the UK's BBSRC.  It
              is intended to give you a quick answer to the question: where is my
              gene of interest expressed/enriched in the adult fly? For each gene &
              tissue, the following data are given:
              <ul>
                <li>mRNA Signal: Abundance of the mRNA. FlyAtlas considers values over 100 as being abundant, and anything over 1000 as remarkable.</li><br/>
                <li>mRNA Signal SEM: The SEM value tells you how consistent or variable the MRNASignal is.</li><br/>
                <li>mRNA Enrichment: This factor tells you how much higher the signal is in a particular tissue than in the whole fly, i.e. whether the gene is tissue-specific.</li><br/>
                <li>Present Call: How many of the four arrays for each sample actually gave a detectable expression, according to Affymetrix's GCOS software.</li><br/>
                <li>Affymetrix Call: reflects an up- or down-regulation, or no change at all, compared to the whole fly.</li><br/>
              </ul>
            </dd>
          </dl>
        </div>

        <h4>
          <a href="javascript:toggleDiv('hiddenDiv4');">
            <img id='hiddenDiv4Toggle' src="images/disclosed.gif"/>
            <i>D. melanogaster</i>  - Microarray-based gene expression data from ArrayExpress ...
          </a>
        </h4>

        <div id="hiddenDiv4" class="dataSetDescription">
          <dl>
            <dt>
              Arbeitman et al (2002) Science 297:2270-2275 (<a href="http://www.ncbi.nlm.nih.gov/entrez/query.fcgi?cmd=Retrieve&db=PubMed&dopt=Abstract&list_uids=12351791" target="_new">PubMed: 12351791</a>) - Gene Expression During the Life Cycle of Drosophila melanogaster - ArrayExpress: <a href="http://www.ebi.ac.uk/microarray-as/aer/result?queryFor=Experiment&eAccession=E-FLYC-6" target="_new">E-FLYC-6</a>
            </dt>
            <dd>
              Arbeitman et al reported gene expression patterns for nearly one third of all <i>Drosophila</i> genes during a complete time course of development.  Graphs are displayed on summary pages for each gene involved in the experiment showing Log 2 exression ratio for 67 time points across life stages.
            </dd>
            <i>An example graph showing expression of the gene 'big brain'.</i>
            <br/>
            <img style="border: 1px solid black" src="model/images/big_brain_expression.png"/>
          </dl>
        </div>

        <h4>
          <a href="javascript:toggleDiv('hiddenDiv5');">
            <img id='hiddenDiv5Toggle' src="images/disclosed.gif"/>
            <i>A. gambiae</i>  - Gene expression data from ArrayExpress ...
          </a>
        </h4>

        <div id="hiddenDiv5" class="dataSetDescription">
          <dl>
            <dt>
              Koutsos et al (2007) PNAS 104:11304-11309 (<a href="http://www.ncbi.nlm.nih.gov/entrez/query.fcgi?cmd=Retrieve&db=PubMed&dopt=Abstract&list_uids=17563388" target="_new">PubMed: 17563388</a>) - Life cycle transcriptome of the malaria mosquito <i>A. gambiae</i> and comparison with the fruitfly <i>Drosophila melanogaster</i> - ArrayExpress: <a href="http://www.ebi.ac.uk/microarray-as/aer/result?queryFor=Experiment&eAccession=E-TABM-186/" target="_new">E-TABM-186</a>
            </dt>
            <dd>
              Koutsos et al used an EST microarray platform encompassing 19,680 ESTs to determine genome-wide expression profiles during the <i>A. gambiae</i> life cycle.
            </dd>
          </dl>
        </div>
      </div>
    </td>

    <td width="40%" valign="top">
      <div class="heading2">
        Bulk download
      </div>
      <div class="body">
        <ul>
          <li>
            <im:querylink text="All BDGP in situ results for <i>D. melanogaster</i> genes " skipBuilder="true">
<query name="" model="genomic" view="Gene.primaryIdentifier Gene.symbol Gene.mRNAExpressionResults.stageRange Gene.mRNAExpressionResults.expressed Gene.mRNAExpressionResults.mRNAExpressionTerms.name" sortOrder="Gene.primaryIdentifier asc" constraintLogic="A and B">
  <constraint path="Gene.organism.name" code="A" op="=" value="Drosophila melanogaster"/>
  <constraint path="Gene.dataSets.name" code="B" op="=" value="BDGP in situ data set"/>
</query>
            </im:querylink>
          </li>

          <li>
            <im:querylink text="All FlyAtlas results for <i>D. melanogaster</i> genes " skipBuilder="true">
<query name="" model="genomic" view="Gene.primaryIdentifier Gene.symbol Gene.microArrayResults.material.primaryIdentifier Gene.microArrayResults.tissue.name Gene.microArrayResults.mRNASignal Gene.microArrayResults.mRNASignalSEM Gene.microArrayResults.presentCall Gene.microArrayResults.enrichment Gene.microArrayResults.affyCall" sortOrder="Gene.primaryIdentifier asc" constraintLogic="A and B">
  <constraint path="Gene.organism.name" code="A" op="=" value="Drosophila melanogaster"/>
  <constraint path="Gene.microArrayResults.dataSets.name" code="B" op="=" value="FlyAtlas"/>
  <constraint path="Gene.microArrayResults" type="FlyAtlasResult"/>
</query>
            </im:querylink>
          </li>


          <li>
            <im:querylink text="All Fly-FISH results for <i>D. melanogaster</i> genes " skipBuilder="true">
<query name="" model="genomic" view="Gene.primaryIdentifier Gene.symbol Gene.mRNAExpressionResults.stageRange Gene.mRNAExpressionResults.expressed Gene.mRNAExpressionResults.mRNAExpressionTerms.name" sortOrder="Gene.primaryIdentifier asc" constraintLogic="A and B">
  <constraint path="Gene.organism.name" code="A" op="=" value="Drosophila melanogaster"/>
  <constraint path="Gene.dataSets.name" code="B" op="=" value="fly-Fish data set"/>
</query>
            </im:querylink>
          </li>

          <li>
            <im:querylink text="All results from Koutsos et al for <i>A. gambiae</i> genes " skipBuilder="true">
<query name="" model="genomic" view="Gene.primaryIdentifier Gene.microArrayResults.value Gene.microArrayResults.standardError Gene.microArrayResults.type Gene.microArrayResults.assays.name" sortOrder="Gene.primaryIdentifier asc Gene.primaryIdentifier asc" constraintLogic="A and B">
  <constraint path="Gene.organism.name" code="A" op="=" value="Anopheles gambiae"/>
  <constraint path="Gene.microArrayResults" type="AGambiaeLifeCycle"/>
</query>
            </im:querylink>
          </li>
        </ul>
      </div>
    </td>
  </tr>
</table>
<!-- /microarray.jsp -->