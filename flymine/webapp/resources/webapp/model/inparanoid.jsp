<!-- inparanoid.jsp -->
<%@ taglib tagdir="/WEB-INF/tags" prefix="im" %>

<TABLE width="100%">
  <tr>
    <td valign="top">
      <div class="heading2">
        Current data
      </div>
      <div class="body">

 <h4>
  <a href="javascript:toggleDiv('hiddenDiv1');">
    <img id='hiddenDiv1Toggle' src="images/disclosed.gif"/>
      Homology data from inparanoid ...
  </a>
 </h4>

<div id="hiddenDiv1" class="dataSetDescription">


        <p>Orthologue and inparalogue relationships calculated by <A href="http://inparanoid.sbc.su.se/" target="_new">InParanoid</A> between the following organisms:</p>
        <ul>
          <li><I>D. melanogaster</I></li>
          <li><I>D. pseudoobscura</I></li>
          <li><I>A. gambiae</I></li>
          <li><I>C. elegans</I></li>
        </ul><br/>

        <p>
          In addition, orthologues/inparalogues from these five species to several others:
        </p>
        <p>
          <i>C. familiaris , D. discoideum, D. rerio, G. gallus, H. sapiens, M. musculus, P. troglodytes, R. norvegicus, S. cerevisiae, S. pombe</i>
        </p>

          <p><im:querylink text="Show all pairs of organisms linked by orthologues" skipBuilder="true">
            <query name="" model="genomic" view="Homologue.gene.organism.shortName Homologue.homologue.organism.shortName"><node path="Homologue" type="Homologue"></node></query>
          </im:querylink></p>


  <p>The inparanoid program calculates orthologue and
inparalogue clusters, pairwise between two organisms, by first finding
the best recipricol blast match for each gene. This becomes the
seed-orthologue to which inparalogues are clustered (outparalogues are
excluded - see definitions below).  Each member in the cluster
receives an inparalog score which reflects the distance to the
seed-orthologue.  A score of 1.0 means there is identical distance to
the seed orthologue (and so all orthologues in the cluster will have
an inparanoid score of 1.0).  Each inparalogue in the cluster will
have a score less than one which reflects how similar it is to the
seed orthologue.  In addition to the inparanoid score, each orthologue
within the cluster has a bootstrap score, which is the confidence that
this seed-ortholog pair are true orthologues. (This is estimated by
sampling how often the pair is found as recipricolly best matches by a
bootstrapping procedure applied to the original Blast alignment). </p>

   <p>   Glossary according to Sonnhammer and Koonin </a> (<a href="http://www.ncbi.nlm.nih.gov/entrez/query.fcgi?cmd=Retrieve&db=PubMed&dopt=Abstract&list_uids=12446146" target="_new">PubMed: 12446146</a>) ...</p>




      <ul>
       <li>Homologous genes: genes with common ancestry.</li>
       <li>Orthologous genes: genes in two species that have directly evolved from a single gene in the last common ancestor and are likely to be functionally related.</li>
       <li>Paralogous genes: homologous genes related by a duplication event. Might be in the same or in a different genome.</li>
       <li>Inparalogous genes: genes that derive from a duplication event after a speciation of interest. Inparalogs are together orthologs to the corresponding orthologous gene/genes in the other species.</li>
       <li>Outparalogous genes: genes that derive from a duplication event before a speciation event of interest, thus not orthologs according to definition.</li>
      </ul>




</div>

   <h4>
    <a href="javascript:toggleDiv('hiddenDiv2');">
     <img id='hiddenDiv2Toggle' src="images/disclosed.gif"/>
      Homology data from the <i>Drosophila</i> 12 genomes consortium ...
    </a>
   </h4>

<div id="hiddenDiv2" class="dataSetDescription">

          <p>
            Orthologue and paralogue predictions between the 12 <i>Drosophila</i> genomes from the <i>Drosophila</i> 12 genomes consortium </a> (<a href="http://www.ncbi.nlm.nih.gov/entrez/query.fcgi?cmd=Retrieve&db=PubMed&dopt=Abstract&list_uids=17994087" target="_new">PubMed: 17994087</a>)</p>
          </ul>
      </div>
    </td>


    <td width="40%" valign="top">
      <div class="heading2">
       Bulk download
      </div>
      <div class="body">
        <ul>
          <li>
            <im:querylink text="Homologues: <i>D. melanogaster</i> vs <i>A. gambiae</i> " skipBuilder="true">
