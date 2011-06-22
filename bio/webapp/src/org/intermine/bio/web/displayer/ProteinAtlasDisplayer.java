package org.intermine.bio.web.displayer;

/*
 * Copyright (C) 2002-2011 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.intermine.api.InterMineAPI;
import org.intermine.api.profile.Profile;
import org.intermine.api.query.PathQueryExecutor;
import org.intermine.api.results.ExportResultsIterator;
import org.intermine.api.results.ResultElement;
import org.intermine.bio.web.model.ProteinAtlasExpressions;
import org.intermine.metadata.Model;
import org.intermine.model.InterMineObject;
import org.intermine.pathquery.Constraints;
import org.intermine.pathquery.OrderDirection;
import org.intermine.pathquery.PathQuery;
import org.intermine.web.displayer.ReportDisplayer;
import org.intermine.web.logic.config.ReportDisplayerConfig;
import org.intermine.web.logic.results.ReportObject;
import org.intermine.web.logic.session.SessionMethods;

import org.intermine.model.bio.Gene;
import org.intermine.model.bio.Protein;

public class ProteinAtlasDisplayer extends ReportDisplayer {

    public ProteinAtlasDisplayer(ReportDisplayerConfig config, InterMineAPI im) {
        super(config, im);
    }

    @Override
    public void display(HttpServletRequest request, ReportObject reportObject) {

        // get the gene/protein in question from the request
        InterMineObject object = reportObject.getObject();

        // API connection
        HttpSession session = request.getSession();
        final InterMineAPI im = SessionMethods.getInterMineAPI(session);
        Model model = im.getModel();
        PathQuery query = new PathQuery(model);

        // dealing with genes...
        if (object instanceof Gene) {
            // cast me Gene
            Gene gene = (Gene) object;
            Object genePrimaryIDObj = gene.getPrimaryIdentifier();
            if (genePrimaryIDObj != null) {
                // fetch the expression
                String genePrimaryID = String.valueOf(genePrimaryIDObj);
                query = geneProteinAtlasExpressionQuery(genePrimaryID, query);

                // execute the query
                Profile profile = SessionMethods.getProfile(session);
                PathQueryExecutor executor = im.getPathQueryExecutor(profile);
                ExportResultsIterator values = executor.execute(query);

                // parse values
                ProteinAtlasExpressions results = new ProteinAtlasExpressions(values);

                // attach to results
                request.setAttribute("lolcat", results);
            }

            //result.put("gene", geneComments2(values));
        } else if (object instanceof Protein) {
            //
        } else {
            // big fat fail
        }
    }

    /**
     * Return an API query fetching all tissue expressions
     * @author radek
     *
     * @param genePrimaryID
     * @param query
     * @return
     */
    private PathQuery geneProteinAtlasExpressionQuery(String genePrimaryID, PathQuery query) {
        query.addViews(
                "Gene.proteinAtlasExpression.cellType",
                "Gene.proteinAtlasExpression.expressionType",
                "Gene.proteinAtlasExpression.level",
                "Gene.proteinAtlasExpression.reliability",
                "Gene.proteinAtlasExpression.tissue.name",
                "Gene.primaryIdentifier");
        query.addOrderBy("Gene.proteinAtlasExpression.expressionType", OrderDirection.ASC);
        query.addConstraint(Constraints.eq("Gene.primaryIdentifier", genePrimaryID));

        return query;
    }

}
