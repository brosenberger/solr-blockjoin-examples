package at.bro.code.solr.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;

/**
 *
 * @author brosenberger
 *
 */
public final class SolrUtils {

    private SolrUtils() {
        // should not be instantiated
    }

    public static List<SolrDocument> expandResults(QueryResponse queryResponse, String idField) {
        final SolrDocumentList results = queryResponse.getResults();
        final List<SolrDocument> documents = new ArrayList<>();
        final Map<String, SolrDocumentList> expandedResults = queryResponse.getExpandedResults();
        for (int i = 0; i < results.getNumFound(); i++) {
            final SolrDocument solrDocument = results.get(i);
            final SolrDocumentList children = expandedResults.get(solrDocument.get(idField));
            if (children != null) {
                solrDocument.addChildDocuments(children);
            }

            // TODO possibly add grandchildren?

            documents.add(solrDocument);
        }
        return documents;
    }
}