<query name="" model="genomic" view="Homologue.gene.primaryIdentifier Homologue.gene.secondaryIdentifier Homologue.gene.symbol Homologue.homologue.primaryIdentifier Homologue.homologue.secondaryIdentifier Homologue.type Homologue.inParanoidScore" sortOrder="Homologue.gene.primaryIdentifier Homologue.gene.secondaryIdentifier Homologue.gene.symbol Homologue.homologue.primaryIdentifier Homologue.homologue.secondaryIdentifier Homologue.type Homologue.inParanoidScore" constraintLogic="A and B">
  <node path="Homologue" type="Homologue">
  </node>
  <node path="Homologue.gene" type="Gene">
  </node>
  <node path="Homologue.gene.organism" type="Organism">
  </node>
  <node path="Homologue.gene.organism.name" type="String">
    <constraint op="=" value="Drosophila melanogaster" description="Show the predicted orthologues between:" identifier="" editable="true" code="A">
    </constraint>
  </node>
  <node path="Homologue.homologue" type="Gene">
  </node>
  <node path="Homologue.homologue.organism" type="Organism">
  </node>
  <node path="Homologue.homologue.organism.name" type="String">
    <constraint op="=" value="Anopheles gambiae" description="" identifier="" editable="true" code="B">
    </constraint>
  </node>
</query>
            </im:querylink>
          </li>

          <li>
            <im:querylink text="Homologues: <i>D. melanogaster</i> vs <i>C. elegans</i> " skipBuilder="true">
<query name="" model="genomic" view="Homologue.gene.primaryIdentifier Homologue.gene.secondaryIdentifier Homologue.gene.symbol Homologue.homologue.primaryIdentifier Homologue.homologue.secondaryIdentifier Homologue.homologue.symbol Homologue.type Homologue.inParanoidScore" sortOrder="Homologue.gene.primaryIdentifier Homologue.gene.secondaryIdentifier Homologue.gene.symbol Homologue.homologue.primaryIdentifier Homologue.homologue.secondaryIdentifier Homologue.homologue.symbol Homologue.type Homologue.inParanoidScore" constraintLogic="A and B">
  <node path="Homologue" type="Homologue">
  </node>
  <node path="Homologue.gene" type="Gene">
  </node>
  <node path="Homologue.gene.organism" type="Organism">
  </node>
  <node path="Homologue.gene.organism.name" type="String">
    <constraint op="=" value="Drosophila melanogaster" description="Show the predicted orthologues between:" identifier="" editable="true" code="A">
    </constraint>
  </node>
  <node path="Homologue.homologue" type="Gene">
  </node>
  <node path="Homologue.homologue.organism" type="Organism">
  </node>
  <node path="Homologue.homologue.organism.name" type="String">
    <constraint op="=" value="Caenorhabditis elegans" description="" identifier="" editable="true" code="B">
    </constraint>
  </node>
</query>
            </im:querylink>
          </li>

          <li>
           <im:querylink text="Homologues: <i>D. melanogaster</i> vs <i>H. sapiens</i> " skipBuilder="true">
<query name="" model="genomic" view="Homologue.gene.primaryIdentifier Homologue.gene.secondaryIdentifier Homologue.gene.symbol Homologue.homologue.primaryIdentifier Homologue.homologue.secondaryIdentifier Homologue.type Homologue.inParanoidScore" sortOrder="Homologue.gene.primaryIdentifier Homologue.gene.secondaryIdentifier Homologue.gene.symbol Homologue.homologue.primaryIdentifier Homologue.homologue.secondaryIdentifier Homologue.type Homologue.inParanoidScore" constraintLogic="A and B">
  <node path="Homologue" type="Homologue">
  </node>
  <node path="Homologue.gene" type="Gene">
  </node>
  <node path="Homologue.gene.organism" type="Organism">
  </node>
  <node path="Homologue.gene.organism.name" type="String">
    <constraint op="=" value="Drosophila melanogaster" description="Show the predicted orthologues between:" identifier="" editable="true" code="A">
    </constraint>
  </node>
  <node path="Homologue.homologue" type="Gene">
  </node>
  <node path="Homologue.homologue.organism" type="Organism">
  </node>
  <node path="Homologue.homologue.organism.name" type="String">
    <constraint op="=" value="Homo sapiens" description="" identifier="" editable="true" code="B">
    </constraint>
  </node>
</query>
            </im:querylink>
           </li>

          <li>
           <im:querylink text="Homologues: <i>D. melanogaster</i> vs <i>M. musculus</i> " skipBuilder="true">
<query name="" model="genomic" view="Homologue.gene.primaryIdentifier Homologue.gene.secondaryIdentifier Homologue.gene.symbol Homologue.homologue.primaryIdentifier Homologue.homologue.symbol Homologue.type Homologue.inParanoidScore" sortOrder="Homologue.gene.primaryIdentifier Homologue.gene.secondaryIdentifier Homologue.gene.symbol Homologue.homologue.primaryIdentifier Homologue.homologue.symbol Homologue.type Homologue.inParanoidScore" constraintLogic="A and B">
  <node path="Homologue" type="Homologue">
  </node>
  <node path="Homologue.gene" type="Gene">
  </node>
  <node path="Homologue.gene.organism" type="Organism">
  </node>
  <node path="Homologue.gene.organism.name" type="String">
    <constraint op="=" value="Drosophila melanogaster" description="Show the predicted orthologues between:" identifier="" editable="true" code="A">
    </constraint>
  </node>
  <node path="Homologue.homologue" type="Gene">
  </node>
  <node path="Homologue.homologue.organism" type="Organism">
  </node>
  <node path="Homologue.homologue.organism.name" type="String">
    <constraint op="=" value="Mus musculus" description="" identifier="" editable="true" code="B">
    </constraint>
  </node>
</query>
           </im:querylink>
          </li>


          <li>
          <im:querylink text="Homologues: <i>D. melanogaster</i> vs <i>D. pseudoobscura</i> " skipBuilder="true">
<query name="" model="genomic" view="Homologue.gene.primaryIdentifier Homologue.gene.secondaryIdentifier Homologue.gene.symbol Homologue.homologue.primaryIdentifier Homologue.homologue.secondaryIdentifier Homologue.homologue.symbol Homologue.type" sortOrder="Homologue.gene.primaryIdentifier Homologue.gene.secondaryIdentifier Homologue.gene.symbol Homologue.homologue.primaryIdentifier Homologue.homologue.secondaryIdentifier Homologue.homologue.symbol Homologue.type" constraintLogic="A and B">
  <node path="Homologue" type="Homologue">
  </node>
  <node path="Homologue.gene" type="Gene">
  </node>
  <node path="Homologue.gene.organism" type="Organism">
  </node>
  <node path="Homologue.gene.organism.name" type="String">
    <constraint op="=" value="Drosophila melanogaster" description="Show the predicted orthologues between:" identifier="" editable="true" code="A">
    </constraint>
  </node>
  <node path="Homologue.homologue" type="Gene">
  </node>
  <node path="Homologue.homologue.organism" type="Organism">
  </node>
  <node path="Homologue.homologue.organism.name" type="String">
    <constraint op="=" value="Drosophila pseudoobscura" description="" identifier="" editable="true" code="B">
    </constraint>
  </node>
</query>
          </im:querylink>
         </li>

          <li>
            <im:querylink text="Homologues: <i>A. gambiae</i> vs <i>C. elegans</i> " skipBuilder="true">
<query name="" model="genomic" view="Homologue.gene.primaryIdentifier Homologue.gene.secondaryIdentifier Homologue.homologue.primaryIdentifier Homologue.homologue.secondaryIdentifier Homologue.homologue.symbol Homologue.type Homologue.inParanoidScore" sortOrder="Homologue.gene.primaryIdentifier Homologue.gene.secondaryIdentifier Homologue.homologue.primaryIdentifier Homologue.homologue.secondaryIdentifier Homologue.homologue.symbol Homologue.type Homologue.inParanoidScore" constraintLogic="A and B">
  <node path="Homologue" type="Homologue">
  </node>
  <node path="Homologue.gene" type="Gene">
  </node>
  <node path="Homologue.gene.organism" type="Organism">
  </node>
  <node path="Homologue.gene.organism.name" type="String">
    <constraint op="=" value="Anopheles gambiae" description="Show the predicted orthologues between:" identifier="" editable="true" code="A">
    </constraint>
  </node>
  <node path="Homologue.homologue" type="Gene">
  </node>
  <node path="Homologue.homologue.organism" type="Organism">
  </node>
  <node path="Homologue.homologue.organism.name" type="String">
    <constraint op="=" value="Caenorhabditis elegans" description="" identifier="" editable="true" code="B">
    </constraint>
  </node>
</query>
            </im:querylink>
          </li>

        </ul>
      </div>
    </td>
  </tr>
</TABLE>

<!-- /inparanoid.jsp -->
